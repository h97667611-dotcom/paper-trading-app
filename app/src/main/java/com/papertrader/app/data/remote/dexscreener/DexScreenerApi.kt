package com.papertrader.app.data.remote.dexscreener

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * DexScreener public API. Base URL: https://api.dexscreener.com/
 * No API key is required for these public endpoints.
 */
interface DexScreenerApi {

    @GET("latest/dex/search?q={query}")
    suspend fun searchPairs(@Path("query") query: String): DexPairsResponseDto

    @GET("token-profiles/latest/v1")
    suspend fun getTrendingTokenProfiles(): List<TokenProfileDto>

    companion object {
        const val BASE_URL = "https://api.dexscreener.com/"
    }
}

data class DexPairsResponseDto(
    val pairs: List<DexPairDto>?
)

data class DexPairDto(
    val chainId: String,
    val dexId: String,
    val url: String,
    val pairAddress: String,
    val baseToken: DexTokenDto,
    val quoteToken: DexTokenDto,
    val priceUsd: String?,
    val priceChange: DexPriceChangeDto?,
    val liquidity: DexLiquidityDto?,
    val volume: DexVolumeDto?
)

data class DexTokenDto(
    val address: String,
    val name: String,
    val symbol: String
)

data class DexPriceChangeDto(
    val h24: Double?
)

data class DexLiquidityDto(
    val usd: Double?
)

data class DexVolumeDto(
    val h24: Double?
)

data class TokenProfileDto(
    val url: String?,
    val chainId: String?,
    val tokenAddress: String?,
    val description: String?
)
