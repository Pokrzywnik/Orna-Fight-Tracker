package com.frodo.fighttracker

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns PaddleOCR boxes into rewards.
 *
 *  1. Only the reward band counts: below the "Here's what you found" header
 *     (or VICTORY!) and above the CONTINUE button. With trustedBand = true
 *     (scan = user crop that starts below the header) the whole image counts.
 *
 *  2. Numbers are read ONLY from the box that contains the keyword
 *     ("1275gold", "25orns"); neighbouring boxes are never glued on.
 *
 *  3. XP = the "party XP" row (or a plain "XP" row on screens without a party).
 *     "guild XP" and event XP rows are NOT counted.
 *
 *  4. Proofs: a name box ("Proof of Beast") is joined with the "Slain(3)"
 *     box directly below / next to it (3-column grid layout).
 *
 *  5. result.solid = xp + gold + orns found (and header found, or the
 *     crop is trusted and the rows are in the game's order).
 */
object RewardTextParser {

    data class Parsed(
        val gold: Long,
        val orns: Long,
        val exp: Long,
        val shards: Long,
        val proofs: Map<String, Long>
    ) {
        fun isEmpty(): Boolean =
            gold == 0L &&
                    orns == 0L &&
                    exp == 0L &&
                    shards == 0L &&
                    proofs.isEmpty()
    }

    data class Result(
        val parsed: Parsed,
        val rewardScreen: Boolean,
        val solid: Boolean,
        val goldRow: RewardOcrEngine.OcrBox?,
        val ornsRow: RewardOcrEngine.OcrBox?,
        val xpRow: RewardOcrEngine.OcrBox?,
        val shardsRow: RewardOcrEngine.OcrBox?
    )

    private val ORNS =
        Regex("""(?<![a-z])orns?(?![a-z])|ornów|ornow|ornos""")

    private val GOLD =
        Regex("""gold|ouro|zlota|złota|złtota|zloto|złoto""")

    private val GOLD_EXCLUDED =
        listOf("kingdom", "reino", "królestwa")

    private val XP =
        Regex("""xp(?![a-z])|doświadczenia|drużynowego""")

    private val SHARDS =
        Regex("""tower\s*shards?|fragmentos\s*da\s*torre|fragmenty\s*wie[żzź]""")

    private const val HEADER = "hereswhatyoufound"

    private val VICTORY =
        listOf("victory", "zwycięstwo", "zwcięstwo", "vitória", "vitoria")

    private val CONTINUE =
        listOf("continue", "kontynuuj", "continuar")

    /** plain number, or thousands grouped ("1 275" / "1.275") */
    private const val NUM = """\d{1,3}(?:[ .]\d{3})+|\d+"""

    private val NUMBER_BEFORE = Regex("""($NUM)\s*$""")

    private val DIGITS_ONLY = Regex("""^\D{0,2}($NUM)\D{0,1}$""")

    /** "(3)", "[3]", "3)" after a proof name. */
    private const val PROOF_COUNT =
        """(?:[(\[{]\s*(\d{1,4})\s*[)\]}]?|(\d{1,4})\s*[)\]}])?"""

    // ---------------------------------------------------------------

    /**
     * trustedBand = true when the scanned image is the user's crop that
     * starts BELOW the "Here's what you found" header.
     */
    fun parse(
        context: Context,
        scan: RewardOcrEngine.ScanResult,
        trustedBand: Boolean = false
    ): Result {

        val boxes = scan.boxes

        val header =
            boxes
                .filter { fuzzy(it, listOf(HEADER), 4) }
                .maxByOrNull { it.top }

        val victory =
            boxes
                .filter { fuzzy(it, VICTORY, 2) }
                .minByOrNull { it.top }

        val button =
            boxes
                .filter { fuzzy(it, CONTINUE, 2) }
                .maxByOrNull { it.top }

        val anchorBottom: Int? =
            header?.bottom ?: victory?.bottom

        val anchored = anchorBottom != null || trustedBand

        val region =
            boxes.filter { b ->

                (anchorBottom == null || b.centerY > anchorBottom) &&
                        (button == null || b.centerY < button.top)
            }

        val gold = find(region, GOLD, GOLD_EXCLUDED, anchored)
        val orns = find(region, ORNS, emptyList(), anchored)
        val xp = findXp(region, anchored)
        val shards = find(region, SHARDS, emptyList(), anchored)

        val proofs = parseProofs(context, region)

        val hasXpAndCurrency =
            xp != null && (gold != null || orns != null)

        val anyCurrency =
            xp != null || gold != null || orns != null

        // real evidence only (a trusted crop alone proves nothing)
        val rewardScreen =
            anchorBottom != null ||
                    hasXpAndCurrency ||
                    (button != null && anyCurrency) ||
                    (trustedBand && anyCurrency)

        // without a visible header also require the game's row order
        val orderOk =
            !trustedBand ||
                    (xp != null && gold != null && orns != null &&
                            xp.first.top < gold.first.top &&
                            gold.first.top < orns.first.top)

        val solid =
            anchored &&
                    xp != null &&
                    gold != null &&
                    orns != null &&
                    orderOk

        return Result(
            parsed = Parsed(
                gold = gold?.second ?: 0L,
                orns = orns?.second ?: 0L,
                exp = xp?.second ?: 0L,
                shards = shards?.second ?: 0L,
                proofs = proofs
            ),
            rewardScreen = rewardScreen,
            solid = solid,
            goldRow = gold?.first,
            ornsRow = orns?.first,
            xpRow = xp?.first,
            shardsRow = shards?.first
        )
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    /** subscript / superscript digits ("Slain(₃)") -> ASCII digits */
    private fun asciiDigits(s: String): String {

        val sb = StringBuilder(s.length)

        for (c in s) {
            sb.append(
                when (c) {
                    in '₀'..'₉' -> '0' + (c - '₀')
                    in '⁴'..'⁹' -> '4' + (c - '⁴')
                    '⁰' -> '0'
                    '¹' -> '1'
                    '²' -> '2'
                    '³' -> '3'
                    else -> c
                }
            )
        }

        return sb.toString()
    }

    /** lowercase, separators removed, typical 0/o OCR confusions fixed */
    private fun clean(text: String): String =
        asciiDigits(text)
            .lowercase()
            .replace(",", "")
            .replace("0rns", "orns")
            .replace("0rn5", "orns")
            .replace("orn5", "orns")
            .replace("0uro", "ouro")

    private fun squash(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private fun fuzzy(
        box: RewardOcrEngine.OcrBox,
        targets: List<String>,
        maxDist: Int
    ): Boolean {

        val s = squash(box.text)

        if (s.length < 4) return false

        return targets.any { t ->
            abs(s.length - t.length) <= maxDist + 2 &&
                    levenshtein(s, t) <= maxDist
        }
    }

    private fun levenshtein(a: String, b: String): Int {

        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)

        for (i in 1..a.length) {

            cur[0] = i

            for (j in 1..b.length) {

                val cost = if (a[i - 1] == b[j - 1]) 0 else 1

                cur[j] =
                    min(
                        min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                    )
            }

            val tmp = prev
            prev = cur
            cur = tmp
        }

        return prev[b.length]
    }

    /**
     * First box (top-most when anchored, bottom-most otherwise) that has
     * the keyword WITH its own number in front of it.
     */
    private fun find(
        region: List<RewardOcrEngine.OcrBox>,
        label: Regex,
        excluded: List<String>,
        anchored: Boolean
    ): Pair<RewardOcrEngine.OcrBox, Long>? {

        val ordered =
            if (anchored)
                region.sortedBy { it.top }
            else
                region.sortedByDescending { it.top }

        for (box in ordered) {

            val t = clean(box.text)

            if (excluded.any { t.contains(it) }) {
                continue
            }

            for (match in label.findAll(t)) {

                val before =
                    t.substring(0, match.range.first)

                // "150714.gold" -> junk between number and keyword is ignored
                var value =
                    toNumber(
                        NUMBER_BEFORE
                            .find(before.trimEnd { !it.isLetterOrDigit() })
                            ?.groupValues
                            ?.get(1)
                    )

                // keyword alone in its box, number in a separate box on its left
                if (
                    value == null &&
                    anchored &&
                    before.none { it.isDigit() } &&
                    squash(t).length <= 14
                ) {
                    value = neighbourNumber(box, region)
                }

                if (value != null && value > 0L) {
                    return Pair(box, value)
                }
            }
        }

        return null
    }

    /**
     * Player XP.
     *
     *   "34,646,287 party XP"        -> counted (preferred)
     *   "79830 XP"                   -> counted when there is no party row
     *   "2 guild XP", "9 <event> XP" -> ignored
     */
    private fun findXp(
        region: List<RewardOcrEngine.OcrBox>,
        anchored: Boolean
    ): Pair<RewardOcrEngine.OcrBox, Long>? {

        val ordered =
            if (anchored)
                region.sortedBy { it.top }
            else
                region.sortedByDescending { it.top }

        var plain: Pair<RewardOcrEngine.OcrBox, Long>? = null

        for (box in ordered) {

            val t = clean(box.text)

            val m = XP.find(t) ?: continue

            val before =
                t.substring(0, m.range.first)
                    .trimEnd { !it.isLetterOrDigit() }

            if (before.endsWith("party")) {

                val numberPart =
                    before.removeSuffix("party")
                        .trimEnd { !it.isLetterOrDigit() }

                val v =
                    toNumber(
                        NUMBER_BEFORE.find(numberPart)?.groupValues?.get(1)
                    )

                if (v != null && v > 0L) {
                    return Pair(box, v)
                }

                continue
            }

            val v =
                when {

                    before.lastOrNull()?.isDigit() == true ->
                        toNumber(
                            NUMBER_BEFORE.find(before)?.groupValues?.get(1)
                        )

                    anchored && before.isEmpty() && squash(t).length <= 14 ->
                        neighbourNumber(box, region)

                    // ends with letters: guild / event xp
                    else -> null
                }

            if (v != null && v > 0L && plain == null) {
                plain = Pair(box, v)
            }
        }

        return plain
    }

    /** digits-only box on the same row, directly to the left of [box] */
    private fun neighbourNumber(
        box: RewardOcrEngine.OcrBox,
        candidates: List<RewardOcrEngine.OcrBox>
    ): Long? {

        var best: RewardOcrEngine.OcrBox? = null

        for (n in candidates) {

            if (n === box) continue

            val overlap =
                min(n.bottom, box.bottom) - max(n.top, box.top)

            if (overlap < min(n.height, box.height) * 0.5f) continue

            val gap = box.left - n.right

            if (gap < -4 || gap > box.height * 2) continue

            if (DIGITS_ONLY.find(clean(n.text)) == null) continue

            val current = best

            if (current == null || n.right > current.right) {
                best = n
            }
        }

        val chosen = best ?: return null

        return toNumber(
            DIGITS_ONLY.find(clean(chosen.text))?.groupValues?.get(1)
        )
    }

    private fun toNumber(raw: String?): Long? {

        if (raw == null) return null

        val digits = raw.filter { it.isDigit() }

        if (digits.isEmpty() || digits.length > 12) return null

        return digits.toLongOrNull()
    }

    /**
     * Proofs / keys.
     *
     * The game lays them out in a grid:
     *
     *   ProofofBeast   ProofofArisen   ProofofDespair
     *   Slain(3)       Slain(3)
     *
     * A name box without "slain" is therefore joined with the
     * "Slain(n)" box directly BELOW it (same column) or directly to
     * its RIGHT (same row). Units are separated by "|" so a count can
     * never leak from one proof to the next.
     */
    private fun parseProofs(
        context: Context,
        boxes: List<RewardOcrEngine.OcrBox>
    ): Map<String, Long> {

        val sorted =
            boxes.sortedWith(compareBy({ it.top }, { it.left }))

        val used = BooleanArray(sorted.size)

        val units = mutableListOf<String>()

        for (i in sorted.indices) {

            if (used[i]) continue

            val b = sorted[i]

            var text = asciiDigits(b.text)

            val s = squash(b.text)

            if (s.startsWith("proof") || s.startsWith("skeleton")) {

                // names can wrap over two boxes ("Proofof" / "Melancholy")
                // and the count is a separate box ("Slain(3)", "(2)")
                var last = b
                var joins = 0

                while (joins < 2 && !squash(text).contains("slain")) {

                    var next = -1

                    // 1st choice: box directly BELOW in the same column
                    for (j in i + 1 until sorted.size) {

                        if (used[j]) continue

                        val n = sorted[j]

                        // sorted by top: nothing closer can follow
                        if (n.top - last.bottom > last.height * 1.5) break

                        val ns = squash(n.text)

                        if (
                            ns.isEmpty() ||
                            ns.startsWith("proof") ||
                            ns.startsWith("skeleton")
                        ) continue

                        val cx = (n.left + n.right) / 2

                        if (
                            n.top >= last.bottom - 6 &&
                            cx >= last.left - 12 &&
                            cx <= last.right + 12
                        ) {
                            next = j
                            break
                        }
                    }

                    // 2nd choice: "Slain(n)" / "(n)" directly to the RIGHT, same row
                    if (next < 0) {

                        for (j in i + 1 until sorted.size) {

                            if (used[j]) continue

                            val n = sorted[j]

                            if (n.top - last.bottom > last.height * 1.5) break

                            val ns = squash(n.text)

                            if (
                                (n.text.trim().startsWith("(") ||
                                        ns.startsWith("slain")) &&
                                abs(n.centerY - last.centerY) <= last.height / 2 &&
                                n.left - last.right in -4..40
                            ) {
                                next = j
                                break
                            }
                        }
                    }

                    if (next < 0) break

                    text += asciiDigits(sorted[next].text)
                    used[next] = true
                    last = sorted[next]
                    joins++
                }
            }

            units.add(text)
        }

        val flat =
            units.joinToString("|") { unit ->
                unit.lowercase().filter { !it.isWhitespace() }
            }

        val result =
            mutableMapOf<String, Long>()

        for (proof in ProofTracking.activeProofs(context)) {

            val core =
                proof.pattern
                    .replace("\\s+", "")
                    .replace("\\s*", "")
                    .replace(" ", "")

            val regex =
                Regex(core + PROOF_COUNT)

            var total = 0L

            for (match in regex.findAll(flat)) {

                total +=
                    match.groupValues[1].toLongOrNull()
                        ?: match.groupValues[2].toLongOrNull()
                                ?: 1L
            }

            if (total > 0L) {
                result[proof.id] = total
            }
        }

        return result
    }
}