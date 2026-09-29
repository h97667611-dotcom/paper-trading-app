package com.papertrader.app.data.remote.binance

import com.google.gson.JsonArray
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Binance public market-data endpoint (no key). Used for real OHLC candles of
 * crypto. Each kline row is [openTime, open, high, low, close, volume, ...].
 */
interface BinanceApi {

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): JsonArray

    companion object {
        const val BASE_URL = "https://data-api.binance.vision/"
    }
}
