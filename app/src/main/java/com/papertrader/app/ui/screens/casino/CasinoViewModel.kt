package com.papertrader.app.ui.screens.casino

import androidx.lifecycle.ViewModel
import com.papertrader.app.data.casino.CasinoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CasinoUiState(
    val chips: Long = CasinoStore.STARTING_CHIPS,
    val startChips: Long = CasinoStore.STARTING_CHIPS
)

/** Holds the play-chip balance shared by the built-in games and the demo slots. */
class CasinoViewModel(private val store: CasinoStore) : ViewModel() {

    private val _state = MutableStateFlow(CasinoUiState(chips = store.chips, startChips = store.startChips))
    val uiState: StateFlow<CasinoUiState> = _state.asStateFlow()

    fun refreshChips() {
        _state.update { it.copy(chips = store.chips, startChips = store.startChips) }
    }

    fun settle(bet: Long, payout: Long) {
        val next = (store.chips - bet + payout).coerceAtLeast(0L)
        store.chips = next
        _state.update { it.copy(chips = next) }
    }

    /** The player picks their own chip limit (1 to 10,000,000). It becomes the balance and the refill amount. */
    fun setChips(amount: Long) {
        val value = amount.coerceIn(1L, CasinoStore.MAX_CHIPS)
        store.chips = value
        store.startChips = value
        _state.update { it.copy(chips = value, startChips = value) }
    }

    /** Refills the balance to the chosen chip limit. */
    fun resetChips() {
        val value = store.startChips
        store.chips = value
        _state.update { it.copy(chips = value) }
    }
}
