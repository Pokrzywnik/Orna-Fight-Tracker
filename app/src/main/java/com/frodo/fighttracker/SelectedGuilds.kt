package com.frodo.fighttracker

import android.content.Context

object SelectedGuilds {

    private const val PREF = "guild_selection"
    private const val KEY = "enabled"

    private val enabled = mutableSetOf<String>()

    private fun normalize(file: String): String {

        return when (file) {
            "anguish_1_0.csv",
            "anguish_2_0.csv" -> "anguish"

            else -> file
        }
    }

    fun load(context: Context) {

        val prefs =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        enabled.clear()

        enabled.addAll(
            prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        )

        if (enabled.isEmpty()) {

            enabled.addAll(
                listOf(
                    "remembrance.csv",
                    "coral.csv",
                    "anguish",
                    "sparring.csv",
                    "trials.csv",
                    "towers.csv"
                )
            )

            save(context)
        }
    }

    fun save(context: Context) {

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, enabled)
            .apply()
    }

    fun isEnabled(file: String): Boolean {
        return enabled.contains(normalize(file))
    }

    fun setEnabled(
        context: Context,
        file: String,
        value: Boolean
    ) {

        val key = normalize(file)

        if (value)
            enabled.add(key)
        else
            enabled.remove(key)

        save(context)
    }
}