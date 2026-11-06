package com.frodo.fighttracker

/** The reading delay is restarted whenever a step becomes visible again. */
internal class TutorialProgress(private val stepCount: Int, restoredStep: Int = 0) {
    companion object {
        const val READING_DELAY_MS = 1_500L
    }

    var step = restoredStep.coerceIn(0, stepCount - 1)
        private set
    private var readyAt = Long.MAX_VALUE

    val isFinished: Boolean get() = step >= stepCount

    fun markVisible(now: Long) {
        readyAt = now + READING_DELAY_MS
    }

    fun pause() {
        readyAt = Long.MAX_VALUE
    }

    fun canAdvance(now: Long): Boolean = !isFinished && now >= readyAt

    fun advance(now: Long): Boolean {
        if (!canAdvance(now)) return false
        step++
        pause()
        return true
    }
}
