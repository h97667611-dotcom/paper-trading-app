package com.papertrader.app.data.casino

import android.content.Context

/**
 * Local storage for the casino tab: the play-chip balance. It is not real money and not
 * connected to the trading account. Everything stays on this phone.
 */
class CasinoStore(context: Context) {
    private val prefs = context.getSharedPreferences("ghost_casino", Context.MODE_PRIVATE)

    var chips: Long
        get() = prefs.getLong("chips", STARTING_CHIPS)
        set(value) {
            prefs.edit().putLong("chips", value).apply()
        }

    /** The chip limit the player chose: used as the balance when chips are refilled or reset. */
    var startChips: Long
        get() = prefs.getLong("start_chips", STARTING_CHIPS)
        set(value) {
            prefs.edit().putLong("start_chips", value).apply()
        }

    companion object {
        const val STARTING_CHIPS = 1_000L
        const val MAX_CHIPS = 10_000_000L
    }
}
