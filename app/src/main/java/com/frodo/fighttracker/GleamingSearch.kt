package com.frodo.fighttracker

import java.util.Locale

/** Name matching for the Gleaming Codex, including hidden Arisen display aliases. */
internal object GleamingSearch {

    private const val ARISEN_PREFIX = "arisen "

    /**
     * Arisen world-boss records whose exported display names omit the prefix.
     * The IDs come directly from gleaming_mobs.json. Sprite filenames are not
     * used as metadata because Fey and Shade mobs also reuse *_arisen artwork.
     */
    private val arisenWorldMobIds = setOf(
        "camazotz",
        "cerberus-c633f157",
        "minotaur-935686b0",
        "lizarr-lord-9fded6ad",
        "ankou",
        "arachne-e15f8316",
        "carman-5458cf68",
        "fenrir",
        "ronin-63fcd94a",
        "cthulhu-edf67d83",
        "fafnir",
        "finfolk-ef529612",
        "great-gazer-5e49b351",
        "hydra-d754db90",
        "sluagh-1999e7d6",
        "tiamat-316a9861"
    )

    fun matches(mob: GleamingMob, rawQuery: String): Boolean {
        val query = normalize(rawQuery)
        if (query.isEmpty()) return true

        val name = normalize(mob.name)
        if (query in name) return true

        // Some Arisen monsters are displayed without the prefix (for example
        // "Fafnir" uses arisen_fafnir.png). Accept both "Fafnir" and
        // "Arisen Fafnir" without changing the visible codex name.
        val isArisen = name.startsWith(ARISEN_PREFIX) || mob.id in arisenWorldMobIds
        if (!isArisen) return false

        // "arisen" by itself acts as an instant category filter. Longer
        // queries match against the display name after removing the prefix.
        if (query == ARISEN_PREFIX.trim()) return true
        if (!query.startsWith(ARISEN_PREFIX)) return false
        val withoutPrefix = query.removePrefix(ARISEN_PREFIX).trimStart()
        return withoutPrefix.isNotEmpty() && withoutPrefix in name
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
