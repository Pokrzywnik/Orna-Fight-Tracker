package com.frodo.fighttracker

object TrackingController {

    var isRunning = false
    var startTime = 0L

    fun start() {
        isRunning = true
        startTime = System.currentTimeMillis()
    }

    fun stop(): Long {
        isRunning = false
        return System.currentTimeMillis() - startTime
    }
}