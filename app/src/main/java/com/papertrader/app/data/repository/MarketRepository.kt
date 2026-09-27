package com.papertrader.app.data.repository

import com.papertrader.app.data.remote.coingecko.CoinGeckoApi
import com.papertrader.app.data.remote.dexscreener.DexScreenerApi
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.domain.model.PricePoint
import com.papertrader.app.util.ErrorMessages
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for external market data. Encapsulates CoinGecko
 * (coin prices/market caps/history) and DexScreener (DEX pair data) behind
 * one clean interface, with:
 *  - an in-memory cache so identical requests within [CACHE_TTL_MILLIS] are
 *    served without another network round trip,
 *  - a simple retry-with-backoff for transient failures, and
 *  - an offline fallback that returns the last successfully cached response
 *    (marked [NetworkResult.Success.isFromCache]) when a fresh call fails.
 *
 * Nothing in this class ever sends an order — it only reads public market
 * data used to price simulated paper trades.
 */
class MarketRepository(
    private val coinGeckoApi: CoinGeckoApi,
    private val dexScreenerApi: DexScreenerApi
) {
    private data class CacheEntry<T>(val value: T, val timestampMillis: Long)

    private val marketsCache = ConcurrentHashMap<String, CacheEntry<List<Coin>>>()
    private val chartCache = ConcurrentHashMap<String, CacheEntry<List<PricePoint>>>()
    private val dexCache = ConcurrentHashMap<String, CacheEntry<List<DexPair>>>()

    suspend fun getTopCoins(perPage: Int = 100): NetworkResult<List<Coin>> {
        val cacheKey = "top_$perPage"
        return withRetryAndCache(
            cache = marketsCache,
            cacheKey = cacheKey,
            fetch = {
                coinGeckoApi.getMarkets(perPage = perPage).map { it.toDomain() }
            }
        )
    }

    suspend fun getCoinsByIds(ids: List<String>): NetworkResult<List<Coin>> {
        if (ids.isEmpty()) return NetworkResult.Success(emptyList())
        val cacheKey = "ids_${ids.sorted().joinToString(",")}"
        return withRetryAndCache(
            cache = marketsCache,
            cacheKey = cacheKey,
            fetch = {
                coinGeckoApi.getMarketsByIds(ids = ids.joinToString(",")).map { it.toDomain() }
            }
        )
    }

    suspend fun searchCoins(query: String): NetworkResult<List<Coin>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        return try {
            val results = coinGeckoApi.search(query).coins.take(20)
            val ids = results.map { it.id }
            getCoinsByIds(ids)
        } catch (e: Exception) {
            NetworkResult.Error(e.toUserMessage())
        }
    }

    suspend fun getPriceHistory(coinId: String, days: String): NetworkResult<List<PricePoint>> {
        val cacheKey = "chart_${coinId}_$days"
        return withRetryAndCache(
            cache = chartCache,
            cacheKey = cacheKey,
            fetch = {
                coinGeckoApi.getMarketChart(coinId = coinId, days = days).prices.map {
                    PricePoint(timestampMillis = it[0].toLong(), price = it[1])
                }
            }
        )
    }

    suspend fun getTrendingDexPairs(query: String = "solana"): NetworkResult<List<DexPair>> {
        val cacheKey = "dex_$query"
        return withRetryAndCache(
            cache = dexCache,
            cacheKey = cacheKey,
            fetch = {
                dexScreenerApi.searchPairs(query).pairs.orEmpty().mapNotNull { it.toDomainOrNull() }
            }
        )
    }

    /** Generic fetch helper: cache -> retry(backoff) -> stale-cache fallback. */
    private suspend fun <T> withRetryAndCache(
        cache: ConcurrentHashMap<String, CacheEntry<T>>,
        cacheKey: String,
        fetch: suspend () -> T
    ): NetworkResult<T> {
        val cached = cache[cacheKey]
        val isFresh = cached != null &&
            (System.currentTimeMillis() - cached.timestampMillis) < CACHE_TTL_MILLIS
        if (isFresh) {
            return NetworkResult.Success(cached!!.value, isFromCache = false)
        }

        var lastError: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = fetch()
                cache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
                return NetworkResult.Success(result, isFromCache = false)
            } catch (e: Exception) {
                lastError = e
                if (e.isRateLimited()) {
                    // Back off longer for HTTP 429 than for a generic retry.
                    delay(RATE_LIMIT_BACKOFF_MILLIS * (attempt + 1))
                } else {
                    delay(RETRY_BACKOFF_MILLIS * (attempt + 1))
                }
            }
        }

        // All retries failed: fall back to the last cached value if we have one.
        cached?.let {
            return NetworkResult.Success(it.value, isFromCache = true)
        }

        return NetworkResult.Error(
            message = lastError?.toUserMessage() ?: ErrorMessages.GENERIC,
            isRateLimited = lastError?.isRateLimited() ?: false
        )
    }

    private fun Exception.isRateLimited(): Boolean =
        this is HttpException && code() == 429

    private fun Exception.toUserMessage(): String = when {
        this is IOException -> ErrorMessages.NO_INTERNET
        this is HttpException && code() == 429 -> ErrorMessages.RATE_LIMITED
        this is HttpException && code() == 404 -> ErrorMessages.INVALID_COIN
        this is HttpException -> ErrorMessages.API_UNREACHABLE
        else -> ErrorMessages.GENERIC
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 30_000L
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MILLIS = 800L
        private const val RATE_LIMIT_BACKOFF_MILLIS = 2_000L
    }
}

private fun com.papertrader.app.data.remote.coingecko.CoinMarketDto.toDomain(): Coin = Coin(
    id = id,
    symbol = symbol.uppercase(),
    name = name,
    imageUrl = image,
    currentPrice = currentPrice ?: 0.0,
    priceChangePercent24h = priceChangePercentage24h ?: 0.0,
    marketCap = marketCap ?: 0.0,
    volume24h = totalVolume ?: 0.0,
    high24h = high24h ?: 0.0,
    low24h = low24h ?: 0.0,
    ath = ath ?: 0.0,
    atl = atl ?: 0.0
)

private fun com.papertrader.app.data.remote.dexscreener.DexPairDto.toDomainOrNull(): DexPair? {
    val price = priceUsd?.toDoubleOrNull() ?: return null
    return DexPair(
        pairAddress = pairAddress,
        chain = chainId,
        dexName = dexId,
        baseTokenSymbol = baseToken.symbol,
        quoteTokenSymbol = quoteToken.symbol,
        priceUsd = price,
        priceChange24h = priceChange?.h24 ?: 0.0,
        liquidityUsd = liquidity?.usd ?: 0.0,
        volume24h = volume?.h24 ?: 0.0,
        url = url
    )
}
