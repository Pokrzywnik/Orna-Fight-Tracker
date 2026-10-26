package com.frodo.fighttracker

import android.content.Context

/**
 * What the tracker counts besides gold / orns / exp / shards.
 * Any combination of modes can be enabled at the same time.
 */
object ProofTracking {

    enum class Mode(val key: String, val label: String) {
        ANGUISH("anguish", "Anguish proofs"),
        KINGS_GUARD("kings_guard", "King's guard proofs"),
        SKELETON_KEY("skeleton_key", "Skeleton key");

        companion object {
            fun fromKey(key: String?): Mode? =
                values().firstOrNull { it.key == key }
        }
    }

    /**
     * pattern = regex matched against OCR text that is
     * lowercased and has all whitespace collapsed to single spaces
     * (so text wrapped to two lines still matches).
     */
    data class ProofType(
        val id: String,
        val label: String,
        val pattern: String,
        val iconUrl: String
    )

    private const val PREF = "settings"

    private const val KEY_OVERLAY_MODES = "proof_overlay_modes"

    /** old single-choice setting (kept only for migration) */
    private const val KEY_MODE = "proof_mode"

    /** new multi-choice setting */
    private const val KEY_MODES = "proof_modes"

    private const val ICON_BASE =
        "https://playorna.com/static/img/useables/"

    // "pro[o0]f" tolerates a typical OCR 0/o mix-up
    private fun proof(
        id: String,
        label: String,
        namePattern: String,
        icon: String
    ) = ProofType(
        id = id,
        label = label,
        pattern = "pro[o0]f of $namePattern",
        iconUrl = ICON_BASE + icon
    )

    private val anguish =
        proof("anguish", "Proof of Anguish", "anguish", "proof_anguish.png")

    private val despair =
        proof("despair", "Proof of Despair", "despair", "proof_despair.png")

    private val melancholy =
        proof("melancholy", "Proof of Melancholy", "melancholy", "proof_melancholy.png")

    private val agony =
        proof("agony", "Proof of Agony", "agony", "proof_agony.png")

    private val torment =
        proof("torment", "Proof of Torment", "torment", "proof_torment.png")

    private val beast =
        proof("beast", "Proof of Beast Slain", """beast\s+slain""", "proof_guard1.png")

    private val rarity =
        proof("rarity", "Proof of Rarity Slain", """rarity\s+slain""", "proof_guard3.png")

    private val arisen =
        proof("arisen", "Proof of Arisen Slain", """arisen\s+slain""", "proof_guard2.png")

    private val skeletonKey =
        ProofType(
            id = "key",
            label = "Skeleton key",
            pattern = """skeleton\s+keys?""",
            iconUrl = ICON_BASE + "gauntlet_key.png"
        )

    /** Every proof type (used for history, independent of current selection). */
    val allProofs: List<ProofType> =
        listOf(
            anguish,
            despair,
            melancholy,
            agony,
            torment,
            beast,
            rarity,
            arisen,
            skeletonKey
        )

    /**
     * Currently enabled modes.
     *
     * Default (nothing saved yet): Anguish only.
     * An old single-choice value is migrated automatically.
     */
    fun getModes(context: Context): Set<Mode> {

        val prefs =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val saved =
            prefs.getStringSet(KEY_MODES, null)

        if (saved != null) {

            return Mode.values()
                .filter { it.key in saved }
                .toSet()
        }

        // migration from the old single-choice setting
        val legacy =
            Mode.fromKey(prefs.getString(KEY_MODE, null))

        return setOf(legacy ?: Mode.ANGUISH)
    }

    fun setModes(context: Context, modes: Set<Mode>) {

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(
                KEY_MODES,
                modes.map { it.key }.toSet()
            )
            .apply()
    }

    /** Short text for the button. */
    fun modesLabel(context: Context): String {

        val modes = getModes(context)

        return if (modes.isEmpty())
            "nothing"
        else
            Mode.values()
                .filter { it in modes }
                .joinToString(", ") { it.label }
    }

    /**
     * Proofs counted right now: union of all enabled modes.
     * (Anguish depends on the Anguish version from settings.)
     */
    private fun proofsFor(mode: Mode, context: Context): List<ProofType> =
        when (mode) {
            Mode.ANGUISH ->
                if (SettingsStore.getAnguishVersion(context) == "2.0")
                    listOf(despair, melancholy, agony, torment)
                else
                    listOf(anguish)
            Mode.KINGS_GUARD -> listOf(beast, rarity, arisen)
            Mode.SKELETON_KEY -> listOf(skeletonKey)
        }

    /** Proofs counted right now: union of all enabled modes. */
    fun activeProofs(context: Context): List<ProofType> {
        val modes = getModes(context)
        return Mode.values()
            .filter { it in modes }
            .flatMap { proofsFor(it, context) }
    }

    /** Modes whose proofs are shown in the floating overlay (default: all). */
    fun getOverlayModes(context: Context): Set<Mode> {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_OVERLAY_MODES, null)

        return if (saved != null)
            Mode.values().filter { it.key in saved }.toSet()
        else
            Mode.values().toSet()
    }

    fun setOverlayModes(context: Context, modes: Set<Mode>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_OVERLAY_MODES, modes.map { it.key }.toSet())
            .apply()
    }

    /** Tracked AND switched on for the overlay. */
    fun overlayProofs(context: Context): List<ProofType> {
        val tracked = getModes(context)
        val overlay = getOverlayModes(context)
        return Mode.values()
            .filter { it in tracked && it in overlay }
            .flatMap { proofsFor(it, context) }
    }
}