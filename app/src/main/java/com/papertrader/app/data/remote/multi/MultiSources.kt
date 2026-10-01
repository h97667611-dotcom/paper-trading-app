package com.papertrader.app.data.remote.multi

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.papertrader.app.domain.model.Candle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HttpStatusException(val code: Int) : IOException("HTTP $code")
class SourceCoolingDownException(source: String) : IOException("$source is cooling down")

/** Tiny keyless HTTP helper shared by all extra data sources. */
object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body?.string() ?: throw IOException("empty body")
        }
    }

    suspend fun json(url: String): JsonElement = JsonParser.parseString(text(url))

    fun url(base: String, vararg params: Pair<String, String>): String {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { builder.addQueryParameter(it.first, it.second) }
        return builder.build().toString()
    }
}

/**
 * Protects one data source from being hammered: calls can be spaced out, and after a failure
 * (especially a rate limit) the source is skipped for a growing cool-down so the app moves on
 * to the next source instead of waiting or retrying.
 */
class SourceGuard(val name: String, private val minIntervalMs: Long = 0L) {
    private val spacing = Mutex()
    @Volatile private var blockedUntil = 0L
    @Volatile private var failures = 0
    private var lastCall = 0L

    fun isAvailable(): Boolean = System.currentTimeMillis() >= blockedUntil

    suspend fun <T> call(block: suspend () -> T): T {
        if (!isAvailable()) throw SourceCoolingDownException(name)
        if (minIntervalMs > 0L) {
            spacing.withLock {
                val wait = lastCall + minIntervalMs - System.currentTimeMillis()
                if (wait > 0L) delay(wait)
                lastCall = System.currentTimeMillis()
            }
        }
        try {
            val result = block()
            failures = 0
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (!message.contains("404")) {
                failures++
                val limited = message.contains("429") || message.contains("403") || message.contains("418")
                val base = if (limited) 60_000L else 15_000L
                blockedUntil = System.currentTimeMillis() + minOf(base * failures, 300_000L)
            }
            throw e
        }
    }
}

private fun JsonObject.str(key: String): String =
    get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() } ?: ""

private fun JsonObject.dbl(key: String): Double? =
    get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString.toDouble() }.getOrNull() }

// ======================= Crypto: spot price feeds =======================

data class SpotQuote(
    val symbol: String,
    val price: Double,
    val changePct: Double,
    val high: Double,
    val low: Double,
    val quoteVolume: Double
)

/** One exchange's "all tickers" endpoint, parsed once and shared for a few seconds. */
private class TickerFeed(
    private val guard: SourceGuard,
    private val ttlMs: Long,
    private val load: suspend () -> Map<String, SpotQuote>
) {
    private val lock = Mutex()
    private var cached: Map<String, SpotQuote>? = null
    private var cachedAt = 0L

    suspend fun get(): Map<String, SpotQuote> = lock.withLock {
        val current = cached
        if (current != null && System.currentTimeMillis() - cachedAt < ttlMs) return@withLock current
        val fresh = guard.call { load() }
        cached = fresh
        cachedAt = System.currentTimeMillis()
        fresh
    }
}

/**
 * Keyless crypto price sources used when the first choices are limited or down. They are tried
 * tier by tier (rotating inside a tier so no single exchange sees all the traffic) until every
 * requested symbol has a price.
 */
object CryptoFeeds {
    private val okx = TickerFeed(SourceGuard("OKX"), 15_000L) {
        parseOkx(Http.json("https://www.okx.com/api/v5/market/tickers?instType=SPOT"))
    }
    private val kucoin = TickerFeed(SourceGuard("KuCoin"), 15_000L) {
        parseKuCoin(Http.json("https://api.kucoin.com/api/v1/market/allTickers"))
    }
    private val bybit = TickerFeed(SourceGuard("Bybit"), 15_000L) {
        parseBybit(Http.json("https://api.bybit.com/v5/market/tickers?category=spot"))
    }
    private val gate = TickerFeed(SourceGuard("Gate.io"), 20_000L) {
        parseGate(Http.json("https://api.gateio.ws/api/v4/spot/tickers"))
    }
    private val paprika = TickerFeed(SourceGuard("CoinPaprika"), 30_000L) {
        parsePaprika(Http.json("https://api.coinpaprika.com/v1/tickers?quotes=USD&limit=300"))
    }

    private val tiers: List<List<TickerFeed>> = listOf(listOf(okx, kucoin), listOf(bybit, gate, paprika))
    private val rotation = AtomicInteger(0)
    private val candleGuard = SourceGuard("OKX candles", 250L)

    suspend fun fillMissing(have: MutableMap<String, SpotQuote>, symbols: Set<String>) {
        for (tier in tiers) {
            if (have.keys.containsAll(symbols)) return
            val offset = if (tier.size > 1) rotation.getAndIncrement() else 0
            for (i in tier.indices) {
                if (have.keys.containsAll(symbols)) return
                val feed = tier[(offset + i) % tier.size]
                try {
                    val quotes = feed.get()
                    for (symbol in symbols) {
                        if (symbol !in have) quotes[symbol]?.let { have[symbol] = it }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // This source is unavailable right now: move on to the next one.
                }
            }
        }
    }

    suspend fun okxCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val bar = when (interval) {
            "1m" -> "1m"
            "5m" -> "5m"
            "30m" -> "30m"
            "1h" -> "1H"
            "1d" -> "1D"
            "1w" -> "1W"
            else -> return emptyList()
        }
        val url = Http.url(
            "https://www.okx.com/api/v5/market/candles",
            "instId" to "${symbol.uppercase()}-USDT",
            "bar" to bar,
            "limit" to minOf(limit, 300).toString()
        )
        val data = candleGuard.call { Http.json(url) }.asJsonObject.getAsJsonArray("data") ?: return emptyList()
        val out = ArrayList<Candle>(data.size())
        for (row in data) {
            val a = row.asJsonArray
            out.add(
                Candle(
                    timeMillis = a[0].asString.toLong(),
                    open = a[1].asString.toDouble(),
                    high = a[2].asString.toDouble(),
                    low = a[3].asString.toDouble(),
                    close = a[4].asString.toDouble(),
                    volume = a[5].asString.toDouble()
                )
            )
        }
        out.sortBy { it.timeMillis }
        return out
    }

    private fun parseOkx(root: JsonElement): Map<String, SpotQuote> {
        val out = HashMap<String, SpotQuote>()
        val data = root.asJsonObject.getAsJsonArray("data") ?: return out
        for (e in data) {
            val o = e.asJsonObject
            val id = o.str("instId")
            if (!id.endsWith("-USDT")) continue
            val last = o.dbl("last") ?: continue
            if (last <= 0.0) continue
            val open = o.dbl("open24h") ?: 0.0
            val base = id.removeSuffix("-USDT")
            out[base] = SpotQuote(
                base, last, if (open > 0.0) (last - open) / open * 100.0 else 0.0,
                o.dbl("high24h") ?: last, o.dbl("low24h") ?: last, o.dbl("volCcy24h") ?: 0.0
            )
        }
        return out
    }

    private fun parseKuCoin(root: JsonElement): Map<String, SpotQuote> {
        val out = HashMap<String, SpotQuote>()
        val list = root.asJsonObject.getAsJsonObject("data")?.getAsJsonArray("ticker") ?: return out
        for (e in list) {
            val o = e.asJsonObject
            val symbol = o.str("symbol")
            if (!symbol.endsWith("-USDT")) continue
            val last = o.dbl("last") ?: continue
            if (last <= 0.0) continue
            val base = symbol.removeSuffix("-USDT")
            out[base] = SpotQuote(
                base, last, (o.dbl("changeRate") ?: 0.0) * 100.0,
                o.dbl("high") ?: last, o.dbl("low") ?: last, o.dbl("volValue") ?: 0.0
            )
        }
        return out
    }

    private fun parseBybit(root: JsonElement): Map<String, SpotQuote> {
        val out = HashMap<String, SpotQuote>()
        val list = root.asJsonObject.getAsJsonObject("result")?.getAsJsonArray("list") ?: return out
        for (e in list) {
            val o = e.asJsonObject
            val symbol = o.str("symbol")
            if (!symbol.endsWith("USDT")) continue
            val last = o.dbl("lastPrice") ?: continue
            if (last <= 0.0) continue
            val base = symbol.removeSuffix("USDT")
            out[base] = SpotQuote(
                base, last, (o.dbl("price24hPcnt") ?: 0.0) * 100.0,
                o.dbl("highPrice24h") ?: last, o.dbl("lowPrice24h") ?: last, o.dbl("turnover24h") ?: 0.0
            )
        }
        return out
    }

    private fun parseGate(root: JsonElement): Map<String, SpotQuote> {
        val out = HashMap<String, SpotQuote>()
        for (e in root.asJsonArray) {
            val o = e.asJsonObject
            val pair = o.str("currency_pair")
            if (!pair.endsWith("_USDT")) continue
            val last = o.dbl("last") ?: continue
            if (last <= 0.0) continue
            val base = pair.removeSuffix("_USDT")
            out[base] = SpotQuote(
                base, last, o.dbl("change_percentage") ?: 0.0,
                o.dbl("high_24h") ?: last, o.dbl("low_24h") ?: last, o.dbl("quote_volume") ?: 0.0
            )
        }
        return out
    }

    private fun parsePaprika(root: JsonElement): Map<String, SpotQuote> {
        val out = HashMap<String, SpotQuote>()
        for (e in root.asJsonArray) {
            val o = e.asJsonObject
            val symbol = o.str("symbol").uppercase()
            val usd = o.getAsJsonObject("quotes")?.getAsJsonObject("USD") ?: continue
            val price = usd.dbl("price") ?: continue
            if (price <= 0.0 || symbol.isEmpty() || symbol in out) continue
            out[symbol] = SpotQuote(symbol, price, usd.dbl("percent_change_24h") ?: 0.0, price, price, usd.dbl("volume_24h") ?: 0.0)
        }
        return out
    }
}

// ======================= Stocks =======================

data class StockQuote(
    val symbol: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val yearHigh: Double,
    val yearLow: Double
)

/** Keyless stock sources: CNBC (batch quotes + charts) and the Nasdaq website API. */
object StockFeeds {
    private val cnbc = SourceGuard("CNBC", 250L)
    private val nasdaq = SourceGuard("Nasdaq", 500L)

    private fun String.num(): Double? =
        replace(",", "").replace("%", "").replace("+", "").replace("\$", "").trim().toDoubleOrNull()

    /** All symbols in ONE request. Unknown symbols are simply missing from the result. */
    suspend fun cnbcQuotes(symbols: List<String>): Map<String, StockQuote> {
        if (symbols.isEmpty()) return emptyMap()
        val url = Http.url(
            "https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol",
            "symbols" to symbols.joinToString("|"),
            "requestMethod" to "itv", "noform" to "1", "partnerId" to "2",
            "fund" to "1", "exthrs" to "1", "output" to "json", "events" to "1"
        )
        val root = cnbc.call { Http.json(url) }.asJsonObject.getAsJsonObject("FormattedQuoteResult")
            ?: return emptyMap()
        val node = root.get("FormattedQuote") ?: return emptyMap()
        val items: List<JsonElement> = if (node.isJsonArray) node.asJsonArray.toList() else listOf(node)
        val out = HashMap<String, StockQuote>()
        for (el in items) {
            val o = el.asJsonObject
            val symbol = o.str("symbol").uppercase()
            val code = o.dbl("code") ?: 0.0
            val last = o.str("last").num() ?: continue
            if (symbol.isEmpty() || code != 0.0 || last <= 0.0) continue
            val high = o.str("high").num() ?: last
            val low = o.str("low").num() ?: last
            out[symbol] = StockQuote(
                symbol = symbol,
                name = o.str("name").ifEmpty { symbol },
                price = last,
                changePct = o.str("change_pct").num() ?: 0.0,
                high = high,
                low = low,
                volume = o.str("volume").num() ?: 0.0,
                yearHigh = o.str("yrhiprice").num() ?: high,
                yearLow = o.str("yrloprice").num() ?: low
            )
        }
        return out
    }

    suspend fun nasdaqQuote(symbol: String): StockQuote? {
        val sym = symbol.uppercase()
        val root = nasdaq.call { Http.json("https://api.nasdaq.com/api/quote/$sym/info?assetclass=stocks") }.asJsonObject
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val primary = data.get("primaryData")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val price = primary.str("lastSalePrice").num() ?: return null
        return StockQuote(
            symbol = sym,
            name = data.str("companyName").removeSuffix(" Common Stock").ifEmpty { sym },
            price = price,
            changePct = primary.str("percentageChange").num() ?: 0.0,
            high = price, low = price,
            volume = primary.str("volume").num() ?: 0.0,
            yearHigh = price, yearLow = price
        )
    }

    suspend fun nasdaqSearch(query: String): List<String> {
        val url = Http.url("https://api.nasdaq.com/api/autocomplete/slookup/10", "search" to query)
        val data = nasdaq.call { Http.json(url) }.asJsonObject.getAsJsonArray("data") ?: return emptyList()
        return data.mapNotNull { e ->
            val o = e.asJsonObject
            val asset = o.str("asset").uppercase()
            if (asset == "STOCKS" || asset == "ETF") o.str("symbol").takeIf { it.isNotEmpty() } else null
        }
    }

    /** [range] is one of CNBC's chart ranges: 1D, 5D, 1Y, ALL. */
    suspend fun cnbcCandles(symbol: String, range: String): List<Candle> {
        val url = Http.url("https://ts-api.cnbc.com/harmony/app/charts/$range.json", "symbol" to symbol.uppercase())
        val bars = cnbc.call { Http.json(url) }.asJsonObject.getAsJsonObject("barData")
            ?.getAsJsonArray("priceBars") ?: return emptyList()
        return bars.mapNotNull { b ->
            val o = b.asJsonObject
            val time = o.dbl("tradeTimeinMills")?.toLong() ?: return@mapNotNull null
            val open = o.dbl("open") ?: return@mapNotNull null
            val high = o.dbl("high") ?: return@mapNotNull null
            val low = o.dbl("low") ?: return@mapNotNull null
            val close = o.dbl("close") ?: return@mapNotNull null
            Candle(time, open, high, low, close, o.dbl("volume") ?: 0.0)
        }
    }
}
