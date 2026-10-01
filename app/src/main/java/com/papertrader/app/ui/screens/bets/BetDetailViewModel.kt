package com.papertrader.app.ui.screens.bets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.BetMarket
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.domain.model.Position
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BetDetailUiState(
    val isLoading: Boolean = true,
    val market: BetMarket? = null,
    val selected: Int = 0,
    val range: ChartRange = ChartRange.ONE_DAY,
    val candles: List<Candle> = emptyList(),
    val isChartLoading: Boolean = true,
    val chartError: String? = null,
    val positions: List<Position> = emptyList(),
    val errorMessage: String? = null,
    val message: String? = null
)

class BetDetailViewModel(
    private val marketRepository: MarketRepository,
    private val tradingRepository: PaperTradingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BetDetailUiState())
    val uiState: StateFlow<BetDetailUiState> = _uiState.asStateFlow()

    private var currentToken: String? = null
    private var chartJob: Job? = null
    private var liveJob: Job? = null
    private var positionJob: Job? = null

    fun load(tokenId: String) {
        if (currentToken == tokenId && _uiState.value.market != null) return
        currentToken = tokenId
        chartJob?.cancel()
        liveJob?.cancel()
        positionJob?.cancel()
        viewModelScope.launch {
            when (val result = marketRepository.getBetMarket(tokenId)) {
                is NetworkResult.Success -> {
                    val market = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            market = market,
                            selected = market.tokenIds.indexOf(tokenId).coerceAtLeast(0),
                            errorMessage = null
                        )
                    }
                    observePositions(market)
                    loadHistory()
                    startLive()
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun selectOutcome(index: Int) {
        if (index == _uiState.value.selected) return
        _uiState.update { it.copy(selected = index, candles = emptyList()) }
        loadHistory()
    }

    fun selectRange(range: ChartRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range, candles = emptyList()) }
        loadHistory()
    }

    private fun observePositions(market: BetMarket) {
        val ids = market.tokenIds.map { AssetIds.bet(it) }.toSet()
        positionJob = viewModelScope.launch {
            tradingRepository.observePositions().collect { list ->
                _uiState.update { it.copy(positions = list.filter { p -> p.coinId in ids }) }
            }
        }
    }

    private fun loadHistory() {
        val state = _uiState.value
        val market = state.market ?: return
        val token = market.tokenIds.getOrNull(state.selected) ?: return
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _uiState.update { it.copy(isChartLoading = true, chartError = null) }
            when (val r = marketRepository.getBetHistory(token, _uiState.value.range)) {
                is NetworkResult.Success -> _uiState.update { it.copy(candles = r.data, isChartLoading = false) }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(candles = emptyList(), isChartLoading = false, chartError = r.message)
                }
            }
        }
    }

    /** Refreshes the prices every few seconds while the screen is open. */
    private fun startLive() {
        liveJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                val token = currentToken ?: break
                try {
                    val result = marketRepository.getBetMarket(token, fresh = true)
                    if (result is NetworkResult.Success) {
                        _uiState.update { s ->
                            val price = result.data.prices.getOrNull(s.selected)
                            s.copy(market = result.data, candles = if (price != null) bumpLast(s.candles, price) else s.candles)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // keep the last prices
                }
            }
        }
    }

    fun closePosition(coinId: String) {
        val market = _uiState.value.market ?: return
        val index = market.tokenIds.indexOfFirst { AssetIds.bet(it) == coinId }
        if (index < 0) return
        // A lost bet is worth 0; the engine needs a positive price, so settle at a tiny value.
        val price = maxOf(market.prices[index], 0.0001)
        viewModelScope.launch {
            when (val result = tradingRepository.closePosition(coinId, price)) {
                is TradingResult.Success -> _uiState.update { it.copy(message = "Position closed.") }
                is TradingResult.Error -> _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun bumpLast(candles: List<Candle>, price: Double): List<Candle> {
        if (candles.isEmpty()) return candles
        val last = candles.last()
        if (last.close == price) return candles
        return candles.dropLast(1) + last.copy(close = price, high = maxOf(last.high, price), low = minOf(last.low, price))
    }
}
