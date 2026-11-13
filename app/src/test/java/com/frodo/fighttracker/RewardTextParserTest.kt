package com.frodo.fighttracker

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardTextParserTest {

    @Test
    fun normalizesCommonProofOcrMistakes() {
        assertEquals(
            "proofofbeastslain(6)",
            RewardTextParser.normalizeProofText("ProotofBeastSlain(6)")
        )
        assertEquals(
            "proofofrarityslain(5)",
            RewardTextParser.normalizeProofText("Pro0fofRaritySlain(5)")
        )
    }
}
