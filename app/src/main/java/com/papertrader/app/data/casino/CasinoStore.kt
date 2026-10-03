package com.papertrader.app.data.casino

import android.content.Context

/**
 * Local storage for the casino tab: the play-chip balance (not real money, not connected to the
 * trading account) and the optional casino API credentials. Everything stays on this phone.
 */
class CasinoStore(context: Context) {
    private val prefs = context.getSharedPreferences("ghost_casino", Context.MODE_PRIVATE)

    var chips: Long
        get() = prefs.getLong("chips", STARTING_CHIPS)
        set(value) {
            prefs.edit().putLong("chips", value).apply()
        }

    var casinoBaseUrl: String
        get() = prefs.getString("casino_base_url", "").orEmpty()
        set(value) {
            prefs.edit().putString("casino_base_url", value).apply()
        }

    var casinoMerchantId: String
        get() = prefs.getString("casino_merchant_id", "").orEmpty()
        set(value) {
            prefs.edit().putString("casino_merchant_id", value).apply()
        }

    var casinoSecret: String
        get() = prefs.getString("casino_secret", "").orEmpty()
        set(value) {
            prefs.edit().putString("casino_secret", value).apply()
        }

    companion object {
        const val STARTING_CHIPS = 1_000L
        const val DEFAULT_BASE_URL = "https://p1.api-casino-g1.xyz"
    }
}
