package com.papertrader.app.ui.screens.casino

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.casino.CasinoStore
import com.papertrader.app.data.casino.Hub88Client
import com.papertrader.app.data.casino.Hub88Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The game the player screen should open (set right before navigating). */
object CasinoSession {
    @Volatile var title: String = ""
    @Volatile var gameCode: String = ""
    @Volatile var baseUrl: String = ""
    @Volatile var operatorId: Long = 0L
    @Volatile var privateKey: String = ""
}

data class CasinoUiState(
    val chips: Long = CasinoStore.STARTING_CHIPS,
    val baseUrl: String = "",
    val operatorId: String = "",
    val hasKey: Boolean = false,
    val games: List<Hub88Game> = emptyList(),
    val category: String? = null,
    val search: String = "",
    val visibleCount: Int = 40,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val connected: Boolean get() = baseUrl.isNotBlank() && operatorId.isNotBlank() && hasKey
}

class CasinoViewModel(private val store: CasinoStore) : ViewModel() {

    private val _state = MutableStateFlow(
        CasinoUiState(
            chips = store.chips,
            baseUrl = store.hubBaseUrl,
            operatorId = store.hubOperatorId,
            hasKey = store.hubPrivateKey.isNotBlank()
        )
    )
    val uiState: StateFlow<CasinoUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (_state.value.connected) loadGames()
    }

    fun saveCredentials(baseUrl: String, operatorId: String, privateKey: String) {
        store.hubBaseUrl = baseUrl.trim()
        store.hubOperatorId = operatorId.trim()
        store.hubPrivateKey = privateKey.trim()
        _state.update {
            it.copy(
                baseUrl = baseUrl.trim(), operatorId = operatorId.trim(), hasKey = privateKey.isNotBlank(),
                games = emptyList(), category = null, search = "", visibleCount = 40, error = null
            )
        }
        loadGames()
    }

    fun clearCredentials() {
        store.hubBaseUrl = ""
        store.hubOperatorId = ""
        store.hubPrivateKey = ""
        _state.update {
            it.copy(baseUrl = "", operatorId = "", hasKey = false, games = emptyList(), category = null, search = "", error = null)
        }
    }

    fun reload() = loadGames()

    fun selectCategory(category: String?) {
        _state.update { it.copy(category = category, visibleCount = 40) }
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text, visibleCount = 40) }
    }

    fun showMore() {
        _state.update { it.copy(visibleCount = it.visibleCount + 40) }
    }

    fun openGame(game: Hub88Game) {
        CasinoSession.title = game.name
        CasinoSession.gameCode = game.code
        CasinoSession.baseUrl = store.hubBaseUrl
        CasinoSession.operatorId = store.hubOperatorId.toLongOrNull() ?: 0L
        CasinoSession.privateKey = store.hubPrivateKey
    }

    private fun loadGames() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val operatorId = store.hubOperatorId.toLongOrNull()
            if (operatorId == null) {
                _state.update { it.copy(isLoading = false, error = "The operator ID must be a number.") }
                return@launch
            }
            try {
                val games = Hub88Client.demoGames(store.hubBaseUrl, operatorId, store.hubPrivateKey)
                _state.update { it.copy(games = games, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = Hub88Client.describeError(e)) }
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
}
