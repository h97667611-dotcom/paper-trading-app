package com.papertrader.app.data.casino

import android.content.Context

/**
 * Local storage for the casino tab: the play-chip balance (not real money, not connected to the
 * trading account) and the optional SlotsLaunch credentials. Everything stays on this phone.
 */
class CasinoStore(context: Context) {
    private val prefs = context.getSharedPreferences("ghost_casino", Context.MODE_PRIVATE)

    var chips: Long
        get() = prefs.getLong("chips", STARTING_CHIPS)
        set(value) {
            prefs.edit().putLong("chips", value).apply()
        }

    var slotsToken: String
        get() = prefs.getString("slots_token", "").orEmpty()
        set(value) {
            prefs.edit().putString("slots_token", value).apply()
        }

    var slotsHost: String
        get() = prefs.getString("slots_host", "").orEmpty()
        set(value) {
            prefs.edit().putString("slots_host", value).apply()
        }

    companion object {
        const val STARTING_CHIPS = 1_000L
    }
}
