package com.papertrader.app.util

/**
 * Wraps the outcome of any network-backed repository call so UI layers can
 * render loading / error / stale-cache states without catching exceptions
 * themselves.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T, val isFromCache: Boolean = false) : NetworkResult<T>()
    data class Error(val message: String, val isRateLimited: Boolean = false, val cachedData: Any? = null) :
        NetworkResult<Nothing>()
}

/** Human-readable mapping of common failure modes for this app. */
object ErrorMessages {
    const val NO_INTERNET = "No internet connection. Showing the last data we loaded."
    const val API_UNREACHABLE = "Market data service is unreachable right now."
    const val RATE_LIMITED = "Too many requests right now — please wait a moment and try again."
    const val INVALID_COIN = "That asset couldn't be found."
    const val NO_MARKET_DATA = "Market data isn't available for this asset yet."
    const val EMPTY_PORTFOLIO = "You don't have any open positions yet."
    const val GENERIC = "Something went wrong. Please try again."
}
