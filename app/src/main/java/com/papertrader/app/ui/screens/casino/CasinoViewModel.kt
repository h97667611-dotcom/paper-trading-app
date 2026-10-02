package com.papertrader.app.ui.screens.casino

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.casino.CasinoStore
import com.papertrader.app.data.casino.SlotGame
import com.papertrader.app.data.casino.SlotProvider
import com.papertrader.app.data.casino.SlotsLaunchClient
import com.papertrader.app.data.remote.multi.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** The slot the player screen should open (set right before navigating). */
object CasinoSession {
    @Volatile var title: String = ""
    @Volatile var url: String = ""
    @Volatile var host: String = ""
}

data class CasinoUiState(
    val chips: Long = CasinoStore.STARTING_CHIPS,
    val token: String = "",
    val host: String = "",
    val providers: List<SlotProvider> = emptyList(),
    val selectedProvider: String? = null,
    val games: List<SlotGame> = emptyList(),
    val page: Int = 0,
    val lastPage: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CasinoViewModel(private val store: CasinoStore) : ViewModel() {

    private val _state = MutableStateFlow(
        CasinoUiState(chips = store.chips, token = store.slotsToken, host = store.slotsHost)
    )
    val uiState: StateFlow<CasinoUiState> = _state.asStateFlow()
    private var pageJob: Job? = null

    init {
        if (store.slotsToken.isNotBlank()) {
            loadProviders()
            loadPage(reset = true)
        }
    }

    fun saveCredentials(token: String, host: String) {
        store.slotsToken = token.trim()
        store.slotsHost = host.trim()
        _state.update {
            it.copy(
                token = token.trim(), host = host.trim(), providers = emptyList(), selectedProvider = null,
                games = emptyList(), page = 0, lastPage = 0, error = null
            )
        }
        loadProviders()
        loadPage(reset = true)
    }

    fun clearCredentials() {
        store.slotsToken = ""
        store.slotsHost = ""
        _state.update {
            it.copy(
                token = "", host = "", providers = emptyList(), selectedProvider = null,
                games = emptyList(), page = 0, lastPage = 0, error = null
            )
        }
    }

    fun selectProvider(id: String?) {
        _state.update { it.copy(selectedProvider = id, games = emptyList(), page = 0, lastPage = 0) }
        loadPage(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.isLoading && s.page < s.lastPage) loadPage(reset = false)
    }

    fun openGame(game: SlotGame) {
        CasinoSession.title = game.name
        CasinoSession.url = game.url
        CasinoSession.host = _state.value.host
    }

    private fun loadPage(reset: Boolean) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val s = _state.value
            val next = if (reset) 1 else s.page + 1
            try {
                val page = SlotsLaunchClient.games(s.token, s.host, next, s.selectedProvider)
                _state.update {
                    it.copy(
                        games = if (reset) page.games else it.games + page.games,
                        page = page.page, lastPage = page.lastPage, isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = friendly(e)) }
            }
        }
    }

    private fun loadProviders() {
        viewModelScope.launch {
            try {
                val list = SlotsLaunchClient.providers(_state.value.token, _state.value.host)
                _state.update { it.copy(providers = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The game list shows the error; providers are only a filter.
            }
        }
    }

    // ---- play chips for the built-in games ----

    fun refreshChips() {
        _state.update { it.copy(chips = store.chips) }
    }

    fun settle(bet: Long, payout: Long) {
        val next = (store.chips - bet + payout).coerceAtLeast(0L)
        store.chips = next
        _state.update { it.copy(chips = next) }
    }

    fun resetChips() {
        store.chips = CasinoStore.STARTING_CHIPS
        _state.update { it.copy(chips = CasinoStore.STARTING_CHIPS) }
    }

    private fun friendly(e: Exception): String = when {
        e is HttpStatusException && (e.code == 401 || e.code == 403) ->
            "SlotsLaunch rejected the token or origin host (HTTP ${e.code}). Check both."
        e is HttpStatusException && e.code == 429 -> "Rate limit reached. Try again in a minute."
        e is HttpStatusException -> "SlotsLaunch answered with HTTP ${e.code}."
        e is IOException -> "No connection."
        else -> "Could not read the SlotsLaunch response."
    }
}
