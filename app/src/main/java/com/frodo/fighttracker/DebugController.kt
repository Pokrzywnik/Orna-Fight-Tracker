package com.frodo.fighttracker

object DebugController {

    @Volatile
    var enabled: Boolean = false

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }
}