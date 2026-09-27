package com.papertrader.app

import android.app.Application
import com.papertrader.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaperTraderApplication : Application() {

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
    }
}
