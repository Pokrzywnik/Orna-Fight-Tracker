package com.frodo.fighttracker

import android.util.Log
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Complete item assessment pipeline.
 *
 * OCR text
 *     ↓
 * item normalization
 *     ↓
 * ItemMatcher
 *     ↓
 * items.json
 *     ↓
 * OCR stats
 *     ↓
 * reverse stat calculation
 *     ↓
 * per-stat quality
 *     ↓
 * outlier resistant quality estimation
 */
class ItemAssessor {

    companion object {

        private const val TAG = "ItemAssessor"

        /**
         * Upgrade mapping
         *
         * 1-10 = normal upgrade levels
         * 11   = MF
         * 12   = DF
         * 13   = GF
         */
        private val upgradeLevels = mapOf(
            "1" to 1,
            "2" to 2,
            "3" to 3,
            "4" to 4,
            "5" to 5,
            "6" to 6,
            "7" to 7,
            "8" to 8,
            "9" to 9,
            "10" to 10,
            "MF" to 11,
            "DF" to 12,
            "GF" to 13
        )

        /**
         * Quality bonus
         */
        private val qualityBonuses = mapOf(
            "MF" to 0.01,
            "DF" to 0.02,
            "GF" to 0.03
        )

        /**
         * Hard quality constraints from Orna rarity.
         */
        private val qualityRanges = mapOf(
            "BROKEN" to 70..89,
            "POOR" to 90..100,
            "SUPERIOR" to 101..120,
            "FAMED" to 121..140,
            "LEGENDARY" to 141..170,
            "ORNATE" to 171..200
        )

        /**
         * Stats that are percentages in items.json.
         */
        private val percentageStats = setOf(
            "Ward",
            "Crit"
        )

        /**
         * OCR aliases.
         */
        private val statAliases = mapOf(

            "hp" to "HP",
            "health" to "HP",

            "mana" to "Mana",

            "att" to "Attack",
            "attack" to "Attack",

            "mag" to "Magic",
            "magic" to "Magic",

            "def" to "Defense",
            "defense" to "Defense",

            "res" to "Resistance",
            "resistance" to "Resistance",

            "dex" to "Dexterity",
            "dexterity" to "Dexterity",

            "ward" to "Ward",

            "crit" to "Crit",

            "foresight" to "Foresight"
        )

    }

    /**
     * Parsed OCR stat.
     */
    data class ParsedStat(
        val name: String,
        val value: Double
    )

    /**
     * Result of reverse calculating one stat.
     */
    data class StatQuality(
        val statName: String,

        val actualValue: Double,

        val baseValue: Double,

        /**
         * Expected stat at 100% quality
         * for the detected item level.
         */
        val expectedAt100: Double,

        /**
         * Minimum possible quality.
         */
        val minQuality: Double,

        /**
         * Maximum possible quality.
         */
        val maxQuality: Double,

        /**
         * Middle point of the possible interval.
         */
        val estimatedQuality: Double,

        /**
         * Exact integer quality values that reproduce
         * the OCR value using the game formula.
         */
        val exactQualityCandidates: Set<Int> = emptySet(),

        /**
         * Whether this stat is likely an outlier.
         */
        var outlier: Boolean = false
    )
    private data class QualityCandidate(
        val minQuality: Int,
        val maxQuality: Int,
        val matchingStats: Int,
        val matchingStatNames: List<String>
    )

    /**
     * Final assessment.
     */
    data class Assessment(
        val itemId: String,
        val itemName: String,

        val quality: String?,
        val enchant: String?,

        val upgradeLevel: String,

        val actualStats: Map<String, Double>,

        val statQualities: List<StatQuality>,

        /**
         * Exact quality if it was uniquely determined.
         * Example:
         * 199 -> 1.99
         * Null means that the quality is only known as a range.
         */
        val exactQuality: Double?,

        val qualityRangeMin: Double,

        val qualityRangeMax: Double
    )

    /**
     * ------------------------------------------------------------
     * MAIN ENTRY
     * ------------------------------------------------------------
     *
     * itemId/itemName comes from ItemMatcher.
     *
     * OCR text can contain multiple stat groups.
     */
    fun assess(
        item: ItemMatcher.ItemMatch,
        upgradeLevel: String,
        ocrTexts: List<String>
    ): Assessment {

        val itemId =
            item.id

        val itemName =
            item.name

        val quality =
            item.quality

        val enchant =
            item.enchant

        val isBoss =
            item.isBoss

        Log.d(TAG, "========================================")
        Log.d(TAG, "ITEM ASSESSMENT")
        Log.d(TAG, "========================================")

        Log.d(TAG, "ITEM: $itemName")
        Log.d(TAG, "ID: $itemId")
        Log.d(TAG, "QUALITY: $quality")
        Log.d(TAG, "ENCHANT: $enchant")
        Log.d(TAG, "LEVEL: $upgradeLevel")
        Log.d(TAG, "BOSS: $isBoss")

        /*
         * 1. Parse all OCR stat lines.
         */
        val actualStats =
            parseStats(
                ocrTexts
            )

        Log.d(
            TAG,
            "ACTUAL STATS: $actualStats"
        )

        /*
         * 2. Calculate quality independently
         *    for every stat.
         */
        val statQualities =
            mutableListOf<StatQuality>()

        for ((statName, actualValue) in actualStats) {

            val baseValue =
                item.baseStats[statName]

            if (baseValue == null) {

                Log.d(
                    TAG,
                    "Skipping $statName - not present in items.json"
                )

                continue
            }

            val allowedQualityRange =
                getQualityRange(quality)

            val statQuality =
                calculateStatQuality(
                    statName = statName,
                    actualValue = actualValue,
                    baseValue = baseValue,
                    upgradeLevel = upgradeLevel,
                    isBoss = isBoss,
                    allowedQualityRange = allowedQualityRange
                )

            if (statQuality != null) {
                statQualities.add(statQuality)
            }
        }

        /*
 * 3. Find common quality using the initially detected
 *    boss scaling.
 *
 *    Normally:
 *      boss item -> boss scaling
 *      normal item -> normal scaling
 */
        var commonQuality =
            findBestCommonQuality(
                actualStats = actualStats,
                item = item,
                upgradeLevel = upgradeLevel,
                isBossScaling = isBoss
            )

        /*
 * ------------------------------------------------------------
 * SCALING SELECTION
 * ------------------------------------------------------------
 *
 * A boss item can occasionally contain stats whose values
 * match NORMAL scaling better than BOSS scaling.
 *
 * Therefore we do NOT use:
 *
 *     if (bossResult == null) tryNormal()
 *
 * because boss scaling may produce a misleading partial result.
 *
 * Example:
 *
 * BOSS:
 *     Resistance -> 169%
 *     Magic      -> no match
 *
 * NORMAL:
 *     Resistance -> 199%
 *     Magic      -> 199%
 *
 * In that case NORMAL is clearly the correct model.
 */

        if (isBoss) {

            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "TESTING BOSS VS NORMAL SCALING"
            )

            /*
             * --------------------------------------------------------
             * BOSS SCALING
             * --------------------------------------------------------
             */

            val bossQuality =
                findBestCommonQuality(
                    actualStats = actualStats,
                    item = item,
                    upgradeLevel = upgradeLevel,
                    isBossScaling = true
                )

            /*
             * --------------------------------------------------------
             * NORMAL SCALING
             * --------------------------------------------------------
             */

            val normalQuality =
                findBestCommonQuality(
                    actualStats = actualStats,
                    item = item,
                    upgradeLevel = upgradeLevel,
                    isBossScaling = false
                )

            /*
             * Number of OCR stats which can actually be explained
             * by each scaling model.
             */
            val bossSupport =
                bossQuality?.matchingStats ?: 0

            val normalSupport =
                normalQuality?.matchingStats ?: 0

            val totalStats =
                actualStats.count { (statName, _) ->
                    item.baseStats.containsKey(statName)
                }

            Log.d(
                TAG,
                "BOSS RESULT: " +
                        "${bossQuality?.minQuality}-${bossQuality?.maxQuality}% " +
                        "support=$bossSupport/$totalStats"
            )

            Log.d(
                TAG,
                "NORMAL RESULT: " +
                        "${normalQuality?.minQuality}-${normalQuality?.maxQuality}% " +
                        "support=$normalSupport/$totalStats"
            )

            /*
             * --------------------------------------------------------
             * SELECT BEST SCALING
             * --------------------------------------------------------
             *
             * Prefer the model which explains more stats.
             *
             * If both explain exactly the same number of stats,
             * prefer the original boss classification.
             */

            val useNormalScaling =
                normalSupport > bossSupport

            if (useNormalScaling) {

                Log.d(
                    TAG,
                    "SCALING DECISION: NORMAL"
                )

                commonQuality =
                    normalQuality

                /*
                 * Recalculate StatQuality objects using
                 * normal scaling so that the displayed per-stat
                 * values also agree with the selected model.
                 */
                statQualities.clear()

                for ((statName, actualValue) in actualStats) {

                    val baseValue =
                        item.baseStats[statName]
                            ?: continue

                    val allowedQualityRange =
                        getQualityRange(quality)

                    val statQuality =
                        calculateStatQuality(
                            statName = statName,
                            actualValue = actualValue,
                            baseValue = baseValue,
                            upgradeLevel = upgradeLevel,
                            isBoss = false,
                            allowedQualityRange = allowedQualityRange
                        )

                    if (statQuality != null) {
                        statQualities.add(statQuality)
                    }
                }

            } else {

                Log.d(
                    TAG,
                    "SCALING DECISION: BOSS"
                )

                commonQuality =
                    bossQuality
            }

            Log.d(
                TAG,
                "========================================"
            )
        }

        /*
         * 4. Mark stats that don't agree with
         *    the common quality as possible
         *    adornment/outlier stats.
         */
        if (commonQuality != null) {

            markOutliersAgainstQuality(
                stats = statQualities,
                commonQualityMin = commonQuality.minQuality,
                commonQualityMax = commonQuality.maxQuality
            )
        }

        /*
         * 5. Final quality.

         * This can be:
         * 174%
         * or:
         * 170-176%
         */
        val finalQualityMin =
            commonQuality
                ?.minQuality
                ?.div(100.0)
                ?: 0.0

        val finalQualityMax =
            commonQuality
                ?.maxQuality
                ?.div(100.0)
                ?: 0.0

        /*
         * Exact quality exists ONLY when min == max.
         */
        val exactQuality =
            if (
                commonQuality != null &&
                commonQuality.minQuality ==
                commonQuality.maxQuality
            ) {
                commonQuality.minQuality / 100.0
            } else {
                null
            }

        /*
         * 6. Overall possible range.
         *
         * This is informational only.
         * It does NOT determine final quality.
         */
        val valid =
            statQualities
                .filter { !it.outlier }

        val rangeMin =
            if (valid.isNotEmpty()) {
                valid.maxOf {
                    it.minQuality
                }
            } else {
                0.0
            }

        val rangeMax =
            if (valid.isNotEmpty()) {
                valid.minOf {
                    it.maxQuality
                }
            } else {
                0.0
            }

        val hasValidOverallRange =
            valid.isNotEmpty() &&
                    rangeMin <= rangeMax

        Log.d(TAG, "----------------------------------------")

        for (stat in statQualities) {

            Log.d(
                TAG,
                "${stat.statName}: " +
                        "actual=${stat.actualValue} " +
                        "base=${stat.baseValue} " +
                        "100%=${stat.expectedAt100} " +
                        "quality=" +
                        "%.2f".format(
                            stat.estimatedQuality * 100.0
                        ) +
                        "% " +
                        "range=" +
                        "%.2f".format(
                            stat.minQuality * 100.0
                        ) +
                        "-" +
                        "%.2f".format(
                            stat.maxQuality * 100.0
                        ) +
                        "% " +
                        "outlier=${stat.outlier}"
            )
        }

        Log.d(TAG, "----------------------------------------")

        if (commonQuality != null) {

            if (commonQuality.minQuality == commonQuality.maxQuality) {

                Log.d(
                    TAG,
                    "FINAL QUALITY = ${commonQuality.minQuality}%"
                )

            } else {

                Log.d(
                    TAG,
                    "FINAL QUALITY RANGE = " +
                            "${commonQuality.minQuality}%-" +
                            "${commonQuality.maxQuality}%"
                )
            }

        } else {

            Log.d(
                TAG,
                "FINAL QUALITY = UNKNOWN"
            )
        }

        Log.d(
            TAG,
            "QUALITY RANGE = " +
                    "%.2f".format(
                        rangeMin * 100.0
                    ) +
                    "-" +
                    "%.2f".format(
                        rangeMax * 100.0
                    ) +
                    "%"
        )

        return Assessment(
            itemId = itemId,
            itemName = itemName,
            quality = quality,
            enchant = enchant,
            upgradeLevel = upgradeLevel,
            actualStats = actualStats,
            statQualities = statQualities,
            exactQuality = exactQuality,
            qualityRangeMin = finalQualityMin,
            qualityRangeMax = finalQualityMax
        )
    }

    /**
     * ------------------------------------------------------------
     * OCR STAT PARSER
     * ------------------------------------------------------------
     *
     * Examples:
     *
     * HP:-278(-278)
     * Mana:620(+464)
     * Att:2.052(+1.412)
     * Mag:180(-460)
     * Dex:315(+229)
     * Ward:122%(+122)
     *
     * Parenthesized values are deliberately ignored.
     */
    private fun parseStats(
        ocrTexts: List<String>
    ): Map<String, Double> {

        val result =
            linkedMapOf<String, Double>()

        /*
         * Match:
         *
         * HP:-278
         * Mana:620
         * Att:2.052
         * Ward:122%
         *
         * We stop at the first opening parenthesis.
         */
        val regex =
            Regex(
                pattern =
                    "(?i)" +
                            "(HP|Mana|Att|Attack|Mag|Magic|Def|Defense|Res|Resistance|Dex|Dexterity|Ward|Crit|Foresight)" +
                            "\\s*:\\s*" +
                            "([+-]?[0-9oO]+(?:[.,][0-9oO]+)?)" +
                            "\\s*%?"
            )

        for (text in ocrTexts) {

            for (match in regex.findAll(text)) {

                val rawName =
                    match.groupValues[1]
                        .lowercase()

                val canonicalName =
                    statAliases[rawName]
                        ?: continue

                val rawValue =
                    match.groupValues[2]

                val value =
                    parseNumber(
                        rawValue
                    )

                /*
                 * Do not overwrite a stat if it appears
                 * multiple times with different values.
                 *
                 * First occurrence is the primary one.
                 */
                if (!result.containsKey(canonicalName)) {

                    result[canonicalName] =
                        value
                }
            }
        }

        return result
    }

    /**
     * Parse numbers used by OCR.
     *
     * Screenshots commonly produce:
     *
     * 2.052
     * 2,052
     *
     * which means 2052, not 2.052.
     */
    private fun parseNumber(
        value: String
    ): Double {

        val normalized =
            value
                .replace("o", "0", ignoreCase = true)
                .replace(",", "")
                .replace(".", "")

        return normalized.toDoubleOrNull()
            ?: 0.0
    }

    /**
     * ------------------------------------------------------------
     * QUALITY CALCULATION
     * ------------------------------------------------------------
     */
    private fun calculateStatQuality(
        statName: String,
        actualValue: Double,
        baseValue: Double,
        upgradeLevel: String,
        isBoss: Boolean,
        allowedQualityRange: IntRange?
    ): StatQuality? {

        if (baseValue == 0.0) {
            return null
        }

        val level =
            upgradeLevels[
                upgradeLevel.uppercase()
            ] ?: return null

        /*
         * Calculate stat before quality multiplier.
         */
        val scaledBase =
            calculateScaledBase(
                baseStat = baseValue,
                isBoss = isBoss,
                level = level
            )

        /*
         * What would this stat be at exactly
         * 100% quality?
         */
        val qualityBonus =
            qualityBonuses[
                upgradeLevel.uppercase()
            ] ?: 0.0

        val expectedAt100 =
            calculateFinalValue(
                scaledBase = scaledBase,
                quality = 1.0 + qualityBonus,
                isWard =
                    statName.equals(
                        "Ward",
                        ignoreCase = true
                    )
            )

        /*
         * Reverse engineer quality.
         *
         * We calculate an interval instead of one
         * exact value because the game rounds.
         */
        val range =
            reverseQualityRange(
                actualValue = actualValue,
                scaledBase = scaledBase,
                qualityBonus = qualityBonus,
                isWard =
                    statName.equals(
                        "Ward",
                        ignoreCase = true
                    )
            )

        if (range == null) {
            return null
        }

        val exactQualityCandidates =
            if (allowedQualityRange != null) {

                findExactQualityCandidates(
                    statName = statName,
                    actualValue = actualValue,
                    baseValue = baseValue,
                    upgradeLevel = upgradeLevel,
                    isBoss = isBoss,
                    allowedRange = allowedQualityRange
                )

            } else {
                emptySet()
            }

        return StatQuality(
            statName = statName,
            actualValue = actualValue,
            baseValue = baseValue,
            expectedAt100 = expectedAt100,
            minQuality = range.first,
            maxQuality = range.second,
            estimatedQuality =
                if (exactQualityCandidates.size == 1) {
                    exactQualityCandidates.first() / 100.0
                } else {
                    range.first
                },
            exactQualityCandidates = exactQualityCandidates
        )
    }

    private fun calculateStatAtQuality(
        statName: String,
        baseValue: Double,
        upgradeLevel: String,
        qualityPercent: Int,
        isBoss: Boolean
    ): Double {

        val level =
            upgradeLevels[
                upgradeLevel.uppercase()
            ] ?: return Double.NaN

        val scaledBase =
            calculateScaledBase(
                baseStat = baseValue,
                isBoss = isBoss,
                level = level
            )

        val qualityBonus =
            qualityBonuses[
                upgradeLevel.uppercase()
            ] ?: 0.0

        val quality =
            qualityPercent / 100.0

        return calculateFinalValue(
            scaledBase = scaledBase,
            quality = quality + qualityBonus,
            isWard =
                statName.equals(
                    "Ward",
                    ignoreCase = true
                )
        )
    }

    private fun getQualityRange(
        quality: String?
    ): IntRange? {

        if (quality.isNullOrBlank()) {
            return null
        }

        return when (
            quality.trim().uppercase()
        ) {

            "MF",
            "MASTERFORGED",
            "DF",
            "DEMONFORGED",
            "GF",
            "GODFORGED" -> {
                /*
                 * Forged item keeps the original item's quality.
                 * The forged prefix itself does NOT tell us whether
                 * the underlying item was e.g. 180%, 189%, 199%, etc.
                 *
                 * Therefore we must search the full possible quality
                 * range.
                 */
                70..200
            }

            else -> {
                qualityRanges[
                    quality.trim().uppercase()
                ]
            }
        }
    }

    private fun findExactQualityCandidates(
        statName: String,
        actualValue: Double,
        baseValue: Double,
        upgradeLevel: String,
        isBoss: Boolean,
        allowedRange: IntRange
    ): Set<Int> {

        val candidates =
            mutableSetOf<Int>()

        for (quality in allowedRange) {

            val calculated =
                calculateStatAtQuality(
                    statName = statName,
                    baseValue = baseValue,
                    upgradeLevel = upgradeLevel,
                    qualityPercent = quality,
                    isBoss = isBoss
                )

            if (calculated == actualValue) {
                candidates.add(quality)
            }
        }

        Log.d(
            TAG,
            "EXACT QUALITY $statName: " +
                    "actual=$actualValue " +
                    "candidates=$candidates"
        )

        return candidates
    }
    private fun findBestCommonQuality(
        actualStats: Map<String, Double>,
        item: ItemMatcher.ItemMatch,
        upgradeLevel: String,
        isBossScaling: Boolean = item.isBoss
    ): QualityCandidate? {

        val allowedRange =
            getQualityRange(item.quality)
                ?: return null

        /*
         * For every stat we have a set of exact quality
         * values that reproduce the OCR value.
         *
         * Example:
         *
         * Attack -> 170,171,172,173,174,175,176
         * Magic  -> 174,175,176
         *
         * Common quality:
         *
         * 174,175,176
         *
         * Therefore final result is:
         *
         * 174-176%
         *
         * NOT 175%.
         */

        val statCandidates =
            mutableMapOf<String, Set<Int>>()

        for ((statName, actualValue) in actualStats) {

            val baseValue =
                item.baseStats[statName]
                    ?: continue

            val candidates =
                findExactQualityCandidates(
                    statName = statName,
                    actualValue = actualValue,
                    baseValue = baseValue,
                    upgradeLevel = upgradeLevel,
                    isBoss = isBossScaling,
                    allowedRange = allowedRange
                )

            if (candidates.isNotEmpty()) {

                statCandidates[statName] =
                    candidates
            }
        }

        if (statCandidates.isEmpty()) {
            return null
        }

        /*
         * --------------------------------------------------------
         * 1. Try to find quality values supported by ALL stats.
         * --------------------------------------------------------
         */

        var commonValues =
            statCandidates.values
                .reduce { common, candidates ->
                    common.intersect(candidates)
                }

        if (commonValues.isNotEmpty()) {

            val minQuality =
                commonValues.minOrNull()
                    ?: return null

            val maxQuality =
                commonValues.maxOrNull()
                    ?: return null

            val matchingStats =
                statCandidates
                    .filter { (_, candidates) ->
                        candidates.any {
                            it in commonValues
                        }
                    }

            Log.d(
                TAG,
                "COMMON QUALITY RANGE: " +
                        "${minQuality}-${maxQuality}% " +
                        "stats=${matchingStats.keys}"
            )

            return QualityCandidate(
                minQuality = minQuality,
                maxQuality = maxQuality,
                matchingStats = matchingStats.size,
                matchingStatNames = matchingStats.keys.toList()
            )
        }

        /*
         * --------------------------------------------------------
         * 2. No quality is shared by ALL stats.
         *
         * This can happen when one stat was modified by
         * an adornment.
         *
         * We now find the largest exact intersection
         * shared by the greatest number of stats.
         *
         * IMPORTANT:
         *
         * No average.
         * No median.
         * No midpoint.
         * No "closest" artificial percentage.
         * --------------------------------------------------------
         */

        val qualitySupport =
            mutableMapOf<Int, MutableList<String>>()

        for ((statName, candidates) in statCandidates) {

            for (quality in candidates) {

                qualitySupport
                    .getOrPut(quality) {
                        mutableListOf()
                    }
                    .add(statName)
            }
        }

        if (qualitySupport.isEmpty()) {
            return null
        }

        /*
         * Find the maximum number of stats agreeing
         * on the same quality.
         */
        val maxSupport =
            qualitySupport.values
                .maxOf {
                    it.size
                }

        val supportedQualities =
            qualitySupport
                .filterValues {
                    it.size == maxSupport
                }
                .keys

        if (supportedQualities.isEmpty()) {
            return null
        }

        /*
         * Consecutive exact qualities form one range.
         * Example:
         * 174,175,176
         * becomes:
         * 174-176%
         */
        val sortedQualities =
            supportedQualities.sorted()

        var bestStart =
            sortedQualities.first()

        var bestEnd =
            sortedQualities.first()

        var currentStart =
            sortedQualities.first()

        var currentEnd =
            sortedQualities.first()

        for (i in 1 until sortedQualities.size) {

            if (
                sortedQualities[i] ==
                currentEnd + 1
            ) {

                currentEnd =
                    sortedQualities[i]

            } else {

                if (
                    currentEnd - currentStart >
                    bestEnd - bestStart
                ) {

                    bestStart =
                        currentStart

                    bestEnd =
                        currentEnd
                }

                currentStart =
                    sortedQualities[i]

                currentEnd =
                    sortedQualities[i]
            }
        }

        /*
         * Check final range.
         */
        if (
            currentEnd - currentStart >
            bestEnd - bestStart
        ) {

            bestStart =
                currentStart

            bestEnd =
                currentEnd
        }

        val minQuality =
            bestStart

        val maxQuality =
            bestEnd

        val matchingStatNames =
            qualitySupport.values
                .flatten()
                .distinct()

        Log.d(
            TAG,
            "BEST QUALITY RANGE: " +
                    "${minQuality}-${maxQuality}% " +
                    "support=$maxSupport " +
                    "stats=$matchingStatNames"
        )

        return QualityCandidate(
            minQuality = minQuality,
            maxQuality = maxQuality,
            matchingStats = maxSupport,
            matchingStatNames = matchingStatNames
        )
    }
    /**
     * Same level scaling as the lloir OrnaCalculator,
     * but WITHOUT its negative-stat bug.
     */

    private fun detectBossScaling(
        dropIcons: List<String>
    ): Boolean {

        return dropIcons.any { icon ->
            icon
                .trim()
                .lowercase()
                .startsWith("/img/bosses/")
        }
    }

    private fun calculateScaledBase(
        baseStat: Double,
        isBoss: Boolean,
        level: Int
    ): Double {

        val divisor =
            if (baseStat >= 0.0) {

                if (isBoss) {
                    8.0
                } else {
                    10.0
                }

            } else {

                if (isBoss) {
                    -600.0
                } else {
                    -75.0
                }
            }

        val statDelta =
            ceil(
                baseStat / divisor
            )

        val levelScaling =
            if (level == 1) {
                0.0
            } else {
                level * statDelta
            }

        return baseStat + levelScaling
    }

    /**
     * Apply quality multiplier exactly as the
     * supplied calculator does.
     */
    private fun calculateFinalValue(
        scaledBase: Double,
        quality: Double,
        isWard: Boolean
    ): Double {

        return if (isWard) {

            ceil(
                scaledBase *
                        quality *
                        100.0
            ) / 100.0

        } else {

            ceil(
                scaledBase *
                        quality
            )
        }
    }

    /**
     * Reverse the ceil() operation.
     *
     * For:
     *
     * ceil(X) = actual
     *
     * we know:
     *
     * actual - 1 < X <= actual
     *
     * This gives us a QUALITY RANGE instead
     * of pretending that the result is exact.
     */
    private fun reverseQualityRange(
        actualValue: Double,
        scaledBase: Double,
        qualityBonus: Double,
        isWard: Boolean
    ): Pair<Double, Double>? {

        if (scaledBase == 0.0) {
            return null
        }

        if (isWard) {

            /*
             * Ward is calculated with two decimal places.
             */
            val actual =
                actualValue

            val lower =
                (actual - 0.01) /
                        scaledBase

            val upper =
                actual /
                        scaledBase

            val minQuality =
                lower - qualityBonus

            val maxQuality =
                upper - qualityBonus

            return normalizeRange(
                minQuality,
                maxQuality
            )
        }

        /*
         * Normal integer stat.
         */
        val lower =
            (actualValue - 1.0) /
                    scaledBase

        val upper =
            actualValue /
                    scaledBase

        val minQuality =
            lower - qualityBonus

        val maxQuality =
            upper - qualityBonus

        return normalizeRange(
            minQuality,
            maxQuality
        )
    }

    /**
     * Ensure quality range is sane.
     */
    private fun normalizeRange(
        minValue: Double,
        maxValue: Double
    ): Pair<Double, Double>? {

        val low =
            min(
                minValue,
                maxValue
            )

        val high =
            max(
                minValue,
                maxValue
            )

        /*
         * Completely impossible result.
         */
        if (high < 0.0) {
            return null
        }

        return Pair(
            max(0.0, low),
            max(0.0, high)
        )
    }

    private fun markOutliersAgainstQuality(
        stats: MutableList<StatQuality>,
        commonQualityMin: Int,
        commonQualityMax: Int
    ) {

        for (stat in stats) {

            /*
             * A stat is valid if it supports at least one
             * quality value inside the final common range.
             */
            val isMatch =
                stat.exactQualityCandidates.any { quality ->
                    quality in commonQualityMin..commonQualityMax
                }

            stat.outlier =
                !isMatch

            if (stat.outlier) {

                Log.d(
                    TAG,
                    "OUTLIER: ${stat.statName} " +
                            "actual=${stat.actualValue} " +
                            "does not match " +
                            "${commonQualityMin}-${commonQualityMax}%"
                )

            } else {

                Log.d(
                    TAG,
                    "QUALITY MATCH: ${stat.statName} " +
                            "actual=${stat.actualValue} " +
                            "matches " +
                            "${commonQualityMin}-${commonQualityMax}%"
                )
            }
        }
    }


}