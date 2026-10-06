package com.papertrader.app.ui.screens.casino

import androidx.lifecycle.ViewModel
import com.papertrader.app.data.casino.CasinoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CasinoUiState(val chips: Long = CasinoStore.STARTING_CHIPS)

/** Holds the play-chip balance shared by the built-in games and the demo slots. */
class CasinoViewModel(private val store: CasinoStore) : ViewModel() {

    private val _state = MutableStateFlow(CasinoUiState(chips = store.chips))
    val uiState: StateFlow<CasinoUiState> = _state.asStateFlow()

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
