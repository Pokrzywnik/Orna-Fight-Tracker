package com.frodo.fighttracker

/** Presentation-only discoveries. Never writes to GleamingStore or preferences. */
internal object GleamingTutorialPreview {
    fun discoveries(mobs: List<GleamingMob>, saved: Set<String>, enabled: Boolean): Set<String> {
        if (!enabled) return saved
        return mobs.asSequence()
            .filter { it.tier == 1 }
            .take(3)
            .map { it.id }
            .toSet()
    }
}
