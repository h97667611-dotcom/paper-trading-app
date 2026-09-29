package com.papertrader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.papertrader.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaperTraderApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // First launch: create the demo paper account with 10,000 USDT and
        // no open positions, per the product spec.
        applicationScope.launch {
            container.paperTradingRepository.ensureAccountInitialized()
        }

        // Warm the market cache in the background so the Markets tab and coin
        // pages open instantly instead of waiting on the network.
        applicationScope.launch {
            container.marketRepository.getTopCoins(perPage = 100)
        }
        applicationScope.launch {
            container.marketRepository.getPopularStocks()
        }
    }

    /** App-wide image loader: memory + disk cache and a short crossfade, so coin logos don't flicker. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(150)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
}
