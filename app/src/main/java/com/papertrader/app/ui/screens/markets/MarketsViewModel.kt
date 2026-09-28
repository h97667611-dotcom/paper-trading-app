package com.papertrader.app.ui.screens.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.DexPair
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MarketsTab { COINS, DEX_PAIRS }

data class MarketsUiState(
    val isLoading: Boolean = true,
    val selectedTab: MarketsTab = MarketsTab.COINS,
    val searchQuery: String = "",
    val allCoins: List<Coin> = emptyList(),
    val trending: List<Coin> = emptyList(),
    val topGainers: List<Coin> = emptyList(),
    val topLosers: List<Coin> = emptyList(),
    val searchResults: List<Coin> = emptyList(),
    val dexPairs: List<DexPair> = emptyList(),
    val isFromCache: Boolean = false,
    val errorMessage: String? = null
)

class MarketsViewModel(private val marketRepository: MarketRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        marketRepository.peekTopCoins()?.let { applyCoins(it, fromCache = true) }
        loadMarkets()
        loadDexPairs()
    }

    fun selectTab(tab: MarketsTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun refresh() {
        loadMarkets()
        loadDexPairs()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // debounce
            when (val result = marketRepository.searchCoins(query)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(searchResults = result.data)
                is NetworkResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    private fun applyCoins(coins: List<Coin>, fromCache: Boolean) {
        val sortedByChange = coins.sortedByDescending { it.priceChangePercent24h }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            allCoins = coins,
            trending = sortedByChange.take(10),
            topGainers = sortedByChange.filter { it.priceChangePercent24h > 0 }.take(10),
            topLosers = sortedByChange.filter { it.priceChangePercent24h < 0 }.takeLast(10).reversed(),
            isFromCache = fromCache,
            errorMessage = null
        )
    }

    private fun loadMarkets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.allCoins.isEmpty())
            when (val result = marketRepository.getTopCoins(perPage = 100)) {
                is NetworkResult.Success -> applyCoins(result.data, result.isFromCache)
                is NetworkResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }
        }
    }

    private fun loadDexPairs() {
        viewModelScope.launch {
            when (val result = marketRepository.getTrendingDexPairs()) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(dexPairs = result.data)
                is NetworkResult.Error -> { /* Dex pairs are supplementary; keep coin list usable on failure. */ }
            }
        }
    }
}
