package com.papertrader.app.ui.screens.coindetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.AssetIds
import com.papertrader.app.domain.model.Candle
import com.papertrader.app.domain.model.ChartRange
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.Position
import com.papertrader.app.util.ErrorMessages
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

data class CoinDetailUiState(
    val isLoading: Boolean = true,
    val coin: Coin? = null,
    val candles: List<Candle> = emptyList(),
    val range: ChartRange = ChartRange.ONE_DAY,
    val isChartLoading: Boolean = true,
    val chartError: String? = null,
    val position: Position? = null,
    val errorMessage: String? = null,
    val closeMessage: String? = null
)

class CoinDetailViewModel(
    private val marketRepository: MarketRepository,
    private val tradingRepository: PaperTradingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoinDetailUiState())
    val uiState: StateFlow<CoinDetailUiState> = _uiState.asStateFlow()

    private var currentCoinId: String? = null
    private var liveJob: Job? = null
    private var positionJob: Job? = null
    private var chartJob: Job? = null

    fun load(coinId: String) {
        if (currentCoinId == coinId && _uiState.value.coin != null) return
        currentCoinId = coinId
        liveJob?.cancel()
        positionJob?.cancel()
        chartJob?.cancel()

        marketRepository.peekCoin(coinId)?.let { cached ->
            _uiState.update { it.copy(isLoading = false, coin = cached) }
        }

        positionJob = viewModelScope.launch {
            tradingRepository.observePositions().collect { list ->
                val pos = list.firstOrNull { it.coinId == coinId }
                _uiState.update { it.copy(position = pos) }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.coin == null, errorMessage = null) }
            when (val result = marketRepository.getCoinsByIds(listOf(coinId))) {
                is NetworkResult.Success -> {
                    val coin = result.data.firstOrNull() ?: _uiState.value.coin
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            coin = coin,
                            errorMessage = if (coin == null) ErrorMessages.INVALID_COIN else null
                        )
                    }
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
            if (_uiState.value.coin != null) {
                loadCandles()
                startLiveUpdates()
            }
        }
    }

    fun selectRange(range: ChartRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range, candles = emptyList()) }
        loadCandles()
    }

    private fun loadCandles() {
        val coin = _uiState.value.coin ?: return
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _uiState.update { it.copy(isChartLoading = true, chartError = null) }
            when (val r = marketRepository.getCandles(coin, _uiState.value.range)) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(candles = r.data, isChartLoading = false)
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(candles = emptyList(), isChartLoading = false, chartError = r.message)
                }
            }
        }
    }

    /** Crypto polls every few seconds, stocks a bit slower to stay within free rate limits. */
    private fun startLiveUpdates() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val coin = _uiState.value.coin ?: break
                delay(if (AssetIds.isStock(coin.id)) STOCK_POLL_MS else CRYPTO_POLL_MS)
                tick++
                try {
                    pollOnce(coin, tick)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep showing the last data; the next tick tries again.
                }
            }
        }
    }

    private suspend fun pollOnce(coin: Coin, tick: Int) {
        if (AssetIds.isStock(coin.id)) {
            val fresh = marketRepository.getLiveStockCoin(coin) ?: return
            _uiState.update { it.copy(coin = fresh, candles = bumpLast(it.candles, fresh.currentPrice)) }
            return
        }
        val tail = marketRepository.getLiveTail(coin, _uiState.value.range)
        if (tail != null) {
            val price = tail.last().close
            _uiState.update {
                it.copy(
                    coin = it.coin?.copy(currentPrice = price),
                    candles = mergeTail(it.candles, tail)
                )
            }
            if (tick % 8 == 0) refreshCoinStats(keepPrice = true)
        } else if (tick % 8 == 0) {
            refreshCoinStats(keepPrice = false)
        }
    }

    private suspend fun refreshCoinStats(keepPrice: Boolean) {
        val id = currentCoinId ?: return
        val result = marketRepository.getCoinsByIds(listOf(id))
        if (result is NetworkResult.Success) {
            result.data.firstOrNull()?.let { fresh ->
                _uiState.update { state ->
                    val price = if (keepPrice) state.coin?.currentPrice ?: fresh.currentPrice else fresh.currentPrice
                    state.copy(
                        coin = fresh.copy(currentPrice = price),
                        candles = if (keepPrice) state.candles else bumpLast(state.candles, price)
                    )
                }
            }
        }
    }

    fun closePosition() {
        val coin = _uiState.value.coin ?: return
        viewModelScope.launch {
            when (val result = tradingRepository.closePosition(coin.id, coin.currentPrice)) {
                is TradingResult.Success -> _uiState.update {
                    it.copy(
                        closeMessage = "Position closed. Realized P&L: ${"%.2f".format(result.value.realizedPnl ?: 0.0)}"
                    )
                }
                is TradingResult.Error -> _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun mergeTail(existing: List<Candle>, tail: List<Candle>): List<Candle> {
        if (existing.isEmpty()) return existing
        val out = existing.toMutableList()
        for (c in tail) {
            val idx = out.indexOfLast { it.timeMillis == c.timeMillis }
            if (idx >= 0) {
                out[idx] = c
            } else if (c.timeMillis > out.last().timeMillis) {
                out.add(c)
            }
        }
        return out
    }

    private fun bumpLast(candles: List<Candle>, price: Double): List<Candle> {
        if (candles.isEmpty() || price <= 0.0) return candles
        val last = candles.last()
        if (last.close == price) return candles
        val updated = last.copy(close = price, high = maxOf(last.high, price), low = minOf(last.low, price))
        return candles.dropLast(1) + updated
    }

    companion object {
        private const val CRYPTO_POLL_MS = 4_000L
        private const val STOCK_POLL_MS = 15_000L
    }
}
