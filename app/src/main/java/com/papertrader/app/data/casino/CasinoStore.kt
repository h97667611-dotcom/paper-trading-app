package com.papertrader.app.data.casino

import android.content.Context

/**
 * Local storage for the casino tab: the play-chip balance (not real money, not connected to the
 * trading account) and the optional Hub88 operator credentials. Everything stays on this phone.
 */
class CasinoStore(context: Context) {
    private val prefs = context.getSharedPreferences("ghost_casino", Context.MODE_PRIVATE)

    var chips: Long
        get() = prefs.getLong("chips", STARTING_CHIPS)
        set(value) {
            prefs.edit().putLong("chips", value).apply()
        }

    var hubBaseUrl: String
        get() = prefs.getString("hub_base_url", "").orEmpty()
        set(value) {
            prefs.edit().putString("hub_base_url", value).apply()
        }

    var hubOperatorId: String
        get() = prefs.getString("hub_operator_id", "").orEmpty()
        set(value) {
            prefs.edit().putString("hub_operator_id", value).apply()
        }

    var hubPrivateKey: String
        get() = prefs.getString("hub_private_key", "").orEmpty()
        set(value) {
            prefs.edit().putString("hub_private_key", value).apply()
        }

    companion object {
        const val STARTING_CHIPS = 1_000L
    }
}
