package com.papertrader.app.data.remote.binance

import com.google.gson.JsonArray
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Binance public market-data endpoint (no key). Used for real OHLC candles of crypto and as the
 * live-price fallback when CoinGecko is unavailable.
 * Each kline row is [openTime, open, high, low, close, volume, ...].
 */
interface BinanceApi {

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): JsonArray

    /** [symbols] is a JSON array string such as ["BTCUSDT","ETHUSDT"]. */
    @GET("api/v3/ticker/24hr")
    suspend fun getTickers(@Query("symbols") symbols: String): List<BinanceTickerDto>

    @GET("api/v3/ticker/24hr")
    suspend fun getAllTickers(): List<BinanceTickerDto>

    companion object {
        const val BASE_URL = "https://data-api.binance.vision/"
    }
}

data class BinanceTickerDto(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val highPrice: String,
    val lowPrice: String,
    val quoteVolume: String
)
