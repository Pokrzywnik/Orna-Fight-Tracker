package com.frodo.fighttracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GleamingTutorialPreviewTest {
    private val mobs = listOf(
        GleamingMob("t2", "Tier 2 mob", 2, "t2.png"),
        GleamingMob("one", "First", 1, "one.png"),
        GleamingMob("two", "Second", 1, "two.png"),
        GleamingMob("three", "Third", 1, "three.png"),
        GleamingMob("four", "Fourth", 1, "four.png")
    )

    @Test
    fun newUserGetsThreeTemporaryTierOneDiscoveries() {
        val saved = mutableSetOf<String>()
        assertEquals(setOf("one", "two", "three"),
            GleamingTutorialPreview.discoveries(mobs, saved, enabled = true))
        assertTrue(saved.isEmpty())
        assertTrue(GleamingTutorialPreview.discoveries(mobs, saved, enabled = false).isEmpty())
    }

    @Test
    fun tutorialShowsExamplesAndRestoresTheUntouchedRealCollectionAfterward() {
        val saved = mutableSetOf("four", "t2")
        assertEquals(setOf("one", "two", "three"),
            GleamingTutorialPreview.discoveries(mobs, saved, enabled = true))
        assertEquals(setOf("four", "t2"), saved)
        assertEquals(saved, GleamingTutorialPreview.discoveries(mobs, saved, enabled = false))
    }
}
