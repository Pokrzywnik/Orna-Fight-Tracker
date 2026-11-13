package com.frodo.fighttracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WipRewardTextParserTest {

    private fun box(text: String, top: Int, left: Int = 100) =
        RewardOcrEngine.OcrBox(text, left, top, 500, top + 30)

    @Test
    fun acceptsCommonLocaleSeparators() {
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("12 294 201"))
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("12,294,201"))
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("12.294.201"))
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("12'294'201"))
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("12294201"))
    }

    @Test
    fun dropsDetachedIconDigitBeforeGroupedValue() {
        assertEquals(12_294_201L, WipRewardTextParser.parseLocalizedNumber("8 12 294 201"))
    }

    @Test
    fun rejectsOversizedValues() {
        assertNull(WipRewardTextParser.parseLocalizedNumber("1234567890123"))
        assertEquals(4_906_865_132L, WipRewardTextParser.parseLocalizedNumber("4906865132"))
    }

    @Test
    fun choosesPlayerXpAndExcludesEventAndKingdomRows() {
        val values = WipRewardTextParser.parseCoreValues(
            listOf(
                box("3,179,212 party XP", 100),
                box("3 The Cursed Queens XP", 140),
                box("81,384 gold", 180),
                box("18 kingdom gold", 220),
                box("738 orns", 260)
            )
        )
        assertEquals(Triple(3_179_212L, 81_384L, 738L), values)
    }

    @Test
    fun supportsPortugueseAndSpaceGrouping() {
        val portuguese = WipRewardTextParser.parseCoreValues(
            listOf(
                box("50.249.150 party XP", 100),
                box("1.369.246 ouro", 140),
                box("78 ouro do reino", 180),
                box("7.156 orns", 220)
            )
        )
        assertEquals(Triple(50_249_150L, 1_369_246L, 7_156L), portuguese)

        val spaces = WipRewardTextParser.parseCoreValues(
            listOf(
                box("11 154 840 party XP", 100),
                box("14 497 408 gold", 140),
                box("59 384 orns", 180)
            )
        )
        assertEquals(Triple(11_154_840L, 14_497_408L, 59_384L), spaces)
    }

    @Test
    fun keepsPlayerXpAboveGuildAndEventRows() {
        val values = WipRewardTextParser.parseCoreValues(
            listOf(
                box("1,735,648 XP", 100),
                box("4 guild XP", 145),
                box("4 The Crimson Festival XP", 190),
                box("109,814 gold", 235),
                box("581 orns", 280)
            )
        )
        assertEquals(Triple(1_735_648L, 109_814L, 581L), values)
    }

    @Test
    fun repairsZeroReadAsLetterBeforePartyXp() {
        val values = WipRewardTextParser.parseCoreValues(
            listOf(
                box("6.297.08opartvXP", 100),
                box("27 guild XP", 145),
                box("3 The Hallowed XP", 190),
                box("33.169.893 gold", 235),
                box("127.366 orns", 280)
            )
        )
        assertEquals(Triple(6_297_080L, 33_169_893L, 127_366L), values)
    }

    @Test
    fun ignoresSeparateIconBoxThatLooksLikeADigit() {
        val values = WipRewardTextParser.parseCoreValues(
            listOf(
                box("8", 100, left = 40),
                box("175,284 party XP", 100),
                box("6", 140, left = 40),
                box("1,426,959 gold", 140),
                box("8", 180, left = 40),
                box("6,203 orns", 180)
            )
        )
        assertEquals(Triple(175_284L, 1_426_959L, 6_203L), values)
    }

    @Test
    fun acceptsEndlessRewardsEvenWhenOcrDropsSeparators() {
        val values = WipRewardTextParser.parseCoreValues(
            listOf(
                box("4906865132XP", 100),
                box("2104615332g0ld", 140),
                box("6639739orns", 180)
            )
        )
        assertEquals(Triple(4_906_865_132L, 2_104_615_332L, 6_639_739L), values)
        assertEquals(7_423_987_052L, WipRewardTextParser.parseLocalizedNumber("7423.987052"))
    }
}
