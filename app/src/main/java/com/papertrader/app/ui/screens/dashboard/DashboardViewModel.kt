package com.papertrader.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.Position
import com.papertrader.app.domain.model.PricePoint
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ChartPeriod(val label: String, val days: String) {
    ONE_DAY("1D", "1"),
    ONE_WEEK("1W", "7"),
    ONE_MONTH("1M", "30"),
    THREE_MONTHS("3M", "90"),
    ONE_YEAR("1Y", "365"),
    ALL("ALL", "max")
}

/** One open position with its live price, for the merged home screen. */
data class HoldingUi(
    val coinId: String,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val isStock: Boolean,
    val quantity: Double,
    val price: Double,
    val value: Double,
    val cost: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val dayPnl: Double,
    val dayChangePercent: Double
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val cashBalance: Double = 0.0,
    val investedValue: Double = 0.0,
    val totalValue: Double = 0.0,
    val todayPnl: Double = 0.0,
    val todayPnlPercent: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val chartPoints: List<PricePoint> = emptyList(),
    val selectedPeriod: ChartPeriod = ChartPeriod.ONE_DAY,
    val cryptoHoldings: List<HoldingUi> = emptyList(),
    val stockHoldings: List<HoldingUi> = emptyList(),
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val tradingRepository: PaperTradingRepository,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val startingCapital = 10_000.0
    private val refreshMutex = Mutex()
    private var latestCash = 0.0
    private var latestPositions: List<Position> = emptyList()
    private var hasData = false
    private var lastSnapshotAt = 0L

    init {
        viewModelScope.launch {
            combine(
                tradingRepository.observeCashBalance(),
                tradingRepository.observePositions()
            ) { cash, positions -> cash to positions }
                .collect { (cash, positions) ->
                    latestCash = cash
                    latestPositions = positions
                    hasData = true
                    refresh()
                }
        }
        // Keep live prices fresh while the home screen is open.
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                if (hasData) refresh()
            }
        }
    }

    fun selectPeriod(period: ChartPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch { loadChart(period) }
    }

    private suspend fun refresh() {
        refreshMutex.withLock {
            val cash = latestCash
            val positions = latestPositions

            if (_uiState.value.isLoading) {
                // First paint right away from local data; live prices refine it below.
                val cost = positions.sumOf { it.avgEntryPrice * it.quantity }
                val total = cash + cost
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cashBalance = cash,
                        investedValue = cost,
                        totalValue = total,
                        totalPnl = total - startingCapital,
                        totalPnlPercent = pctOf(total - startingCapital, startingCapital)
                    )
                }
            }

            var error: String? = null
            val coinsById: Map<String, Coin> = if (positions.isEmpty()) {
                emptyMap()
            } else {
                when (val result = marketRepository.getCoinsByIds(positions.map { it.coinId })) {
                    is NetworkResult.Success -> result.data.associateBy { it.id }
                    is NetworkResult.Error -> {
                        error = result.message
                        emptyMap()
                    }
                }
            }

            val holdings = positions
                .map { buildHolding(it, coinsById[it.coinId]) }
                .sortedByDescending { it.value }
            val invested = holdings.sumOf { it.value }
            val cost = holdings.sumOf { it.cost }
            val unrealized = invested - cost
            val dayPnl = holdings.sumOf { it.dayPnl }
            val total = cash + invested
            val totalPnl = total - startingCapital

            val now = System.currentTimeMillis()
            if (now - lastSnapshotAt > SNAPSHOT_INTERVAL_MS) {
                tradingRepository.recordPortfolioSnapshot(total)
                lastSnapshotAt = now
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    cashBalance = cash,
                    investedValue = invested,
                    totalValue = total,
                    todayPnl = dayPnl,
                    todayPnlPercent = pctOf(dayPnl, invested - dayPnl),
                    unrealizedPnl = unrealized,
                    totalPnl = totalPnl,
                    totalPnlPercent = pctOf(totalPnl, startingCapital),
                    cryptoHoldings = holdings.filter { h -> !h.isStock },
                    stockHoldings = holdings.filter { h -> h.isStock },
                    errorMessage = error
                )
            }
            loadChart(_uiState.value.selectedPeriod)
        }
    }

    private fun buildHolding(pos: Position, coin: Coin?): HoldingUi {
        val price = coin?.currentPrice?.takeIf { it > 0.0 } ?: pos.avgEntryPrice
        val value = pos.quantity * price
        val cost = pos.quantity * pos.avgEntryPrice
        val pnl = value - cost
        val factor = 1.0 + (coin?.priceChangePercent24h ?: 0.0) / 100.0
        val dayPnl = if (factor > 0.01) value - value / factor else 0.0
        return HoldingUi(
            coinId = pos.coinId,
            symbol = pos.symbol,
            name = coin?.name ?: pos.symbol,
            imageUrl = coin?.imageUrl,
            isStock = AssetIds.isStock(pos.coinId),
            quantity = pos.quantity,
            price = price,
            value = value,
            cost = cost,
            pnl = pnl,
            pnlPercent = pctOf(pnl, cost),
            dayPnl = dayPnl,
            dayChangePercent = coin?.priceChangePercent24h ?: 0.0
        )
    }

    private fun pctOf(pnl: Double, base: Double): Double = if (base > 0.0) pnl / base * 100.0 else 0.0

    private suspend fun loadChart(period: ChartPeriod) {
        val now = System.currentTimeMillis()
        val since = now - periodToMillis(period)
        // One-shot read so this never blocks the portfolio/positions combine flow.
        val snapshots = tradingRepository.observeSnapshotsSince(since).first()
        val total = _uiState.value.totalValue
        val points = when {
            snapshots.isEmpty() -> listOf(PricePoint(now - 60_000L, total), PricePoint(now, total))
            snapshots.size == 1 -> listOf(
                PricePoint(snapshots[0].timestampMillis, snapshots[0].totalValue),
                PricePoint(now, total)
            )
            else -> snapshots.map { PricePoint(it.timestampMillis, it.totalValue) }
        }
        _uiState.update { it.copy(chartPoints = points) }
    }

    private fun periodToMillis(period: ChartPeriod): Long = when (period) {
        ChartPeriod.ONE_DAY -> 24L * 60 * 60 * 1000
        ChartPeriod.ONE_WEEK -> 7L * 24 * 60 * 60 * 1000
        ChartPeriod.ONE_MONTH -> 30L * 24 * 60 * 60 * 1000
        ChartPeriod.THREE_MONTHS -> 90L * 24 * 60 * 60 * 1000
        ChartPeriod.ONE_YEAR -> 365L * 24 * 60 * 60 * 1000
        ChartPeriod.ALL -> Long.MAX_VALUE / 2
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
        private const val SNAPSHOT_INTERVAL_MS = 60_000L
    }
}
