package com.papertrader.app.data.remote.yahoo

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Yahoo Finance public chart + search endpoints. No API key needed and they
 * return live quotes, OHLC history and symbol search in one place.
 * Unofficial API: the shape can change without notice.
 */
interface YahooFinanceApi {

    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String,
        @Query("includePrePost") includePrePost: Boolean = false
    ): YahooChartResponse

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 12,
        @Query("newsCount") newsCount: Int = 0
    ): YahooSearchResponse

    companion object {
        const val BASE_URL = "https://query1.finance.yahoo.com/"
    }
}
