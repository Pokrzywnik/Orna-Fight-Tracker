package com.frodo.fighttracker

import android.content.Context
import org.json.JSONArray

/** One normal (non raid / boss) monster that spawns in the open world. */
data class GleamingMob(
    val id: String,
    val name: String,
    val tier: Int,
    val icon: String
) {
    /** "img/monsters/aaru_cobra1.png" -> https://playorna.com/static/img/monsters/aaru_cobra1.png */
    val iconUrl: String
        get() = "https://playorna.com/static/$icon"
}

/**
 * Gleaming codex data + progress.
 *
 * Mob list: assets/gleaming_mobs.json (generated from monsters_en.json -
 * only non-boss / non-raid / non-arisen monsters that have a "world" spawn).
 * Progress: set of mob ids the user has marked as seen gleaming.
 */
object GleamingStore {

    private const val PREF = "gleaming"
    private const val KEY = "discovered"
    private const val ASSET = "gleaming_mobs.json"

    private var cache: List<GleamingMob>? = null

    var discovered: MutableSet<String> = mutableSetOf()
        private set

    fun mobs(context: Context): List<GleamingMob> {

        cache?.let { return it }

        val text = context.applicationContext.assets.open(ASSET)
            .bufferedReader()
            .use { it.readText() }

        val arr = JSONArray(text)

        val list = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            GleamingMob(
                id = o.getString("slug"),
                name = o.getString("name"),
                tier = o.getInt("tier"),
                icon = o.getString("icon")
            )
        }

        cache = list
        return list
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        discovered = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
    }

    fun isDiscovered(id: String): Boolean = id in discovered

    fun setDiscovered(context: Context, id: String, value: Boolean) {

        if (value) discovered.add(id) else discovered.remove(id)

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, discovered.toSet())
            .apply()
    }
}
