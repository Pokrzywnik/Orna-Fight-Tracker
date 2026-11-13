package com.frodo.fighttracker

enum class RewardOcrMode(val value: Int) {
    LEGACY(0),
    DEFAULT(1),
    EXPERIMENTAL(2),
    WIP(3);

    companion object {
        fun fromValue(value: Int): RewardOcrMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
