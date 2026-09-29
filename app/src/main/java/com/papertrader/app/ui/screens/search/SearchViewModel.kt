package com.papertrader.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val crypto: List<Coin> = emptyList(),
    val stocks: List<Coin> = emptyList(),
    val errorMessage: String? = null
)

/** Searches crypto (CoinGecko) and stocks (Yahoo Finance) at the same time. */
class SearchViewModel(private val marketRepository: MarketRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        if (query == _uiState.value.query) return
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(crypto = emptyList(), stocks = emptyList(), isLoading = false, errorMessage = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce typing
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            coroutineScope {
                val cryptoDeferred = async { marketRepository.searchCoins(query) }
                val stocksDeferred = async { marketRepository.searchStocks(query) }
                val crypto = cryptoDeferred.await()
                val stocks = stocksDeferred.await()
                val bothFailed = crypto is NetworkResult.Error && stocks is NetworkResult.Error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        crypto = crypto.dataOrEmpty(),
                        stocks = stocks.dataOrEmpty(),
                        errorMessage = if (bothFailed && crypto is NetworkResult.Error) crypto.message else null
                    )
                }
            }
        }
    }

    private fun NetworkResult<List<Coin>>.dataOrEmpty(): List<Coin> =
        if (this is NetworkResult.Success) data else emptyList()
}
