package com.frodo.fighttracker

import android.content.Context
import android.util.Log
import org.json.JSONArray

class ItemMatcher(
    private val context: Context
) {

    companion object {
        private const val TAG = "ItemMatcher"
        private const val ITEMS_FILE = "items.json"
    }

    data class ItemMatch(
        val id: String,
        val name: String,
        val quality: String?,
        val enchant: String?,
        val originalOcr: String,
        val isBoss: Boolean,
        val baseStats: Map<String, Double>
    )

    /*
     * Item quality prefixes.
     *
     * These are removed from the beginning
     * of the OCR name.
     */
    private val qualityPrefixes =
        listOf(
            "broken",
            "poor",
            "superior",
            "famed",
            "legendary",
            "ornate",
            "masterforged",
            "demonforged",
            "godforged"
        ).sortedByDescending {
            it.length
        }

    /*
     * Element / enchant prefixes.
     */
    private val enchantPrefixes =
        listOf(
            "burning",
            "embered",
            "fiery",
            "flaming",
            "infernal",
            "scalding",
            "warm",

            "chilling",
            "icy",
            "oceanic",
            "snowy",
            "tidal",
            "winter",

            "balanced",
            "earthly",
            "grounded",
            "natural",
            "organic",
            "rocky",
            "stony",

            "electric",
            "shocking",
            "sparking",
            "stormy",
            "thunderous",

            "angelic",
            "bright",
            "divine",
            "moral",
            "pure",
            "purifying",
            "revered",
            "righteous",
            "saintly",
            "sublime",

            "corrupted",
            "diabolic",
            "demonic",
            "gloomy",
            "impious",
            "profane",
            "unhallowed",
            "wicked",

            "beastly",
            "bestial",
            "chimeric",
            "dragonic",
            "wild",

            "colorless",
            "customary",
            "normalized",
            "origin",
            "reformed",
            "renewed",
            "reworked"
        ).sortedByDescending {
            it.length
        }

    /*
     * Canonical item-name index.
     *
     * Example:
     *
     * "Cursed Coward's Blade"
     *
     * becomes:
     *
     * "cursedcowardsblade"
     *
     * The JSON item can therefore be matched
     * against OCR without needing spaces.
     */
    private val itemIndex:
            MutableMap<String, JsonItem> =
        mutableMapOf()

    init {

        Log.d(TAG, "========================================")
        Log.d(TAG, "INITIALIZING ITEM MATCHER")
        Log.d(TAG, "========================================")

        loadItems()

        Log.d(
            TAG,
            "Loaded items: ${itemIndex.size}"
        )

        Log.d(TAG, "========================================")
    }

    /**
     * Internal representation of one JSON item.
     */
    private data class JsonItem(
        val id: String,
        val name: String,
        val isBoss: Boolean,
        val baseStats: Map<String, Double>
    )


    private fun parseBaseStats(
        itemObject: org.json.JSONObject
    ): Map<String, Double> {

        val result =
            linkedMapOf<String, Double>()

        val stats =
            itemObject.optJSONArray("stats")
                ?: return result

        for (i in 0 until stats.length()) {

            val stat =
                stats.optJSONArray(i)
                    ?: continue

            if (stat.length() < 2) {
                continue
            }

            val name =
                stat.optString(
                    0,
                    ""
                ).trim()

            val rawValue =
                stat.optString(
                    1,
                    ""
                ).trim()

            /*
             * Ignore things which are not numeric
             *
             * Example:
             *
             * "Low HP → Stats" = "+12% (Class: Shadowmancy)"
             * "Adornment Slots" = "10"
             *
             * We only want stats used by ItemAssessor.
             */
            val canonicalName =
                when (name.lowercase()) {

                    "hp" ->
                        "HP"

                    "mana" ->
                        "Mana"

                    "attack" ->
                        "Attack"

                    "magic" ->
                        "Magic"

                    "dexterity" ->
                        "Dexterity"

                    "ward" ->
                        "Ward"

                    "crit" ->
                        "Crit"

                    "defense" ->
                        "Defense"

                    "resistance" ->
                        "Resistance"

                    "foresight" ->
                        "Foresight"

                    "crit damage" ->
                        "Crit Damage"

                    "ward bypass" ->
                        "Ward Bypass"

                    "turn delay chance" ->
                        "Turn Delay Chance"

                    else ->
                        null
                }
                    ?: continue

            /*
             * Remove %, + etc.
             *
             * Examples:
             *
             * "350"  -> 350
             * "-150" -> -150
             * "21%"  -> 21
             * "+5%"  -> 5
             */
            val numeric =
                Regex(
                    "[+-]?\\d+(?:[.,]\\d+)?"
                )
                    .find(rawValue)
                    ?.value
                    ?: continue

            val value =
                numeric
                    .replace(",", "")
                    .replace(".", "")
                    .toDoubleOrNull()
                    ?: continue

            /*
             * Only stats supported by ItemAssessor.
             *
             * Don't include Adornment Slots etc.
             */
            if (
                canonicalName in setOf(
                    "HP",
                    "Mana",
                    "Attack",
                    "Magic",
                    "Dexterity",
                    "Ward",
                    "Crit",
                    "Defense",
                    "Resistance",
                    "Foresight"
                )
            ) {

                result[
                    canonicalName
                ] = value
            }
        }

        return result
    }

    /**
     * Load Items from assets/items.json.
     */
    private fun loadItems() {

        try {

            val jsonText =
                context.assets
                    .open(ITEMS_FILE)
                    .bufferedReader(Charsets.UTF_8)
                    .use {
                        it.readText()
                    }

            val jsonArray =
                JSONArray(jsonText)

            Log.d(
                TAG,
                "JSON entries: ${jsonArray.length()}"
            )

            for (
            i in 0 until jsonArray.length()
            ) {

                val obj =
                    jsonArray.optJSONObject(i)
                        ?: continue

                /*
                 * We only care about actual items.
                 */
                val category =
                    obj.optString(
                        "category",
                        ""
                    )

                if (
                    category != "Items"
                ) {
                    continue
                }

                val id =
                    obj.optString(
                        "id",
                        ""
                    )

                val name =
                    obj.optString(
                        "name",
                        ""
                    )

                if (
                    id.isBlank() ||
                    name.isBlank()
                ) {
                    continue
                }

                val canonicalName =
                    canonicalize(name)

                if (
                    canonicalName.isBlank()
                ) {
                    continue
                }

                val isBoss =
                    detectBossScaling(
                        obj
                    )

                val baseStats =
                    parseBaseStats(
                        obj
                    )


                itemIndex[
                    canonicalName
                ] =
                    JsonItem(
                        id = id,
                        name = name,
                        isBoss = isBoss,
                        baseStats = baseStats
                    )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to load items.json",
                e
            )
        }
    }

    /**
     * Match one OCR item name against items.json.
     *
     * Example input:
     *
     * GodforgedCursedCoward''sBlade
     *
     * Result:
     *
     * id      = cursed-cowards-blade
     * name    = Cursed Coward's Blade
     * quality = godforged
     * enchant = null
     */
    fun match(
        ocrText: String
    ): ItemMatch? {

        if (
            ocrText.isBlank()
        ) {
            return null
        }

        Log.d(
            TAG,
            "----------------------------------------"
        )

        Log.d(
            TAG,
            "OCR ITEM: [$ocrText]"
        )

        val normalizedOcrText =
            ocrText
                .trim()
                .replace(Regex("""\s*\([A-Za-z]\)\s*$"""), "")

        val parsed =
            parseOcrName(
                normalizedOcrText
            )

        Log.d(
            TAG,
            "QUALITY: ${parsed.quality}"
        )

        Log.d(
            TAG,
            "ENCHANT: ${parsed.enchant}"
        )

        Log.d(
            TAG,
            "CANONICAL: ${parsed.canonicalName}"
        )

        val item =
            itemIndex[
                parsed.canonicalName
            ]

        if (
            item == null
        ) {

            Log.w(
                TAG,
                "NO ITEM MATCH: " +
                        parsed.canonicalName
            )

            return null
        }

        Log.d(
            TAG,
            "ITEM MATCH!"
        )

        Log.d(
            TAG,
            "ID: ${item.id}"
        )

        Log.d(
            TAG,
            "NAME: ${item.name}"
        )

        return ItemMatch(
            id = item.id,
            name = item.name,
            quality = parsed.quality,
            enchant = parsed.enchant,
            originalOcr = ocrText,
            isBoss = item.isBoss,
            baseStats = item.baseStats
        )
    }

    /**
     * Parse OCR name.
     */
    private fun parseOcrName(
        ocrText: String
    ): ParsedOcrName {

        /*
         * Lowercase first.
         */
        var text =
            ocrText
                .trim()
                .lowercase()

        /*
         * OCR sometimes produces:
         *
         * Coward''s
         *
         * instead of:
         *
         * Coward's
         *
         * For canonical matching apostrophes
         * are irrelevant anyway.
         */
        text =
            text.replace(
                "'",
                ""
            )

        /*
         * Remove accidental spaces.
         */
        text =
            text.replace(
                " ",
                ""
            )

        var quality: String? =
            null

        var enchant: String? =
            null

        /*
         * ------------------------------------
         * QUALITY
         * ------------------------------------
         *
         * Example:
         *
         * godforgedcursedcowardsblade
         *
         * becomes:
         *
         * cursedcowardsblade
         */
        for (
        prefix in qualityPrefixes
        ) {

            if (
                text.startsWith(prefix)
            ) {

                quality =
                    prefix

                text =
                    text.removePrefix(
                        prefix
                    )

                break
            }
        }

        /*
         * ------------------------------------
         * ENCHANT
         * ------------------------------------
         *
         * Example:
         *
         * burningcursedcowardsblade
         *
         * becomes:
         *
         * cursedcowardsblade
         */
        for (
        prefix in enchantPrefixes
        ) {

            if (
                text.startsWith(prefix)
            ) {

                enchant =
                    prefix

                text =
                    text.removePrefix(
                        prefix
                    )

                break
            }
        }

        /*
         * Final canonical representation.
         *
         * Only letters and numbers remain.
         */
        val canonical =
            canonicalize(
                text
            )

        return ParsedOcrName(
            quality = quality,
            enchant = enchant,
            canonicalName = canonical
        )
    }

    private data class ParsedOcrName(
        val quality: String?,
        val enchant: String?,
        val canonicalName: String
    )

    /**
     * Convert an item name to a comparison key.
     *
     * Examples:
     *
     * Cursed Coward's Blade
     * CursedCoward''sBlade
     * cursed-cowards-blade
     * Cursed Cowards Blade
     *
     * all become:
     *
     * cursedcowardsblade
     */
    fun canonicalize(
        text: String
    ): String {

        return text
            .lowercase()
            .filter {
                it.isLetterOrDigit()
            }
    }

    private fun detectBossScaling(
        itemObject: org.json.JSONObject
    ): Boolean {

        val drops =
            itemObject.optJSONArray("drops")
                ?: return false

        for (i in 0 until drops.length()) {

            val dropEntry =
                drops.optJSONArray(i)
                    ?: continue

            if (dropEntry.length() < 2) {
                continue
            }

            val category =
                dropEntry.optString(
                    0,
                    ""
                )

            if (
                !category.equals(
                    "Dropped by",
                    ignoreCase = true
                )
            ) {
                continue
            }

            val sources =
                dropEntry.optJSONArray(1)
                    ?: continue

            for (j in 0 until sources.length()) {

                val source =
                    sources.optJSONObject(j)
                        ?: continue

                val icon =
                    source.optString(
                        "icon",
                        ""
                    )
                        .trim()
                        .lowercase()

                if (
                    icon.startsWith(
                        "/img/bosses/"
                    )
                ) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Debug helper.
     *
     * Useful for testing all three variants.
     */
    fun debugMatch(
        ocrText: String
    ) {

        val result =
            match(
                ocrText
            )

        if (
            result == null
        ) {

            Log.d(
                TAG,
                "DEBUG MATCH: FAILED"
            )

        } else {

            Log.d(
                TAG,
                "DEBUG MATCH RESULT:"
            )

            Log.d(
                TAG,
                "  OCR      = ${result.originalOcr}"
            )

            Log.d(
                TAG,
                "  ID       = ${result.id}"
            )

            Log.d(
                TAG,
                "  NAME     = ${result.name}"
            )

            Log.d(
                TAG,
                "  QUALITY  = ${result.quality}"
            )

            Log.d(
                TAG,
                "  ENCHANT  = ${result.enchant}"
            )
        }
    }
}