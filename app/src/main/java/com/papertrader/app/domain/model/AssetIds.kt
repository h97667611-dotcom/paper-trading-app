package com.papertrader.app.domain.model

/**
 * Crypto and stocks share one id space so positions, orders and navigation work
 * unchanged for both: crypto keeps its CoinGecko id, stocks get a "stock_" prefix.
 */
object AssetIds {
    private const val STOCK_PREFIX = "stock_"

    fun stock(symbol: String): String = STOCK_PREFIX + symbol.uppercase()
    fun isStock(id: String): Boolean = id.startsWith(STOCK_PREFIX)
    fun stockSymbol(id: String): String = id.removePrefix(STOCK_PREFIX)
}
