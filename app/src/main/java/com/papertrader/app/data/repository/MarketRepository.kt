package com.papertrader.app.data.repository

import com.google.gson.JsonElement
import com.papertrader.app.data.remote.binance.BinanceApi
import com.papertrader.app.data.remote.coingecko.CoinGeckoApi
import com.papertrader.app.data.remote.dexscreener.DexScreenerApi
import com.papertrader.app.data.remote.yahoo.YahooFinanceApi
import com.papertrader.app.data.remote.yahoo.YahooMeta
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.domain.model.PricePoint
import com.papertrader.app.util.ErrorMessages
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for external market data:
 *  - CoinGecko: crypto prices / market caps (fallback history)
 *  - Binance: crypto OHLC candles + live tail (no key)
 *  - Yahoo Finance: stock quotes, candles and search (no key)
 *  - DexScreener: DEX pairs
 *
 * Every call goes through an in-memory cache, and list calls fall back to the
 * last cached value when the network fails. It only reads public market data.
 */
class MarketRepository(
    private val coinGeckoApi: CoinGeckoApi,
    private val dexScreenerApi: DexScreenerApi,
    private val binanceApi: BinanceApi,
    private val yahooApi: YahooFinanceApi
) {
    private data class CacheEntry<T>(val value: T, val timestampMillis: Long)

    private val marketsCache = ConcurrentHashMap<String, CacheEntry<List<Coin>>>()
    private val chartCache = ConcurrentHashMap<String, CacheEntry<List<PricePoint>>>()
    private val dexCache = ConcurrentHashMap<String, CacheEntry<List<DexPair>>>()
    private val candleCache = ConcurrentHashMap<String, CacheEntry<List<Candle>>>()
    private val stockQuoteCache = ConcurrentHashMap<String, CacheEntry<Coin>>()
    private val unsupportedBinanceSymbols: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // ---------- Instant-paint helpers ----------

    /** Last known top-coins list regardless of age (for instant first paint), or null. */
    fun peekTopCoins(perPage: Int = 100): List<Coin>? = marketsCache["top_$perPage"]?.value

    /** Last known data for one asset regardless of age, or null. */
    fun peekCoin(coinId: String): Coin? =
        stockQuoteCache[coinId]?.value
            ?: marketsCache["top_100"]?.value?.firstOrNull { it.id == coinId }

    private fun freshCoinsFromTopCache(ids: List<String>): List<Coin>? {
        val entry = marketsCache["top_100"] ?: return null
        if (System.currentTimeMillis() - entry.timestampMillis >= CACHE_TTL_MILLIS) return null
        val byId = entry.value.associateBy { it.id }
        val found = ids.mapNotNull { byId[it] }
        return if (found.size == ids.size) found else null
    }

    // ---------- Crypto lists ----------

    suspend fun getTopCoins(perPage: Int = 100): NetworkResult<List<Coin>> {
        val cacheKey = "top_$perPage"
        return withRetryAndCache(
            cache = marketsCache,
            cacheKey = cacheKey,
            fetch = { coinGeckoApi.getMarkets(perPage = perPage).map { it.toDomain() } }
        )
    }

    /** Works for crypto ids and "stock_" ids alike. */
    suspend fun getCoinsByIds(ids: List<String>): NetworkResult<List<Coin>> {
        if (ids.isEmpty()) return NetworkResult.Success(emptyList())
        val (stockIds, cryptoIds) = ids.partition { AssetIds.isStock(it) }
        val stocks = if (stockIds.isEmpty()) emptyList() else getStockQuotes(stockIds)
        if (cryptoIds.isEmpty()) {
            return if (stocks.isNotEmpty()) NetworkResult.Success(stocks)
            else NetworkResult.Error(ErrorMessages.API_UNREACHABLE)
        }
        return when (val crypto = getCryptoCoinsByIds(cryptoIds)) {
            is NetworkResult.Success -> NetworkResult.Success(crypto.data + stocks, crypto.isFromCache)
            is NetworkResult.Error -> if (stocks.isNotEmpty()) NetworkResult.Success(stocks) else crypto
        }
    }

    private suspend fun getCryptoCoinsByIds(ids: List<String>): NetworkResult<List<Coin>> {
        freshCoinsFromTopCache(ids)?.let { return NetworkResult.Success(it) }
        val cacheKey = "ids_${ids.sorted().joinToString(",")}"
        return withRetryAndCache(
            cache = marketsCache,
            cacheKey = cacheKey,
            fetch = { coinGeckoApi.getMarketsByIds(ids = ids.joinToString(",")).map { it.toDomain() } }
        )
    }

    suspend fun searchCoins(query: String): NetworkResult<List<Coin>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        return try {
            val results = coinGeckoApi.search(query).coins.take(15)
            getCoinsByIds(results.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.toUserMessage())
        }
    }

    // ---------- Stocks (Yahoo Finance) ----------

    suspend fun getPopularStocks(): NetworkResult<List<Coin>> {
        val coins = getStockQuotes(POPULAR_STOCKS.map { AssetIds.stock(it) })
        return if (coins.isEmpty()) NetworkResult.Error(ErrorMessages.API_UNREACHABLE)
        else NetworkResult.Success(coins)
    }

    suspend fun searchStocks(query: String): NetworkResult<List<Coin>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        return try {
            val symbols = yahooApi.search(query.trim()).quotes.orEmpty()
                .filter { it.quoteType == "EQUITY" || it.quoteType == "ETF" }
                .mapNotNull { it.symbol }
                .filter { s -> s.isNotEmpty() && s.all { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '-' } }
                .distinct()
                .take(8)
            NetworkResult.Success(getStockQuotes(symbols.map { AssetIds.stock(it) }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.toUserMessage())
        }
    }

    /** Uncached quote used by the live poll on the detail screen. */
    suspend fun getLiveStockCoin(coin: Coin): Coin? =
        safeCall { fetchStockQuote(AssetIds.stockSymbol(coin.id)) }
            ?.also { stockQuoteCache[coin.id] = CacheEntry(it, System.currentTimeMillis()) }

    private suspend fun getStockQuotes(stockIds: List<String>): List<Coin> = coroutineScope {
        stockIds.map { id ->
            async {
                val cached = stockQuoteCache[id]
                val fresh = cached != null &&
                    System.currentTimeMillis() - cached.timestampMillis < CACHE_TTL_MILLIS
                if (fresh && cached != null) {
                    cached.value
                } else {
                    safeCall { fetchStockQuote(AssetIds.stockSymbol(id)) }
                        ?.also { stockQuoteCache[id] = CacheEntry(it, System.currentTimeMillis()) }
                        ?: cached?.value
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchStockQuote(symbol: String): Coin? {
        val meta = yahooApi.getChart(symbol = symbol, range = "1d", interval = "1d")
            .chart?.result?.firstOrNull()?.meta ?: return null
        return meta.toStockCoin(symbol)
    }

    private suspend fun <T> safeCall(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    // ---------- Candles (chart data) ----------

    suspend fun getCandles(coin: Coin, range: ChartRange): NetworkResult<List<Candle>> {
        val key = "candles_${coin.id}_${range.name}"
        val cached = candleCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestampMillis < CANDLE_TTL_MILLIS) {
            return NetworkResult.Success(cached.value)
        }
        return try {
            val candles = if (AssetIds.isStock(coin.id)) {
                fetchYahooCandles(AssetIds.stockSymbol(coin.id), range)
            } else {
                fetchCryptoCandles(coin, range)
            }
            if (candles.size < 2) {
                staleOrError(cached, ErrorMessages.NO_MARKET_DATA)
            } else {
                candleCache[key] = CacheEntry(candles, System.currentTimeMillis())
                NetworkResult.Success(candles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            staleOrError(cached, e.toUserMessage())
        }
    }

    private fun staleOrError(cached: CacheEntry<List<Candle>>?, message: String): NetworkResult<List<Candle>> =
        if (cached != null) NetworkResult.Success(cached.value, isFromCache = true)
        else NetworkResult.Error(message)

    /** Last two exchange candles for a live update, or null if this asset has no exchange feed. */
    suspend fun getLiveTail(coin: Coin, range: ChartRange): List<Candle>? {
        val pair = binancePairFor(coin) ?: return null
        return safeCall {
            binanceApi.getKlines(pair, range.binanceInterval, 2).mapNotNull { it.toCandleOrNull() }
        }?.takeIf { it.isNotEmpty() }
    }

    private fun binancePairFor(coin: Coin): String? {
        if (AssetIds.isStock(coin.id) || coin.marketCap < MIN_MARKET_CAP_FOR_EXCHANGE_DATA) return null
        val base = coin.symbol.uppercase().filter { it.isLetterOrDigit() }
        if (base.isEmpty() || base == "USDT" || base == "USDC" || base in unsupportedBinanceSymbols) return null
        return base + "USDT"
    }

    private suspend fun fetchCryptoCandles(coin: Coin, range: ChartRange): List<Candle> {
        val pair = binancePairFor(coin)
        if (pair != null) {
            try {
                val candles = binanceApi.getKlines(pair, range.binanceInterval, range.binanceLimit)
                    .mapNotNull { it.toCandleOrNull() }
                if (candles.size >= 2) return candles
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    unsupportedBinanceSymbols.add(coin.symbol.uppercase().filter { it.isLetterOrDigit() })
                }
            } catch (e: IOException) {
                // Exchange not reachable: fall back to CoinGecko below.
            }
        }
        val prices = coinGeckoApi.getMarketChart(coinId = coin.id, days = range.coinGeckoDays).prices
        return pricesToCandles(prices, range)
    }

    private fun pricesToCandles(prices: List<List<Double>>, range: ChartRange): List<Candle> {
        var points = prices.filter { it.size >= 2 }.map { it[0].toLong() to it[1] }
        if (range == ChartRange.ONE_HOUR) {
            val since = System.currentTimeMillis() - 3_600_000L
            points = points.filter { it.first >= since }.ifEmpty { points.takeLast(12) }
        }
        return points.mapIndexed { i, (time, price) ->
            val open = if (i == 0) price else points[i - 1].second
            Candle(time, open, maxOf(open, price), minOf(open, price), price)
        }
    }

    private suspend fun fetchYahooCandles(symbol: String, range: ChartRange): List<Candle> {
        val result = yahooApi.getChart(symbol, range.yahooRange, range.yahooInterval)
            .chart?.result?.firstOrNull() ?: return emptyList()
        val timestamps = result.timestamp ?: return emptyList()
        val quote = result.indicators?.quote?.firstOrNull() ?: return emptyList()
        val out = ArrayList<Candle>(timestamps.size)
        for (i in timestamps.indices) {
            val o = quote.open?.getOrNull(i)
            val h = quote.high?.getOrNull(i)
            val l = quote.low?.getOrNull(i)
            val c = quote.close?.getOrNull(i)
            if (o == null || h == null || l == null || c == null) continue
            out.add(Candle(timestamps[i] * 1000L, o, h, l, c, quote.volume?.getOrNull(i) ?: 0.0))
        }
        return if (range == ChartRange.ONE_HOUR) out.takeLast(60) else out
    }

    // ---------- Older APIs kept for compatibility ----------

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
            fetch = { dexScreenerApi.searchPairs(query).pairs.orEmpty().mapNotNull { it.toDomainOrNull() } }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (e.isRateLimited()) {
                    delay(RATE_LIMIT_BACKOFF_MILLIS * (attempt + 1))
                } else {
                    delay(RETRY_BACKOFF_MILLIS * (attempt + 1))
                }
            }
        }

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
        private const val CANDLE_TTL_MILLIS = 15_000L
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MILLIS = 800L
        private const val RATE_LIMIT_BACKOFF_MILLIS = 2_000L
        private const val MIN_MARKET_CAP_FOR_EXCHANGE_DATA = 100_000_000.0

        val POPULAR_STOCKS = listOf(
            "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA", "NFLX", "AMD",
            "INTC", "DIS", "NKE", "KO", "MCD", "V", "JPM", "COIN", "SPY", "QQQ"
        )
    }
}

private fun JsonElement.toCandleOrNull(): Candle? = try {
    val row = asJsonArray
    Candle(
        timeMillis = row[0].asLong,
        open = row[1].asDouble,
        high = row[2].asDouble,
        low = row[3].asDouble,
        close = row[4].asDouble,
        volume = row[5].asDouble
    )
} catch (e: Exception) {
    null
}

private fun YahooMeta.toStockCoin(symbol: String): Coin? {
    val price = regularMarketPrice ?: return null
    val prev = chartPreviousClose ?: previousClose ?: price
    val changePct = if (prev > 0.0) (price - prev) / prev * 100.0 else 0.0
    return Coin(
        id = AssetIds.stock(symbol),
        symbol = symbol.uppercase(),
        name = longName ?: shortName ?: symbol.uppercase(),
        imageUrl = null,
        currentPrice = price,
        priceChangePercent24h = changePct,
        marketCap = 0.0,
        volume24h = regularMarketVolume ?: 0.0,
        high24h = regularMarketDayHigh ?: price,
        low24h = regularMarketDayLow ?: price,
        ath = fiftyTwoWeekHigh ?: price,
        atl = fiftyTwoWeekLow ?: price
    )
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
