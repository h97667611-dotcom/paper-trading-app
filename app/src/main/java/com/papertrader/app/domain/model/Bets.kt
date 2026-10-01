package com.papertrader.app.domain.model

/** One Polymarket market: a question with two or more outcomes, each priced between 0 and 1. */
data class BetMarket(
    val id: String,
    val question: String,
    val description: String,
    val image: String?,
    val outcomes: List<String>,
    val prices: List<Double>,
    val tokenIds: List<String>,
    val volume: Double,
    val volume24h: Double,
    val liquidity: Double,
    val endDateMillis: Long?,
    val closed: Boolean,
    val acceptingOrders: Boolean,
    val oneDayChange: Double,
    val isSports: Boolean
)

enum class BetCategory(val label: String, val slug: String?) {
    TRENDING("Trending", null),
    POLITICS("Politics", "politics"),
    CRYPTO("Crypto", "crypto"),
    SPORTS("Sports", "sports")
}
