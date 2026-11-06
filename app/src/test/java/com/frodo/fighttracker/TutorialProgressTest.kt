package com.frodo.fighttracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialProgressTest {
    @Test
    fun eachStepMustBeVisibleForAtLeastOneAndAHalfSeconds() {
        val progress = TutorialProgress(2)
        assertFalse(progress.advance(10_000L))
        progress.markVisible(10_000L)
        assertFalse(progress.advance(11_499L))
        assertEquals(0, progress.step)
        assertTrue(progress.advance(11_500L))
        assertEquals(1, progress.step)
        assertFalse(progress.advance(11_501L))
        progress.markVisible(12_000L)
        assertFalse(progress.advance(13_499L))
        assertTrue(progress.advance(13_500L))
        assertTrue(progress.isFinished)
        assertFalse(progress.advance(20_000L))
    }

    @Test
    fun timeInTheBackgroundDoesNotUnlockTheStep() {
        val progress = TutorialProgress(10)
        progress.markVisible(1_000L)
        progress.pause()
        assertFalse(progress.advance(50_000L))
        progress.markVisible(50_000L)
        assertFalse(progress.advance(51_499L))
        assertTrue(progress.advance(51_500L))
    }

    @Test
    fun restoredStepRemainsLockedUntilItIsShownAgain() {
        val progress = TutorialProgress(10, restoredStep = 7)
        assertEquals(7, progress.step)
        assertFalse(progress.advance(100_000L))
        progress.markVisible(100_000L)
        assertTrue(progress.advance(101_500L))
        assertEquals(8, progress.step)
    }
}
