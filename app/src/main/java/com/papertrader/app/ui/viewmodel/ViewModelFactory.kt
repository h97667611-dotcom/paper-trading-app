package com.papertrader.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.papertrader.app.di.AppContainer
import com.papertrader.app.ui.screens.coindetail.CoinDetailViewModel
import com.papertrader.app.ui.screens.dashboard.DashboardViewModel
import com.papertrader.app.ui.screens.funds.FundsViewModel
import com.papertrader.app.ui.screens.history.HistoryViewModel
import com.papertrader.app.ui.screens.markets.MarketsViewModel
import com.papertrader.app.ui.screens.order.OrderViewModel
import com.papertrader.app.ui.screens.positions.PositionsViewModel
import com.papertrader.app.ui.screens.profile.ProfileViewModel

/** Single factory for every screen's ViewModel, backed by the app-wide [AppContainer]. */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when (modelClass) {
            DashboardViewModel::class.java -> DashboardViewModel(container.paperTradingRepository, container.marketRepository)
            MarketsViewModel::class.java -> MarketsViewModel(container.marketRepository)
            CoinDetailViewModel::class.java -> CoinDetailViewModel(container.marketRepository)
            OrderViewModel::class.java -> OrderViewModel(container.paperTradingRepository, container.marketRepository)
            PositionsViewModel::class.java -> PositionsViewModel(container.paperTradingRepository, container.marketRepository)
            HistoryViewModel::class.java -> HistoryViewModel(container.paperTradingRepository)
            FundsViewModel::class.java -> FundsViewModel(container.paperTradingRepository)
            ProfileViewModel::class.java -> ProfileViewModel(container.paperTradingRepository)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return vm as T
    }
}
