package com.frodo.fighttracker

object FightState {
    var gold: Long = 0L
    var orns: Long = 0L
    var exp: Long = 0L
    var shards: Long = 0L

    /** proof id (see ProofTracking) -> total count in current run */
    val proofs: MutableMap<String, Long> = mutableMapOf()
    var lastProofs: Map<String, Long> = emptyMap()

    var startTime: Long = 0L
    var lastGold = 0L
    var lastOrns = 0L
    var lastExp = 0L
    var lastShards = 0L
    var ornsConfirmationThreshold = 3_000_000L
}