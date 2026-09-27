package com.papertrader.app.domain.engine

/**
 * Result wrapper for every engine operation. Kept separate from Kotlin's
 * [Result] so we can carry a typed, user-presentable [EngineError] instead of
 * a raw Throwable.
 */
sealed class TradingResult<out T> {
    data class Success<T>(val value: T) : TradingResult<T>()
    data class Error(val error: EngineError) : TradingResult<Nothing>()
}

enum class EngineError(val message: String) {
    INSUFFICIENT_FUNDS("Not enough available cash to place this order."),
    INSUFFICIENT_POSITION("You don't hold enough of this asset to sell that quantity."),
    INVALID_QUANTITY("Quantity must be greater than zero."),
    INVALID_PRICE("Price must be greater than zero."),
    INVALID_LIMIT_PRICE("Limit price must be greater than zero for a limit order."),
    INVALID_AMOUNT("Amount must be greater than zero."),
    NO_POSITION("No open position for this asset."),
    MARKET_DATA_UNAVAILABLE("Current market price is unavailable. Try again shortly.")
}

inline fun <T> TradingResult<T>.onSuccess(block: (T) -> Unit): TradingResult<T> {
    if (this is TradingResult.Success) block(value)
    return this
}

inline fun <T> TradingResult<T>.onError(block: (EngineError) -> Unit): TradingResult<T> {
    if (this is TradingResult.Error) block(error)
    return this
}
