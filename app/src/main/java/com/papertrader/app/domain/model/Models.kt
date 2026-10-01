package com.papertrader.app.domain.model

/**
 * Core domain models. These are plain Kotlin data classes, independent of
 * Room/Retrofit, so the trading engine and view models can be unit tested
 * without any Android framework dependency.
 */

enum class OrderSide { BUY, SELL }

enum class OrderType { MARKET, LIMIT }

enum class OrderStatus { OPEN, FILLED, CANCELLED }

enum class FundsTransactionType { DEPOSIT, WITHDRAWAL }

data class Coin(
    val id: String,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val currentPrice: Double,
    val priceChangePercent24h: Double,
    val marketCap: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    val ath: Double,
    val atl: Double,
    val resolved: Boolean = false
)

data class PricePoint(val timestampMillis: Long, val price: Double)

/** One OHLC candle. For line-like data open = high = low = close. */
data class Candle(
    val timeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

/** Chart time ranges with the matching candle size for each data source. */
enum class ChartRange(
    val label: String,
    val intervalMillis: Long,
    val binanceInterval: String,
    val binanceLimit: Int,
    val yahooRange: String,
    val yahooInterval: String,
    val coinGeckoDays: String
) {
    ONE_HOUR("1H", 60_000L, "1m", 60, "1d", "1m", "1"),
    ONE_DAY("1D", 300_000L, "5m", 288, "1d", "5m", "1"),
    ONE_WEEK("1W", 1_800_000L, "30m", 336, "5d", "30m", "7"),
    ONE_MONTH("1M", 3_600_000L, "1h", 720, "1mo", "60m", "30"),
    THREE_MONTHS("3M", 86_400_000L, "1d", 90, "3mo", "1d", "90"),
    ONE_YEAR("1Y", 86_400_000L, "1d", 365, "1y", "1d", "365"),
    ALL("ALL", 604_800_000L, "1w", 1000, "max", "1wk", "max")
}

data class DexPair(
    val pairAddress: String,
    val chain: String,
    val dexName: String,
    val baseTokenSymbol: String,
    val quoteTokenSymbol: String,
    val priceUsd: Double,
    val priceChange24h: Double,
    val liquidityUsd: Double,
    val volume24h: Double,
    val url: String
)

data class Position(
    val coinId: String,
    val symbol: String,
    val quantity: Double,
    val avgEntryPrice: Double
) {
    fun positionValue(currentPrice: Double): Double = quantity * currentPrice

    fun unrealizedPnl(currentPrice: Double): Double =
        (currentPrice - avgEntryPrice) * quantity

    fun unrealizedPnlPercent(currentPrice: Double): Double =
        if (avgEntryPrice == 0.0) 0.0 else (currentPrice - avgEntryPrice) / avgEntryPrice * 100.0
}

data class Order(
    val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val limitPrice: Double?,
    val status: OrderStatus,
    val timestampMillis: Long
)

data class Trade(
    val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: Double,
    val price: Double,
    val totalValue: Double,
    val realizedPnl: Double?,
    val orderType: OrderType,
    val timestampMillis: Long
)

data class FundsTransaction(
    val id: Long = 0,
    val type: FundsTransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val timestampMillis: Long
)

/** Immutable snapshot of the paper-trading account used by the engine. */
data class PortfolioState(
    val cashBalance: Double,
    val positions: Map<String, Position>
) {
    fun investedValue(priceOf: (String) -> Double?): Double =
        positions.values.sumOf { pos -> (priceOf(pos.coinId) ?: pos.avgEntryPrice) * pos.quantity }

    fun totalValue(priceOf: (String) -> Double?): Double = cashBalance + investedValue(priceOf)
}
