package com.papertrader.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.model.Position
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ChartPeriod(val label: String, val days: String) {
    ONE_DAY("1D", "1"),
    ONE_WEEK("1W", "7"),
    ONE_MONTH("1M", "30"),
    THREE_MONTHS("3M", "90"),
    ONE_YEAR("1Y", "365"),
    ALL("ALL", "max")
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val cashBalance: Double = 0.0,
    val investedValue: Double = 0.0,
    val totalValue: Double = 0.0,
    val todayPnl: Double = 0.0,
    val todayPnlPercent: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val chartValues: List<Float> = emptyList(),
    val selectedPeriod: ChartPeriod = ChartPeriod.ONE_DAY,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val tradingRepository: PaperTradingRepository,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var startingCapital = 10_000.0

    init {
        observePortfolio()
    }

    private fun observePortfolio() {
        viewModelScope.launch {
            combine(
                tradingRepository.observeCashBalance(),
                tradingRepository.observePositions()
            ) { cash, positions -> cash to positions }
                .collect { (cash, positions) ->
                    refreshPricesAndCompute(cash, positions)
                }
        }
    }

    private suspend fun refreshPricesAndCompute(cash: Double, positions: List<Position>) {
        if (_uiState.value.isLoading) {
            // First paint right away from local data; live prices refine it below.
            val quickInvested = positions.sumOf { it.avgEntryPrice * it.quantity }
            val quickTotal = cash + quickInvested
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cashBalance = cash,
                investedValue = quickInvested,
                totalValue = quickTotal,
                totalPnl = quickTotal - startingCapital,
                totalPnlPercent = if (startingCapital > 0) (quickTotal - startingCapital) / startingCapital * 100.0 else 0.0
            )
        }
        val ids = positions.map { it.coinId }
        val priceById: Map<String, Double> = if (ids.isEmpty()) {
            emptyMap()
        } else {
            when (val result = marketRepository.getCoinsByIds(ids)) {
                is NetworkResult.Success -> result.data.associate { it.id to it.currentPrice }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                    emptyMap()
                }
            }
        }

        val invested = positions.sumOf { pos -> (priceById[pos.coinId] ?: pos.avgEntryPrice) * pos.quantity }
        val costBasis = positions.sumOf { it.avgEntryPrice * it.quantity }
        val unrealizedPnl = invested - costBasis
        val total = cash + invested
        val totalPnl = total - startingCapital

        tradingRepository.recordPortfolioSnapshot(total)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            cashBalance = cash,
            investedValue = invested,
            totalValue = total,
            todayPnl = unrealizedPnl,
            todayPnlPercent = if (costBasis > 0) unrealizedPnl / costBasis * 100.0 else 0.0,
            totalPnl = totalPnl,
            totalPnlPercent = if (startingCapital > 0) totalPnl / startingCapital * 100.0 else 0.0,
            errorMessage = null
        )
        loadChart(_uiState.value.selectedPeriod)
    }

    fun selectPeriod(period: ChartPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        viewModelScope.launch { loadChart(period) }
    }

    private suspend fun loadChart(period: ChartPeriod) {
        // Portfolio-level history chart: falls back to a flat line seeded by
        // the current total value if no snapshot history exists yet (e.g.
        // right after account creation or a reset).
        val since = System.currentTimeMillis() - periodToMillis(period)
        // One-shot read (not a live collect) so this doesn't block the
        // portfolio/positions combine flow that calls into this function.
        val snapshots = tradingRepository.observeSnapshotsSince(since).first()
        val values = if (snapshots.isEmpty()) {
            listOf(_uiState.value.totalValue.toFloat(), _uiState.value.totalValue.toFloat())
        } else {
            snapshots.map { it.totalValue.toFloat() }
        }
        _uiState.value = _uiState.value.copy(chartValues = values)
    }

    private fun periodToMillis(period: ChartPeriod): Long = when (period) {
        ChartPeriod.ONE_DAY -> 24L * 60 * 60 * 1000
        ChartPeriod.ONE_WEEK -> 7L * 24 * 60 * 60 * 1000
        ChartPeriod.ONE_MONTH -> 30L * 24 * 60 * 60 * 1000
        ChartPeriod.THREE_MONTHS -> 90L * 24 * 60 * 60 * 1000
        ChartPeriod.ONE_YEAR -> 365L * 24 * 60 * 60 * 1000
        ChartPeriod.ALL -> Long.MAX_VALUE / 2
    }
}
