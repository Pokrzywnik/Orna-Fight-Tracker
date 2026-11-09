package com.frodo.fighttracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GleamingSearchTest {

    private val fafnir = GleamingMob("fafnir", "Fafnir", 10, "img/bosses/arisen_fafnir.png")

    @Test
    fun arisenVariantMatchesPlainAndPrefixedNames() {
        assertTrue(GleamingSearch.matches(fafnir, "fafnir"))
        assertTrue(GleamingSearch.matches(fafnir, "arisen"))
        assertTrue(GleamingSearch.matches(fafnir, "Arisen Fafnir"))
        assertTrue(GleamingSearch.matches(fafnir, "  arisen   fafnir "))
    }

    @Test
    fun arisenVariantWithoutNameOrSpriteMetadataStillMatches() {
        val arachne = GleamingMob(
            "arachne-e15f8316", "Arachne", 9, "img/bosses/arachne.png"
        )
        assertTrue(GleamingSearch.matches(arachne, "arisen"))
        assertTrue(GleamingSearch.matches(arachne, "arisen arachne"))
        assertTrue(GleamingSearch.matches(arachne, "arachne"))
    }

    @Test
    fun arisenAliasIsNotAddedToOrdinaryMobs() {
        val rat = GleamingMob("rat", "Rat", 1, "img/monsters/rat.png")
        assertTrue(GleamingSearch.matches(rat, "rat"))
        assertFalse(GleamingSearch.matches(rat, "arisen"))
        assertFalse(GleamingSearch.matches(rat, "arisen rat"))

        // Reusing an *_arisen sprite does not make Fey variants Arisen mobs.
        val fey = GleamingMob(
            "fey-bloodwarg", "Fey Bloodwarg", 10, "img/monsters/bloodwarg_arisen.png"
        )
        assertFalse(GleamingSearch.matches(fey, "arisen"))
    }
}
