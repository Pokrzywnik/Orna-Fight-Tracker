package com.frodo.fighttracker

import android.content.Context
import android.util.Log

data class CodexItem(
    val id: String,
    val stats: Map<String, Int>
)

object CodexRepository {

    private const val TAG = "CODEX"
    private val items = mutableMapOf<String, CodexItem>()

    fun get(id: String): CodexItem? = items[id]

    fun loadFromAssets(context: Context) {
        if (items.isNotEmpty()) {
            Log.d(TAG, "Already loaded: ${items.size} items")
            return
        }

        val text = context.assets.open("codex.toml")
            .bufferedReader()
            .use { it.readText() }

        parseToml(text)

        Log.d(TAG, "Loaded ${items.size} items from codex.toml")
    }

    private fun parseToml(text: String) {
        items.clear()

        var currentId: String? = null
        var currentStats: MutableMap<String, Int>? = null

        fun commitCurrent() {
            val id = currentId ?: return
            val stats = currentStats ?: return
            if (stats.isNotEmpty()) {
                items[id] = CodexItem(id, stats.toMap())
            }
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()

            if (line.isBlank() || line.startsWith("#")) {
                return@forEach
            }

            if (line.startsWith("[entries.\"items/")) {
                commitCurrent()

                currentId = extractId(line)

                currentStats =
                    if (line.endsWith(".stats]")) mutableMapOf()
                    else null

                return@forEach
            }

            if (currentStats != null) {
                val match =
                    Regex("""^([A-Za-z0-9_]+)\s*=\s*(-?\d+)\s*$""")
                        .find(line)

                if (match != null) {
                    val key = normalizeStatKey(match.groupValues[1])
                    val value = match.groupValues[2].toInt()
                    currentStats!![key] = value
                }
            }
        }

        commitCurrent()
    }

    private fun extractId(sectionLine: String): String? {
        val match = Regex("""items/([^"]+)""").find(sectionLine)
        return match?.groupValues?.getOrNull(1)
    }

    private fun normalizeStatKey(key: String): String {
        return when (key.lowercase()) {
            "att", "atk", "attack" -> "attack"
            "mag", "magic" -> "magic"
            "def", "defense" -> "defense"
            "res", "resistance" -> "resistance"
            "dex", "dexterity" -> "dexterity"
            "hp" -> "hp"
            "mana" -> "mana"
            "crit" -> "crit"
            "foresight" -> "foresight"
            "ward" -> "ward"
            "adornment_slots" -> "adornment_slots"
            else -> key.lowercase()
        }
    }
}