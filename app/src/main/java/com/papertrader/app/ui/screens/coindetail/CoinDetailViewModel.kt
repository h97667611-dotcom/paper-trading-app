package com.papertrader.app.ui.screens.coindetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CoinChartPeriod(val label: String, val days: String) {
    ONE_HOUR("1H", "1"),
    FOUR_HOURS("4H", "1"),
    ONE_DAY("1D", "1"),
    ONE_WEEK("1W", "7"),
    ONE_MONTH("1M", "30"),
    ONE_YEAR("1Y", "365")
}

data class CoinDetailUiState(
    val isLoading: Boolean = true,
    val coin: Coin? = null,
    val chartValues: List<Float> = emptyList(),
    val selectedPeriod: CoinChartPeriod = CoinChartPeriod.ONE_DAY,
    val errorMessage: String? = null
)

class CoinDetailViewModel(private val marketRepository: MarketRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CoinDetailUiState())
    val uiState: StateFlow<CoinDetailUiState> = _uiState.asStateFlow()

    private var currentCoinId: String? = null

    fun load(coinId: String) {
        if (currentCoinId == coinId && _uiState.value.coin != null) return
        currentCoinId = coinId
        marketRepository.peekCoin(coinId)?.let { cached ->
            _uiState.value = _uiState.value.copy(isLoading = false, coin = cached)
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.coin == null, errorMessage = null)
            when (val result = marketRepository.getCoinsByIds(listOf(coinId))) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, coin = result.data.firstOrNull() ?: _uiState.value.coin)
                    loadChart(coinId, _uiState.value.selectedPeriod)
                }
                is NetworkResult.Error -> _uiState.value =
                    _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun selectPeriod(period: CoinChartPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        currentCoinId?.let { id -> viewModelScope.launch { loadChart(id, period) } }
    }

    private suspend fun loadChart(coinId: String, period: CoinChartPeriod) {
        when (val result = marketRepository.getPriceHistory(coinId, period.days)) {
            is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                chartValues = result.data.map { it.price.toFloat() }
            )
            is NetworkResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
        }
    }
}
