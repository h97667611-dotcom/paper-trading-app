package com.papertrader.app.ui.screens.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MarketsTab { COINS, STOCKS, DEX_PAIRS }

data class MarketsUiState(
    val isLoading: Boolean = true,
    val selectedTab: MarketsTab = MarketsTab.COINS,
    val allCoins: List<Coin> = emptyList(),
    val trending: List<Coin> = emptyList(),
    val topGainers: List<Coin> = emptyList(),
    val topLosers: List<Coin> = emptyList(),
    val stocks: List<Coin> = emptyList(),
    val isStocksLoading: Boolean = true,
    val stocksError: String? = null,
    val dexPairs: List<DexPair> = emptyList(),
    val isFromCache: Boolean = false,
    val errorMessage: String? = null
)

class MarketsViewModel(private val marketRepository: MarketRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    init {
        marketRepository.peekTopCoins()?.let { applyCoins(it, fromCache = true) }
        loadMarkets()
        loadStocks()
        loadDexPairs()
    }

    fun selectTab(tab: MarketsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        loadMarkets()
        loadStocks()
        loadDexPairs()
    }

    private fun applyCoins(coins: List<Coin>, fromCache: Boolean) {
        val sortedByChange = coins.sortedByDescending { it.priceChangePercent24h }
        _uiState.update {
            it.copy(
                isLoading = false,
                allCoins = coins,
                trending = sortedByChange.take(10),
                topGainers = sortedByChange.filter { c -> c.priceChangePercent24h > 0 }.take(10),
                topLosers = sortedByChange.filter { c -> c.priceChangePercent24h < 0 }.takeLast(10).reversed(),
                isFromCache = fromCache,
                errorMessage = null
            )
        }
    }

    private fun loadMarkets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.allCoins.isEmpty()) }
            when (val result = marketRepository.getTopCoins(perPage = 100)) {
                is NetworkResult.Success -> applyCoins(result.data, result.isFromCache)
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun loadStocks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStocksLoading = it.stocks.isEmpty(), stocksError = null) }
            when (val result = marketRepository.getPopularStocks()) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(isStocksLoading = false, stocks = result.data, stocksError = null)
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isStocksLoading = false, stocksError = result.message)
                }
            }
        }
    }

    private fun loadDexPairs() {
        viewModelScope.launch {
            when (val result = marketRepository.getTrendingDexPairs()) {
                is NetworkResult.Success -> _uiState.update { it.copy(dexPairs = result.data) }
                is NetworkResult.Error -> { /* Dex pairs are supplementary; keep the rest usable. */ }
            }
        }
    }
}
