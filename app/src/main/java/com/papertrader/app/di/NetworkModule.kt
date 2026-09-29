package com.papertrader.app.di

import com.papertrader.app.BuildConfig
import com.papertrader.app.data.remote.binance.BinanceApi
import com.papertrader.app.data.remote.coingecko.CoinGeckoApi
import com.papertrader.app.data.remote.dexscreener.DexScreenerApi
import com.papertrader.app.data.remote.yahoo.YahooFinanceApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit clients used by [com.papertrader.app.data.repository.MarketRepository]. */
object NetworkModule {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient by lazy { okHttpClient() }

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
                val original = chain.request()
                val host = original.url.host
                val builder = original.newBuilder()
                if (BuildConfig.COINGECKO_API_KEY.isNotBlank() && host.contains("coingecko")) {
                    builder.addHeader("x-cg-demo-api-key", BuildConfig.COINGECKO_API_KEY)
                }
                val isYahoo = host.contains("yahoo")
                if (isYahoo) {
                    // Yahoo rate-limits the default okhttp user agent, so present a browser one.
                    builder.header("User-Agent", BROWSER_USER_AGENT)
                    builder.header("Accept", "application/json")
                }
                var response = chain.proceed(builder.build())
                if (isYahoo && host == "query1.finance.yahoo.com" &&
                    (response.code == 429 || response.code >= 500)
                ) {
                    // Retry once on Yahoo's second host.
                    response.close()
                    val retry = builder
                        .url(original.url.newBuilder().host("query2.finance.yahoo.com").build())
                        .build()
                    response = chain.proceed(retry)
                }
                response
            }
            .build()
    }

    private fun <T> retrofit(baseUrl: String, service: Class<T>): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(service)

    fun provideCoinGeckoApi(): CoinGeckoApi = retrofit(CoinGeckoApi.BASE_URL, CoinGeckoApi::class.java)

    fun provideDexScreenerApi(): DexScreenerApi = retrofit(DexScreenerApi.BASE_URL, DexScreenerApi::class.java)

    fun provideBinanceApi(): BinanceApi = retrofit(BinanceApi.BASE_URL, BinanceApi::class.java)

    fun provideYahooApi(): YahooFinanceApi = retrofit(YahooFinanceApi.BASE_URL, YahooFinanceApi::class.java)
}
