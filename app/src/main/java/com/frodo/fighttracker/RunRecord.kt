package com.frodo.fighttracker

import androidx.annotation.StringRes

/**
 * What kind of content a run was. Stored as [key] in [RunRecord.type].
 * A run without a type (all runs saved before this field existed, or runs
 * whose type could not be inferred) is shown as "Other" and appears only
 * under the "All" filter.
 */
enum class RunType(val key: String, @StringRes val labelRes: Int) {

    ANGUISH("anguish", R.string.ft_chip_anguish),
    TOWERS("towers", R.string.ft_chip_towers),
    CORAL("coral", R.string.ft_chip_coral),
    GUARD("guard", R.string.ft_chip_guard),
    REMEMBRANCE("remembrance", R.string.ft_chip_remembrance);

    companion object {

        fun fromKey(key: String?): RunType? =
            values().firstOrNull { it.key == key }

        /**
         * Best guess made when a run is stopped:
         *  - Anguish proofs        -> Anguish
         *  - King's guard proofs   -> Guard
         *  - Skeleton keys         -> Coral
         *  - Tower shards          -> Towers
         * When several are present the one with the biggest count wins.
         * Remembrance (and anything else) cannot be detected and stays
         * null until the user picks a type in the rename dialog.
         */
        fun infer(proofs: Map<String, Long>, shards: Long): RunType? {

            fun total(vararg ids: String): Long =
                ids.sumOf { proofs[it] ?: 0L }

            val scores = listOf(
                ANGUISH to total("anguish", "despair", "melancholy", "agony", "torment"),
                GUARD to total("beast", "rarity", "arisen"),
                CORAL to total("key"),
                TOWERS to shards
            )

            val best = scores.maxByOrNull { it.second } ?: return null

            return if (best.second > 0L) best.first else null
        }
    }
}

data class RunRecord(
    val runid: String,
    val date: String,
    val duration: String,
    val gold: Long,
    val orns: Long,
    val exp: Long,
    val shards: Long,
    val name: String = "",
    val proofs: Map<String, Long> = emptyMap(),
    /** [RunType.key] or null (= "Other"). */
    val type: String? = null
)
