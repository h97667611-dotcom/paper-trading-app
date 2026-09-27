package com.papertrader.app.data.remote.coingecko

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CoinGecko public market-data API. Base URL: https://api.coingecko.com/api/v3/
 *
 * Kept as a small, isolated interface behind [com.papertrader.app.data.repository.MarketRepository]
 * so it can be swapped for the paid Pro tier (different base URL + header
 * API key) without touching any other layer of the app.
 */
interface CoinGeckoApi {

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("price_change_percentage") priceChangePercentage: String = "24h",
        @Query("sparkline") sparkline: Boolean = false
    ): List<CoinMarketDto>

    @GET("coins/markets")
    suspend fun getMarketsByIds(
        @Query("ids") ids: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("sparkline") sparkline: Boolean = false
    ): List<CoinMarketDto>

    @GET("search")
    suspend fun search(@Query("query") query: String): CoinSearchResponseDto

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") coinId: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String = "1"
    ): MarketChartResponseDto

    companion object {
        const val BASE_URL = "https://api.coingecko.com/api/v3/"
    }
}
