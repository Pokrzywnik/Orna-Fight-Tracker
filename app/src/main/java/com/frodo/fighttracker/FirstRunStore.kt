package com.frodo.fighttracker

import android.content.Context

object FirstRunStore {

    private const val PREF = "first_run_pref"
    private const val KEY = "seen"

    fun isFirstRun(context: Context): Boolean {
        return context.getSharedPreferences(PREF, 0)
            .getBoolean(KEY, true)
    }

    fun setSeen(context: Context) {
        context.getSharedPreferences(PREF, 0)
            .edit()
            .putBoolean(KEY, false)
            .apply()
    }
}