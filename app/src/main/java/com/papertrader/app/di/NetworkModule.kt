package com.papertrader.app.di

import com.papertrader.app.BuildConfig
import com.papertrader.app.data.remote.coingecko.CoinGeckoApi
import com.papertrader.app.data.remote.dexscreener.DexScreenerApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit clients used by [com.papertrader.app.data.repository.MarketRepository]. */
object NetworkModule {

    private fun okHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                // CoinGecko/DexScreener API keys, if ever configured, are attached
                // here server-side of the request — never hardcoded in a URL or
                // committed to source. Both are currently unset because the
                // public tiers used by this app don't require a key.
                val request = chain.request().newBuilder()
                    .apply {
                        if (BuildConfig.COINGECKO_API_KEY.isNotBlank() &&
                            chain.request().url.host.contains("coingecko")
                        ) {
                            addHeader("x-cg-demo-api-key", BuildConfig.COINGECKO_API_KEY)
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun provideCoinGeckoApi(): CoinGeckoApi = Retrofit.Builder()
        .baseUrl(CoinGeckoApi.BASE_URL)
        .client(okHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CoinGeckoApi::class.java)

    fun provideDexScreenerApi(): DexScreenerApi = Retrofit.Builder()
        .baseUrl(DexScreenerApi.BASE_URL)
        .client(okHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DexScreenerApi::class.java)
}
