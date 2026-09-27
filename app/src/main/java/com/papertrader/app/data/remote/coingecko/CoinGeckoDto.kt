package com.papertrader.app.data.remote.coingecko

import com.google.gson.annotations.SerializedName

/**
 * DTOs for CoinGecko's public "/api/v3" tier
 * (https://www.coingecko.com/en/api/documentation). No API key is required
 * for these endpoints at the free public rate limit.
 */

data class CoinMarketDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String?,
    @SerializedName("current_price") val currentPrice: Double?,
    @SerializedName("market_cap") val marketCap: Double?,
    @SerializedName("total_volume") val totalVolume: Double?,
    @SerializedName("high_24h") val high24h: Double?,
    @SerializedName("low_24h") val low24h: Double?,
    @SerializedName("price_change_percentage_24h") val priceChangePercentage24h: Double?,
    @SerializedName("ath") val ath: Double?,
    @SerializedName("atl") val atl: Double?
)

data class CoinSearchResponseDto(
    val coins: List<CoinSearchResultDto>
)

data class CoinSearchResultDto(
    val id: String,
    val name: String,
    val symbol: String,
    @SerializedName("large") val imageUrl: String?
)

/** Response shape for /coins/{id}/market_chart */
data class MarketChartResponseDto(
    val prices: List<List<Double>> // [ [timestampMillis, price], ... ]
)
