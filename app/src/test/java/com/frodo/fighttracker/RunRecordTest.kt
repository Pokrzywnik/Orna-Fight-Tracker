package com.frodo.fighttracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecordTest {
    private val emptyRun = RunRecord(
        runid = "test",
        date = "2026-11-06 12:00",
        duration = "00:00:10",
        gold = 0L,
        orns = 0L,
        exp = 0L,
        shards = 0L
    )

    @Test
    fun zeroTotalsAreEmptyRegardlessOfDurationOrMetadata() {
        assertTrue(emptyRun.isEmpty)
        assertTrue(emptyRun.copy(duration = "01:30:00", name = "Named run", type = "towers").isEmpty)
        assertTrue(emptyRun.copy(proofs = mapOf("anguish" to 0L, "key" to 0L)).isEmpty)
    }

    @Test
    fun anyRecordedRewardKeepsTheRun() {
        val runs = listOf(
            emptyRun.copy(gold = 1L),
            emptyRun.copy(orns = 1L),
            emptyRun.copy(exp = 1L),
            emptyRun.copy(shards = 1L),
            emptyRun.copy(proofs = mapOf("anguish" to 0L, "agony" to 1L)),
            emptyRun.copy(proofs = mapOf("beast" to 1L)),
            emptyRun.copy(proofs = mapOf("key" to 1L))
        )
        for (run in runs) assertFalse(run.isEmpty)
    }
}
