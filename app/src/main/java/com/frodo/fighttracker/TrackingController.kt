package com.frodo.fighttracker

object TrackingController {

    var isRunning = false
    var startTime = 0L

    fun start() {
        isRunning = true
        startTime = System.currentTimeMillis()
        FightState.ornsConfirmationThreshold = 3_000_000L
    }

    fun stop(): Long {
        isRunning = false
        return System.currentTimeMillis() - startTime
    }
}