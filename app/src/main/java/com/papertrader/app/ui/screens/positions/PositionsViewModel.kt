package com.papertrader.app.ui.screens.positions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.PaperTradingEngine
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.Position
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PositionUiModel(
    val position: Position,
    val currentPrice: Double,
    val positionValue: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPercent: Double
)

data class PositionsUiState(
    val isLoading: Boolean = true,
    val positions: List<PositionUiModel> = emptyList(),
    val errorMessage: String? = null,
    val closeSuccessMessage: String? = null
)

class PositionsViewModel(
    private val tradingRepository: PaperTradingRepository,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val engine = PaperTradingEngine()
    private val _uiState = MutableStateFlow(PositionsUiState())
    val uiState: StateFlow<PositionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tradingRepository.observePositions().collect { positions ->
                refreshPrices(positions)
            }
        }
    }

    private suspend fun refreshPrices(positions: List<Position>) {
        if (positions.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = false, positions = emptyList())
            return
        }
        when (val result = marketRepository.getCoinsByIds(positions.map { it.coinId })) {
            is NetworkResult.Success -> {
                val priceById = result.data.associate { it.id to it.currentPrice }
                val models = positions.map { position ->
                    val price = priceById[position.coinId] ?: position.avgEntryPrice
                    PositionUiModel(
                        position = position,
                        currentPrice = price,
                        positionValue = position.positionValue(price),
                        unrealizedPnl = engine.unrealizedPnl(position, price),
                        unrealizedPnlPercent = position.unrealizedPnlPercent(price)
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false, positions = models, errorMessage = null)
            }
            is NetworkResult.Error -> _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = result.message
            )
        }
    }

    fun closePosition(coinId: String) {
        val currentPrice = _uiState.value.positions.firstOrNull { it.position.coinId == coinId }?.currentPrice
            ?: return
        viewModelScope.launch {
            when (val result = tradingRepository.closePosition(coinId, currentPrice)) {
                is TradingResult.Success -> _uiState.value = _uiState.value.copy(
                    closeSuccessMessage = "Position closed. Realized P&L: ${result.value.realizedPnl ?: 0.0}"
                )
                is TradingResult.Error -> _uiState.value = _uiState.value.copy(
                    errorMessage = result.error.message
                )
            }
        }
    }

    fun consumeMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, closeSuccessMessage = null)
    }
}
