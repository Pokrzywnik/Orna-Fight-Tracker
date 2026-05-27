package com.example.fighttracker

import android.content.Context

object SelectedMaterials {

    private const val PREF = "materials"
    private const val KEY = "selected_list"

    var selected: MutableSet<String> = mutableSetOf()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        selected = set.toMutableSet()
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY, selected).apply()
    }
}