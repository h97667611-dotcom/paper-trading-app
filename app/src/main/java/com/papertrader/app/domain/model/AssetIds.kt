package com.papertrader.app.domain.model

/**
 * Crypto, stocks and prediction-market outcomes share one id space so positions, orders and
 * navigation work unchanged: crypto keeps its CoinGecko id, stocks get "stock_" and
 * Polymarket outcome shares get "pm_" + the outcome's token id.
 */
object AssetIds {
    private const val STOCK_PREFIX = "stock_"
    private const val BET_PREFIX = "pm_"

    fun stock(symbol: String): String = STOCK_PREFIX + symbol.uppercase()
    fun isStock(id: String): Boolean = id.startsWith(STOCK_PREFIX)
    fun stockSymbol(id: String): String = id.removePrefix(STOCK_PREFIX)

    fun bet(tokenId: String): String = BET_PREFIX + tokenId
    fun isBet(id: String): Boolean = id.startsWith(BET_PREFIX)
    fun betToken(id: String): String = id.removePrefix(BET_PREFIX)
}
