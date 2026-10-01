package com.papertrader.app.data.repository

import com.google.gson.JsonElement
import com.papertrader.app.data.remote.binance.BinanceApi
import com.papertrader.app.data.remote.binance.BinanceTickerDto
import com.papertrader.app.data.remote.coingecko.CoinGeckoApi
import com.papertrader.app.data.remote.dexscreener.DexScreenerApi
import com.papertrader.app.data.remote.multi.CryptoFeeds
import com.papertrader.app.data.remote.multi.Polymarket
import com.papertrader.app.data.remote.multi.SourceGuard
import com.papertrader.app.data.remote.multi.SpotQuote
import com.papertrader.app.data.remote.multi.StockFeeds
import com.papertrader.app.data.remote.multi.StockQuote
import com.papertrader.app.domain.model.BetCategory
import com.papertrader.app.domain.model.BetMarket
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val topCoinsMutex = Mutex()
    @Volatile private var coinGeckoCooldownUntil = 0L
    private val yahooGuard = SourceGuard("Yahoo")
    private val binanceGuard = SourceGuard("Binance")
    private val knownCoins = ConcurrentHashMap<String, Coin>()
    private val betListCache = ConcurrentHashMap<String, CacheEntry<List<BetMarket>>>()
    private val betMarketCache = ConcurrentHashMap<String, CacheEntry<BetMarket>>()
    private val tagIdCache = ConcurrentHashMap<String, String>()

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

    /**
     * Top coins. CoinGecko first (real market caps); if it is rate-limited or down, the list is
     * built from [SeedCoins] with live prices from Binance, so this never comes back empty.
     */
    suspend fun getTopCoins(perPage: Int = 100): NetworkResult<List<Coin>> {
        val key = "top_$perPage"
        return topCoinsMutex.withLock {
            val cached = marketsCache[key]
            if (cached != null && System.currentTimeMillis() - cached.timestampMillis < CACHE_TTL_MILLIS) {
                return@withLock NetworkResult.Success(cached.value)
            }
            val fromGecko = safeCall {
                coinGeckoCall { coinGeckoApi.getMarkets(perPage = perPage).map { it.toDomain() } }
            }
            if (!fromGecko.isNullOrEmpty()) {
                fromGecko.forEach { knownCoins[it.id] = it }
                marketsCache[key] = CacheEntry(fromGecko, System.currentTimeMillis())
                return@withLock NetworkResult.Success(fromGecko)
            }
            val fallback = fetchSeedCoins(SeedCoins.all.take(perPage))
            if (fallback.isNotEmpty()) {
                marketsCache[key] = CacheEntry(fallback, System.currentTimeMillis())
                return@withLock NetworkResult.Success(fallback)
            }
            if (cached != null) NetworkResult.Success(cached.value, isFromCache = true)
            else NetworkResult.Error(ErrorMessages.API_UNREACHABLE)
        }
    }

    /** Works for crypto ids, "stock_" ids and "pm_" prediction-market ids alike. */
    suspend fun getCoinsByIds(ids: List<String>): NetworkResult<List<Coin>> {
        if (ids.isEmpty()) return NetworkResult.Success(emptyList())
        val stockIds = ids.filter { AssetIds.isStock(it) }
        val betIds = ids.filter { AssetIds.isBet(it) }
        val cryptoIds = ids.filter { !AssetIds.isStock(it) && !AssetIds.isBet(it) }
        val extra = (if (stockIds.isEmpty()) emptyList() else getStockQuotes(stockIds)) +
            (if (betIds.isEmpty()) emptyList() else getBetCoins(betIds))
        if (cryptoIds.isEmpty()) {
            return if (extra.isNotEmpty()) NetworkResult.Success(extra)
            else NetworkResult.Error(ErrorMessages.API_UNREACHABLE)
        }
        return when (val crypto = getCryptoCoinsByIds(cryptoIds)) {
            is NetworkResult.Success -> NetworkResult.Success(crypto.data + extra, crypto.isFromCache)
            is NetworkResult.Error -> if (extra.isNotEmpty()) NetworkResult.Success(extra) else crypto
        }
    }

    private suspend fun getCryptoCoinsByIds(ids: List<String>): NetworkResult<List<Coin>> {
        freshCoinsFromTopCache(ids)?.let { return NetworkResult.Success(it) }
        val key = "ids_${ids.sorted().joinToString(",")}"
        val cached = marketsCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestampMillis < CACHE_TTL_MILLIS) {
            return NetworkResult.Success(cached.value)
        }
        val fromGecko = safeCall {
            coinGeckoCall { coinGeckoApi.getMarketsByIds(ids = ids.joinToString(",")).map { it.toDomain() } }
        }
        if (!fromGecko.isNullOrEmpty()) {
            fromGecko.forEach { knownCoins[it.id] = it }
            marketsCache[key] = CacheEntry(fromGecko, System.currentTimeMillis())
            return NetworkResult.Success(fromGecko)
        }
        val seeds = ids.mapNotNull { id ->
            SeedCoins.byId[id] ?: knownCoins[id]?.let { known ->
                SeedCoin(
                    known.id, known.symbol, known.name, known.imageUrl.orEmpty(),
                    if (known.currentPrice > 0.0) known.marketCap / known.currentPrice else 0.0
                )
            }
        }
        if (seeds.isNotEmpty()) {
            val fallback = fetchSeedCoins(seeds)
            if (fallback.isNotEmpty()) {
                marketsCache[key] = CacheEntry(fallback, System.currentTimeMillis())
                return NetworkResult.Success(fallback)
            }
        }
        return if (cached != null) NetworkResult.Success(cached.value, isFromCache = true)
        else NetworkResult.Error(ErrorMessages.API_UNREACHABLE)
    }

    suspend fun searchCoins(query: String): NetworkResult<List<Coin>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        val q = query.trim()
        val ids = safeCall { coinGeckoCall { coinGeckoApi.search(q).coins.take(15).map { it.id } } }
            ?: SeedCoins.all
                .filter { it.name.contains(q, ignoreCase = true) || it.symbol.contains(q, ignoreCase = true) }
                .take(15)
                .map { it.id }
        if (ids.isEmpty()) return NetworkResult.Success(emptyList())
        return getCoinsByIds(ids)
    }

    /** Live prices for coins we know by CoinGecko id: Binance first, then the other exchanges. */
    private suspend fun fetchSeedCoins(seeds: List<SeedCoin>): List<Coin> {
        val symbols = seeds.map { it.symbol }.filter { it != "USDT" }.toSet()
        val quotes = HashMap<String, SpotQuote>()
        if (symbols.isNotEmpty()) {
            safeCall { binanceQuotes(symbols) }?.let { quotes.putAll(it) }
            CryptoFeeds.fillMissing(quotes, symbols)
        }
        return seeds.mapNotNull { seed -> seed.toCoin(quotes[seed.symbol]) }
    }

    private suspend fun binanceQuotes(symbols: Set<String>): Map<String, SpotQuote> = binanceGuard.call {
        val json = symbols.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it}USDT\"" }
        val tickers = safeCall { binanceApi.getTickers(json) } ?: binanceApi.getAllTickers()
        tickers.mapNotNull { t -> t.toSpotQuote() }.associateBy { it.symbol }
    }

    private class CoinGeckoCoolingDownException : IOException("CoinGecko cooling down")

    /** After a rate-limit/auth error CoinGecko is skipped for a minute instead of hammering it. */
    private suspend fun <T> coinGeckoCall(block: suspend () -> T): T {
        if (System.currentTimeMillis() < coinGeckoCooldownUntil) throw CoinGeckoCoolingDownException()
        try {
            return block()
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() == 401 || e.code() == 403) {
                coinGeckoCooldownUntil = System.currentTimeMillis() + COINGECKO_COOLDOWN_MILLIS
            }
            throw e
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
        val q = query.trim()
        return try {
            val fromYahoo = safeCall {
                yahooGuard.call {
                    yahooApi.search(q).quotes.orEmpty()
                        .filter { it.quoteType == "EQUITY" || it.quoteType == "ETF" }
                        .mapNotNull { it.symbol }
                }
            }
            val symbols = (fromYahoo
                ?: safeCall { StockFeeds.nasdaqSearch(q) }
                ?: POPULAR_STOCKS.filter { it.contains(q, ignoreCase = true) })
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
    suspend fun getLiveStockCoin(coin: Coin): Coin? {
        val symbol = AssetIds.stockSymbol(coin.id).uppercase()
        val fresh = fetchStockCoins(listOf(symbol))[symbol] ?: return null
        stockQuoteCache[coin.id] = CacheEntry(fresh, System.currentTimeMillis())
        return fresh
    }

    private suspend fun getStockQuotes(stockIds: List<String>): List<Coin> {
        val now = System.currentTimeMillis()
        val stale = stockIds.filter { id ->
            val cached = stockQuoteCache[id]
            cached == null || now - cached.timestampMillis >= CACHE_TTL_MILLIS
        }
        if (stale.isNotEmpty()) {
            val fetched = fetchStockCoins(stale.map { AssetIds.stockSymbol(it).uppercase() })
            for ((symbol, coin) in fetched) {
                stockQuoteCache[AssetIds.stock(symbol)] = CacheEntry(coin, System.currentTimeMillis())
            }
        }
        return stockIds.mapNotNull { stockQuoteCache[it]?.value }
    }

    /**
     * Quotes for several symbols without depending on one provider: CNBC answers all of them in
     * a single request, Yahoo and Nasdaq fill in whatever is still missing.
     */
    private suspend fun fetchStockCoins(symbols: List<String>): Map<String, Coin> {
        val out = HashMap<String, Coin>()
        safeCall { StockFeeds.cnbcQuotes(symbols) }?.forEach { (symbol, quote) -> out[symbol] = quote.toCoin() }
        val missing = symbols.filter { it !in out }
        if (missing.isNotEmpty()) {
            coroutineScope {
                missing
                    .map { symbol -> async { symbol to safeCall { yahooGuard.call { fetchStockQuote(symbol) } } } }
                    .awaitAll()
                    .forEach { (symbol, coin) -> if (coin != null) out[symbol] = coin }
            }
        }
        for (symbol in symbols.filter { it !in out }) {
            safeCall { StockFeeds.nasdaqQuote(symbol) }?.let { out[symbol] = it.toCoin() }
        }
        return out
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
                fetchStockCandles(AssetIds.stockSymbol(coin.id), range)
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
        val okx = safeCall { CryptoFeeds.okxCandles(coin.symbol, range.binanceInterval, range.binanceLimit) }
        if (okx != null && okx.size >= 2) return okx
        val prices = coinGeckoCall { coinGeckoApi.getMarketChart(coinId = coin.id, days = range.coinGeckoDays).prices }
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

    /** Yahoo first; if it is limited, CNBC's chart feed aggregated to the same candle size. */
    private suspend fun fetchStockCandles(symbol: String, range: ChartRange): List<Candle> {
        val yahoo = safeCall { yahooGuard.call { fetchYahooCandles(symbol, range) } }
        if (yahoo != null && yahoo.size >= 2) return yahoo
        val day = 86_400_000L
        val (cnbcRange, window, bucket) = when (range) {
            ChartRange.ONE_HOUR -> Triple("1D", 3_600_000L, 60_000L)
            ChartRange.ONE_DAY -> Triple("1D", day, 300_000L)
            ChartRange.ONE_WEEK -> Triple("5D", 7 * day, 1_800_000L)
            ChartRange.ONE_MONTH -> Triple("1Y", 30 * day, day)
            ChartRange.THREE_MONTHS -> Triple("1Y", 90 * day, day)
            ChartRange.ONE_YEAR -> Triple("1Y", 365 * day, day)
            ChartRange.ALL -> Triple("ALL", Long.MAX_VALUE, 1L)
        }
        return aggregateCandles(StockFeeds.cnbcCandles(symbol, cnbcRange), window, bucket)
    }

    private fun aggregateCandles(raw: List<Candle>, windowMillis: Long, bucketMillis: Long): List<Candle> {
        if (raw.isEmpty()) return raw
        val sorted = raw.sortedBy { it.timeMillis }
        val cutoff = if (windowMillis == Long.MAX_VALUE) Long.MIN_VALUE else sorted.last().timeMillis - windowMillis
        val within = sorted.filter { it.timeMillis >= cutoff }
        if (bucketMillis <= 1L) return within
        val out = ArrayList<Candle>()
        var current: Candle? = null
        var currentBucket = Long.MIN_VALUE
        for (c in within) {
            val bucket = c.timeMillis / bucketMillis
            val cur = current
            if (cur == null || bucket != currentBucket) {
                if (cur != null) out.add(cur)
                current = c.copy(timeMillis = bucket * bucketMillis)
                currentBucket = bucket
            } else {
                current = cur.copy(
                    high = maxOf(cur.high, c.high),
                    low = minOf(cur.low, c.low),
                    close = c.close,
                    volume = cur.volume + c.volume
                )
            }
        }
        current?.let { out.add(it) }
        return out
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

    // ---------- Prediction markets & sports (Polymarket) ----------

    suspend fun getBetMarkets(category: BetCategory): NetworkResult<List<BetMarket>> {
        val key = category.name
        val cached = betListCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestampMillis < BET_LIST_TTL_MILLIS) {
            return NetworkResult.Success(cached.value)
        }
        return try {
            val slug = category.slug
            val tagId = if (slug == null) null else (tagIdCache[slug] ?: Polymarket.tagId(slug)?.also { tagIdCache[slug] = it })
            if (slug != null && tagId == null) throw IOException("category unavailable")
            val list = Polymarket.markets(tagId, 30).filter { !it.closed }
            list.forEach { rememberBetMarket(it) }
            betListCache[key] = CacheEntry(list, System.currentTimeMillis())
            NetworkResult.Success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) NetworkResult.Success(cached.value, isFromCache = true)
            else NetworkResult.Error(e.toUserMessage())
        }
    }

    suspend fun searchBets(query: String): NetworkResult<List<BetMarket>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        return try {
            val list = Polymarket.search(query.trim()).filter { !it.closed }.take(15)
            list.forEach { rememberBetMarket(it) }
            NetworkResult.Success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.toUserMessage())
        }
    }

    /** One market looked up by any of its outcome token ids. */
    suspend fun getBetMarket(tokenId: String, fresh: Boolean = false): NetworkResult<BetMarket> {
        val cached = betMarketCache[tokenId]
        if (!fresh && cached != null && System.currentTimeMillis() - cached.timestampMillis < BET_MARKET_TTL_MILLIS) {
            return NetworkResult.Success(cached.value)
        }
        return try {
            val market = Polymarket.byToken(tokenId)
            if (market != null) {
                rememberBetMarket(market)
                NetworkResult.Success(market)
            } else if (cached != null) {
                NetworkResult.Success(cached.value, isFromCache = true)
            } else {
                NetworkResult.Error(ErrorMessages.NO_MARKET_DATA)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) NetworkResult.Success(cached.value, isFromCache = true)
            else NetworkResult.Error(e.toUserMessage())
        }
    }

    suspend fun getBetHistory(tokenId: String, range: ChartRange): NetworkResult<List<Candle>> {
        val day = 86_400_000L
        val (interval, fidelity, windowMillis) = when (range) {
            ChartRange.ONE_HOUR -> Triple("1h", 1, 0L)
            ChartRange.ONE_DAY -> Triple("1d", 5, 0L)
            ChartRange.ONE_WEEK -> Triple("1w", 30, 0L)
            ChartRange.ONE_MONTH -> Triple("1m", 180, 0L)
            ChartRange.THREE_MONTHS -> Triple("max", 720, 90 * day)
            ChartRange.ONE_YEAR -> Triple("max", 1440, 365 * day)
            ChartRange.ALL -> Triple("max", 1440, 0L)
        }
        return try {
            var points = Polymarket.history(tokenId, interval, fidelity)
            if (points.size < 2) points = Polymarket.history(tokenId, interval, maxOf(fidelity, 60))
            if (windowMillis > 0L) {
                val since = System.currentTimeMillis() - windowMillis
                points = points.filter { it.first >= since }
            }
            if (points.size < 2) {
                NetworkResult.Error(ErrorMessages.NO_MARKET_DATA)
            } else {
                val sorted = points.sortedBy { it.first }
                val candles = sorted.mapIndexed { i, (time, price) ->
                    val open = if (i == 0) price else sorted[i - 1].second
                    Candle(time, open, maxOf(open, price), minOf(open, price), price)
                }
                NetworkResult.Success(candles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.toUserMessage())
        }
    }

    private suspend fun getBetCoins(ids: List<String>): List<Coin> = coroutineScope {
        ids.map { id ->
            async {
                val token = AssetIds.betToken(id)
                val market = when (val r = getBetMarket(token)) {
                    is NetworkResult.Success -> r.data
                    is NetworkResult.Error -> null
                }
                val index = market?.tokenIds?.indexOf(token) ?: -1
                if (market != null && index >= 0) market.toCoin(index) else null
            }
        }.awaitAll().filterNotNull()
    }

    private fun rememberBetMarket(market: BetMarket) {
        val now = System.currentTimeMillis()
        market.tokenIds.forEach { betMarketCache[it] = CacheEntry(market, now) }
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
        private const val COINGECKO_COOLDOWN_MILLIS = 60_000L
        private const val BET_LIST_TTL_MILLIS = 45_000L
        private const val BET_MARKET_TTL_MILLIS = 8_000L

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

private fun BinanceTickerDto.toSpotQuote(): SpotQuote? {
    if (!symbol.endsWith("USDT")) return null
    val price = lastPrice.toDoubleOrNull() ?: return null
    return SpotQuote(
        symbol.removeSuffix("USDT"), price,
        priceChangePercent.toDoubleOrNull() ?: 0.0,
        highPrice.toDoubleOrNull() ?: price,
        lowPrice.toDoubleOrNull() ?: price,
        quoteVolume.toDoubleOrNull() ?: 0.0
    )
}

private fun SeedCoin.toCoin(quote: SpotQuote?): Coin? {
    if (symbol == "USDT") {
        return Coin(
            id = id, symbol = symbol, name = name, imageUrl = image.ifEmpty { null },
            currentPrice = 1.0, priceChangePercent24h = 0.0, marketCap = supply,
            volume24h = 0.0, high24h = 1.0, low24h = 1.0, ath = 1.0, atl = 1.0
        )
    }
    val q = quote ?: return null
    return Coin(
        id = id,
        symbol = symbol,
        name = name,
        imageUrl = image.ifEmpty { null },
        currentPrice = q.price,
        priceChangePercent24h = q.changePct,
        marketCap = q.price * supply,
        volume24h = q.quoteVolume,
        high24h = q.high,
        low24h = q.low,
        ath = q.high,
        atl = q.low
    )
}

private fun StockQuote.toCoin(): Coin = Coin(
    id = AssetIds.stock(symbol),
    symbol = symbol,
    name = name,
    imageUrl = null,
    currentPrice = price,
    priceChangePercent24h = changePct,
    marketCap = 0.0,
    volume24h = volume,
    high24h = high,
    low24h = low,
    ath = yearHigh,
    atl = yearLow
)

/** One outcome of a prediction market as a tradable "coin" priced between 0 and 1 dollar. */
private fun BetMarket.toCoin(index: Int): Coin {
    val price = prices[index]
    val change = if (prices.size == 2 && oneDayChange != 0.0) {
        val delta = if (index == 0) oneDayChange else -oneDayChange
        val base = price - delta
        if (base > 0.0) delta / base * 100.0 else 0.0
    } else {
        0.0
    }
    return Coin(
        id = AssetIds.bet(tokenIds[index]),
        symbol = outcomes[index],
        name = question,
        imageUrl = image,
        currentPrice = price,
        priceChangePercent24h = change,
        marketCap = 0.0,
        volume24h = volume24h,
        high24h = price,
        low24h = price,
        ath = 1.0,
        atl = 0.0,
        resolved = closed
    )
}
