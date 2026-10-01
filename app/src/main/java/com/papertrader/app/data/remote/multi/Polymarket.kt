package com.papertrader.app.data.remote.multi

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.papertrader.app.domain.model.BetMarket

/**
 * Polymarket public APIs (no key): Gamma for markets, tags and search, CLOB for price history.
 * Market prices are probabilities between 0 and 1, which is also the price of one share.
 */
object Polymarket {
    private const val GAMMA = "https://gamma-api.polymarket.com"
    private const val CLOB = "https://clob.polymarket.com"
    private val guard = SourceGuard("Polymarket", 120L)

    suspend fun markets(tagId: String?, limit: Int): List<BetMarket> {
        val params = arrayListOf(
            "active" to "true", "closed" to "false",
            "order" to "volume24hr", "ascending" to "false", "limit" to limit.toString()
        )
        if (tagId != null) params.add("tag_id" to tagId)
        val json = guard.call { Http.json(Http.url("$GAMMA/markets", *params.toTypedArray())) }
        return json.asJsonArray.mapNotNull { parseMarket(it.asJsonObject) }
    }

    suspend fun tagId(slug: String): String? {
        val o = guard.call { Http.json("$GAMMA/tags/slug/$slug") }.asJsonObject
        return o.get("id")?.takeIf { !it.isJsonNull }?.asString
    }

    suspend fun search(query: String): List<BetMarket> {
        val root = guard.call { Http.json(Http.url("$GAMMA/public-search", "q" to query, "limit_per_type" to "10")) }
        val events = root.asJsonObject.getAsJsonArray("events") ?: return emptyList()
        val out = ArrayList<BetMarket>()
        for (event in events) {
            val markets = event.asJsonObject.getAsJsonArray("markets") ?: continue
            for (m in markets) parseMarket(m.asJsonObject)?.let { out.add(it) }
        }
        return out
    }

    suspend fun byToken(tokenId: String): BetMarket? {
        for (closed in listOf<String?>(null, "true")) {
            val params = arrayListOf("clob_token_ids" to tokenId)
            if (closed != null) params.add("closed" to closed)
            val array = guard.call { Http.json(Http.url("$GAMMA/markets", *params.toTypedArray())) }.asJsonArray
            val market = array.firstOrNull()?.let { parseMarket(it.asJsonObject) }
            if (market != null) return market
        }
        return null
    }

    /** Price history of one outcome as (timeMillis, price) points. */
    suspend fun history(tokenId: String, interval: String, fidelity: Int): List<Pair<Long, Double>> {
        val url = Http.url(
            "$CLOB/prices-history",
            "market" to tokenId, "interval" to interval, "fidelity" to fidelity.toString()
        )
        val history = guard.call { Http.json(url) }.asJsonObject.getAsJsonArray("history") ?: return emptyList()
        return history.map { it.asJsonObject.get("t").asLong * 1000L to it.asJsonObject.get("p").asDouble }
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() } ?: ""

    private fun JsonObject.number(key: String): Double =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString.toDouble() }.getOrNull() } ?: 0.0

    /** Gamma sends outcomes, prices and token ids as JSON arrays inside a string. */
    private fun JsonObject.list(key: String): List<String> {
        val el = get(key) ?: return emptyList()
        return try {
            if (el.isJsonArray) el.asJsonArray.map { it.asString }
            else JsonParser.parseString(el.asString).asJsonArray.map { it.asString }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseMarket(o: JsonObject): BetMarket? {
        val outcomes = o.list("outcomes")
        val prices = o.list("outcomePrices").map { it.toDoubleOrNull() ?: 0.0 }
        val tokens = o.list("clobTokenIds")
        if (outcomes.isEmpty() || prices.size != outcomes.size || tokens.size != outcomes.size) return null
        val image = o.text("image").ifEmpty { o.text("icon") }.ifEmpty { null }
        return BetMarket(
            id = o.text("id").ifEmpty { tokens.first() },
            question = o.text("question"),
            description = o.text("description"),
            image = image,
            outcomes = outcomes,
            prices = prices,
            tokenIds = tokens,
            volume = o.number("volumeNum").takeIf { it > 0.0 } ?: o.number("volume"),
            volume24h = o.number("volume24hr"),
            liquidity = o.number("liquidityNum").takeIf { it > 0.0 } ?: o.number("liquidity"),
            endDateMillis = runCatching { java.time.Instant.parse(o.text("endDate")).toEpochMilli() }.getOrNull(),
            closed = o.get("closed")?.takeIf { !it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false,
            acceptingOrders = o.get("acceptingOrders")?.takeIf { !it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } ?: true,
            oneDayChange = o.number("oneDayPriceChange"),
            isSports = o.text("sportsMarketType").isNotEmpty()
        )
    }
}
