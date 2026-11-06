package com.frodo.fighttracker

import org.junit.Assert.*
import org.junit.Test

class TrackingControllerTest {
    @Test
    fun consentCanBeCancelledAndRetriedBeforeCaptureActuallyStarts() {
        assertTrue(TrackingController.requestStart())
        assertTrue(TrackingController.isStarting)
        assertFalse(TrackingController.isRunning)
        assertEquals(0, recording)
        assertEquals(0L, FightState.startTime)
        assertFalse(TrackingController.requestStart())

        // Denying consent must not start the timer, save history or block a retry.
        val historyVersion = TrackingController.historyVersion
        TrackingController.cancelStart()
        assertFalse(TrackingController.isStarting)
        assertFalse(TrackingController.isRunning)
        assertEquals(0, recording)
        assertEquals(0L, FightState.startTime)
        assertEquals(historyVersion, TrackingController.historyVersion)

        assertTrue(TrackingController.requestStart())
        TrackingController.start()
        assertFalse(TrackingController.isStarting)
        assertTrue(TrackingController.isRunning)
        assertEquals(1, recording)
        assertTrue(FightState.startTime > 0L)
        assertEquals(TrackingController.startTime, FightState.startTime)
        assertFalse(TrackingController.requestStart())

        // A late cancellation cannot turn an active capture back into idle.
        TrackingController.cancelStart()
        assertTrue(TrackingController.isRunning)
        assertEquals(1, recording)
    }
}
