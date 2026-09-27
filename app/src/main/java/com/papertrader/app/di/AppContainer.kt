package com.papertrader.app.di

import android.content.Context
import com.papertrader.app.data.local.AppDatabase
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.PaperTradingEngine

/**
 * Minimal, dependency-free service locator. Deliberately avoids pulling in
 * Hilt/Dagger so the project stays easy to open and build with just the
 * Android Gradle Plugin — swap this for Hilt later if the project grows.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    val paperTradingEngine: PaperTradingEngine by lazy { PaperTradingEngine() }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            coinGeckoApi = NetworkModule.provideCoinGeckoApi(),
            dexScreenerApi = NetworkModule.provideDexScreenerApi()
        )
    }

    val paperTradingRepository: PaperTradingRepository by lazy {
        PaperTradingRepository(
            accountDao = database.accountDao(),
            positionDao = database.positionDao(),
            orderDao = database.orderDao(),
            tradeDao = database.tradeDao(),
            fundsHistoryDao = database.fundsHistoryDao(),
            portfolioSnapshotDao = database.portfolioSnapshotDao(),
            engine = paperTradingEngine
        )
    }
}
