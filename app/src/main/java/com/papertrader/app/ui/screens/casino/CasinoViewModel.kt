package com.papertrader.app.ui.screens.casino

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.casino.CasinoGame
import com.papertrader.app.data.casino.CasinoStore
import com.papertrader.app.data.casino.ScriptCasinoClient
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
    @Volatile var gameUuid: String = ""
    @Volatile var baseUrl: String = ""
    @Volatile var merchantId: String = ""
    @Volatile var secret: String = ""
}

data class CasinoUiState(
    val chips: Long = CasinoStore.STARTING_CHIPS,
    val baseUrl: String = "",
    val merchantId: String = "",
    val hasSecret: Boolean = false,
    val games: List<CasinoGame> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val category: String? = null,
    val search: String = "",
    val visibleCount: Int = 40,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val connected: Boolean get() = baseUrl.isNotBlank() && merchantId.isNotBlank() && hasSecret
}

class CasinoViewModel(private val store: CasinoStore) : ViewModel() {

    private val _state = MutableStateFlow(
        CasinoUiState(
            chips = store.chips,
            baseUrl = store.casinoBaseUrl,
            merchantId = store.casinoMerchantId,
            hasSecret = store.casinoSecret.isNotBlank()
        )
    )
    val uiState: StateFlow<CasinoUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (_state.value.connected) loadPage(reset = true)
    }

    fun saveCredentials(baseUrl: String, merchantId: String, secret: String) {
        store.casinoBaseUrl = baseUrl.trim()
        store.casinoMerchantId = merchantId.trim()
        store.casinoSecret = secret.trim()
        _state.update {
            it.copy(
                baseUrl = baseUrl.trim(), merchantId = merchantId.trim(), hasSecret = secret.isNotBlank(),
                games = emptyList(), page = 0, hasMore = false, category = null, search = "", visibleCount = 40, error = null
            )
        }
        loadPage(reset = true)
    }

    fun clearCredentials() {
        store.casinoBaseUrl = ""
        store.casinoMerchantId = ""
        store.casinoSecret = ""
        _state.update {
            it.copy(
                baseUrl = "", merchantId = "", hasSecret = false, games = emptyList(),
                page = 0, hasMore = false, category = null, search = "", error = null
            )
        }
    }

    fun reload() = loadPage(reset = true)

    fun selectCategory(category: String?) {
        _state.update { it.copy(category = category, visibleCount = 40) }
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text, visibleCount = 40) }
    }

    fun showMore() {
        _state.update { it.copy(visibleCount = it.visibleCount + 40) }
    }

    /** Loads the next catalog page from the API. */
    fun loadMore() {
        val s = _state.value
        if (!s.isLoading && s.hasMore) loadPage(reset = false)
    }

    fun openGame(game: CasinoGame) {
        CasinoSession.title = game.name
        CasinoSession.gameUuid = game.uuid
        CasinoSession.baseUrl = store.casinoBaseUrl
        CasinoSession.merchantId = store.casinoMerchantId
        CasinoSession.secret = store.casinoSecret
    }

    private fun loadPage(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val next = if (reset) 1 else _state.value.page + 1
            try {
                val page = ScriptCasinoClient.games(store.casinoBaseUrl, store.casinoMerchantId, store.casinoSecret, next)
                val more = if (page.lastPage != null) page.page < page.lastPage else page.received >= ScriptCasinoClient.pageSize
                _state.update {
                    it.copy(
                        games = if (reset) page.games else (it.games + page.games).distinctBy { g -> g.uuid },
                        page = page.page,
                        hasMore = more && page.received > 0,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = ScriptCasinoClient.describeError(e)) }
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
