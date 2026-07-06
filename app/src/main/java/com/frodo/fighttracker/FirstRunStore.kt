package com.frodo.fighttracker

import android.content.Context

object FirstRunStore {

    private const val PREF = "first_run_pref"
    private const val KEY = "guide_version"

    private const val CURRENT_GUIDE_VERSION = 2

    fun shouldShow(context: Context): Boolean {
        val shown = context
            .getSharedPreferences(PREF, 0)
            .getInt(KEY, 0)

        return shown < CURRENT_GUIDE_VERSION
    }

    fun markShown(context: Context) {
        context.getSharedPreferences(PREF, 0)
            .edit()
            .putInt(KEY, CURRENT_GUIDE_VERSION)
            .apply()
    }
}