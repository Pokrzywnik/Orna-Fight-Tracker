package com.frodo.fighttracker

import android.content.Context

/**
 * Which of Orns / EXP / Gold are shown in the Tracker tab
 * and in the floating overlay. Default: all on.
 */
object CurrencyTracking {

    enum class Currency(val key: String, val label: String) {
        ORNS("orns", "Orns"),
        EXP("exp", "EXP"),
        GOLD("gold", "Gold")
    }

    private const val PREF = "settings"
    private const val KEY_TRACKED = "currency_tracked"
    private const val KEY_OVERLAY = "currency_overlay"

    private fun read(context: Context, key: String): Set<Currency> {

        val saved = context
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(key, null)
            ?: return Currency.values().toSet()

        return Currency.values().filter { it.key in saved }.toSet()
    }

    private fun write(context: Context, key: String, value: Set<Currency>) {

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(key, value.map { it.key }.toSet())
            .apply()
    }

    fun getTracked(context: Context): Set<Currency> =
        read(context, KEY_TRACKED)

    fun setTracked(context: Context, value: Set<Currency>) =
        write(context, KEY_TRACKED, value)

    fun getOverlay(context: Context): Set<Currency> =
        read(context, KEY_OVERLAY)

    fun setOverlay(context: Context, value: Set<Currency>) =
        write(context, KEY_OVERLAY, value)

    /** Tracked AND switched on for the overlay. */
    fun shownInOverlay(context: Context, c: Currency): Boolean =
        c in getTracked(context) && c in getOverlay(context)
}