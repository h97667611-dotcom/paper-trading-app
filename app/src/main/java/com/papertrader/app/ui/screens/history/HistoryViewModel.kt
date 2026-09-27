package com.papertrader.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.domain.model.Trade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TradeFilter(val label: String) {
    ALL("All"),
    BUYS("Buys"),
    SELLS("Sells"),
    PROFITABLE("Profitable"),
    LOSSES("Losses")
}

data class HistoryUiState(
    val allTrades: List<Trade> = emptyList(),
    val filter: TradeFilter = TradeFilter.ALL
) {
    val filteredTrades: List<Trade> get() = when (filter) {
        TradeFilter.ALL -> allTrades
        TradeFilter.BUYS -> allTrades.filter { it.side == OrderSide.BUY }
        TradeFilter.SELLS -> allTrades.filter { it.side == OrderSide.SELL }
        TradeFilter.PROFITABLE -> allTrades.filter { (it.realizedPnl ?: 0.0) > 0 }
        TradeFilter.LOSSES -> allTrades.filter { (it.realizedPnl ?: 0.0) < 0 }
    }
}

class HistoryViewModel(private val tradingRepository: PaperTradingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tradingRepository.observeTrades().collect { trades ->
                _uiState.value = _uiState.value.copy(allTrades = trades)
            }
        }
    }

    fun setFilter(filter: TradeFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }
}
