package com.frodo.fighttracker

import android.content.Context
import java.util.Locale

/** Strict, geometry-aware parser used only by the WIP reward OCR mode. */
object WipRewardTextParser {

    private val ORNS = Regex("""(?<![a-z])(?:orns?|ornów|ornow|ornos)(?![a-z])""")
    private val GOLD = Regex("""(?<![a-z])(?:gold|ouro|oro|or|złoto|zloto|guld|goud)(?![a-z])""")
    private val XP = Regex(
        """(?:xp|exp)(?![a-z])|doświadczenia|doswiadczenia|""" +
            """experi[eê]ncia|experiencia|expérience|experience|erfahrung"""
    )
    private val NUMBER_RUN = Regex("""[0-9][0-9\s,.'’\u00a0\u202f]*""")
    private val SEPARATOR = Regex("""[\s,.'’\u00a0\u202f]+""")

    fun parse(context: Context, scan: RewardOcrEngine.ScanResult): RewardTextParser.Result {
        val baseline = RewardTextParser.parse(context, scan, trustedBand = true)

        // Parse the detector boxes, not concatenated visual rows. Reward icons
        // are separate boxes and must never become a leading amount digit.
        val sequence = coreSequence(scan.boxes)
        val xp = sequence?.first
        val gold = sequence?.second
        val orns = sequence?.third
        val orderOk = sequence != null

        val parsed = RewardTextParser.Parsed(
            gold = gold?.second ?: 0L,
            orns = orns?.second ?: 0L,
            exp = xp?.second ?: 0L,
            shards = baseline.parsed.shards,
            proofs = baseline.parsed.proofs
        )

        return RewardTextParser.Result(
            parsed = parsed,
            rewardScreen = orderOk || baseline.rewardScreen,
            solid = orderOk,
            goldRow = gold?.first,
            ornsRow = orns?.first,
            xpRow = xp?.first,
            shardsRow = baseline.shardsRow
        )
    }

    private data class Sequence(
        val first: Pair<RewardOcrEngine.OcrBox, Long>,
        val second: Pair<RewardOcrEngine.OcrBox, Long>,
        val third: Pair<RewardOcrEngine.OcrBox, Long>
    )

    private fun coreSequence(boxes: List<RewardOcrEngine.OcrBox>): Sequence? {
        val xp = findAll(boxes, XP, listOf("guild", "kingdom", "reino", "królest"))
        val gold = findAll(boxes, GOLD, listOf("kingdom", "reino", "królest"))
        val orns = findAll(boxes, ORNS, emptyList())
        return bestSequence(xp, gold, orns)
    }

    /** Used during progressive OCR; proof/key boxes are above this core block. */
    fun hasCompleteCore(boxes: List<RewardOcrEngine.OcrBox>): Boolean =
        coreSequence(boxes) != null

    internal fun parseCoreValues(
        boxes: List<RewardOcrEngine.OcrBox>
    ): Triple<Long, Long, Long>? = coreSequence(boxes)?.let {
        Triple(it.first.second, it.second.second, it.third.second)
    }

    private fun findAll(
        boxes: List<RewardOcrEngine.OcrBox>,
        label: Regex,
        excluded: List<String>
    ): List<Pair<RewardOcrEngine.OcrBox, Long>> = buildList {
        for (box in boxes) {
            val text = normalizeLabels(box.text)
            if (excluded.any(text::contains)) continue
            val match = label.find(text) ?: continue
            val before = text.substring(0, match.range.first)
            val ownRun = NUMBER_RUN.findAll(before).lastOrNull()?.value
            val value = ownRun?.let(::parseLocalizedNumber)
                ?: neighbourNumber(box, boxes)
                ?: continue
            if (value <= 0L) continue
            add(box to value)
        }
    }

    /** Nearest numeric-only box on the same row, immediately left of a label. */
    private fun neighbourNumber(
        label: RewardOcrEngine.OcrBox,
        boxes: List<RewardOcrEngine.OcrBox>
    ): Long? = boxes.asSequence()
        .filter { it !== label }
        .filter { candidate ->
            val overlap = minOf(candidate.bottom, label.bottom) - maxOf(candidate.top, label.top)
            val gap = label.left - candidate.right
            overlap >= minOf(candidate.height, label.height) * 0.5f &&
                gap in -4..(label.height * 3)
        }
        .mapNotNull { candidate ->
            val text = candidate.text.trim()
            if (text.any(Char::isLetter)) null
            else NUMBER_RUN.find(text)?.value?.let(::parseLocalizedNumber)?.let { candidate to it }
        }
        .maxByOrNull { it.first.right }
        ?.second

    /**
     * Chooses a DPI-independent XP -> Gold -> Orns block. The first valid XP
     * wins, because event XP (Ragnarok, Cursed Queens, etc.) is shown below
     * player/party XP and immediately above Gold.
     */
    private fun bestSequence(
        xp: List<Pair<RewardOcrEngine.OcrBox, Long>>,
        gold: List<Pair<RewardOcrEngine.OcrBox, Long>>,
        orns: List<Pair<RewardOcrEngine.OcrBox, Long>>
    ): Sequence? {
        var best: Sequence? = null
        var bestXpTop = Int.MAX_VALUE
        var bestSpan = Int.MAX_VALUE
        for (x in xp) for (g in gold) for (o in orns) {
            if (x.first.top >= g.first.top || g.first.top >= o.first.top) continue
            val averageHeight = (x.first.height + g.first.height + o.first.height) / 3f
            val span = o.first.bottom - x.first.top
            // Guild/event XP rows can sit between player XP and Gold, so the
            // valid block may span considerably more than three text heights.
            if (span > averageHeight * 20f) continue
            if (x.first.top > bestXpTop ||
                (x.first.top == bestXpTop && span >= bestSpan)) continue
            best = Sequence(x, g, o)
            bestXpTop = x.first.top
            bestSpan = span
        }
        return best
    }

    /**
     * Accepts ungrouped digits and locale grouping with spaces, commas, dots
     * or apostrophes. Starting from the right deliberately drops a detached
     * icon digit: "8 12 294 201" becomes 12,294,201, not 812,294,201.
     */
    internal fun parseLocalizedNumber(raw: String): Long? {
        val value = raw.trim().trim(',', '.', '\'', '’')
        if (value.isEmpty()) return null

        val groups = value.split(SEPARATOR).filter(String::isNotEmpty)
        if (groups.isEmpty() || groups.any { it.any { c -> !c.isDigit() } }) return null

        if (groups.size == 1) {
            val digits = groups[0]
            if (digits.length !in 1..12) return null
            return digits.toLongOrNull()
        }

        var start = groups.lastIndex
        while (start > 0 && groups[start].length == 3 && groups[start - 1].length == 3) {
            start--
        }
        if (groups[start].length == 3 && start > 0 && groups[start - 1].length in 1..3) {
            start--
        }

        val selected = groups.subList(start, groups.size)
        if (selected.first().length !in 1..3 || selected.drop(1).any { it.length != 3 }) {
            // OCR can drop one separator or turn spaces into a single dot
            // ("7 423 987 052" -> "7423.987052"). The WIP white-text mask
            // already removes icons/background, so concatenating the remaining
            // digits is safe and recovers the intended Endless value.
            val fallback = groups.joinToString("")
            if (fallback.length !in 1..12) return null
            return fallback.toLongOrNull()
        }
        val digits = selected.joinToString("")
        if (digits.length > 12) return null
        return digits.toLongOrNull()
    }

    private fun normalizeLabels(value: String): String =
        value.lowercase(Locale.ROOT)
            // Paddle sometimes reads the final zero before "party XP" as o.
            .replace(Regex("(?<=\\d)o(?=part(?:y|v)?(?:xp)?)"), "0")
            .replace("0rns", "orns")
            .replace("0rn5", "orns")
            .replace("orn5", "orns")
            .replace("g0ld", "gold")

}
