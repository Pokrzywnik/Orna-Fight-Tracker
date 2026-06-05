package com.frodo.fighttracker

import android.util.Log

data class ParsedItem(
    val name: String,
    val quality: String?,
    val level: Int?,
    val stats: Map<String, Int>
)
object AssessParser {

    private val qualityList = listOf(
        "broken", "poor", "normal",
        "superior", "famed", "legendary", "ornate"
    )

    fun parse(raw: String): ParsedItem? {


        val lines = raw.lines().map { it.trim() }
        if (lines.isEmpty()) return null

        val name = lines.firstOrNull { it.isNotBlank() } ?: return null

        val quality = qualityList.firstOrNull {
            raw.contains(it, ignoreCase = true)
        }

        val level = Regex("level\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val stats = mutableMapOf<String, Int>()

        val regex = Regex(
            "(mana|att|mag|dex|def|res|ward|crit|foresight)\\s*[:]?\\s*(-?\\d+)",
            RegexOption.IGNORE_CASE
        )

        regex.findAll(raw).forEach {
            val key = it.groupValues[1].lowercase()
            val value = it.groupValues[2].toIntOrNull() ?: 0
            stats[key] = value
        }
        Log.d(
            "ASSESS_PARSE",
            "name=$name quality=$quality stats=$stats"
        )
        return ParsedItem(name, quality, level, stats)
    }
}