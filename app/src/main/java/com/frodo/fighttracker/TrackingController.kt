package com.frodo.fighttracker

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /**
     * Stops tracking, saves the run to history and resets FightState.
     * Can be called from anywhere (MainActivity, overlay, ...).
     *
     * closeOverlay = true  -> also stops the stats overlay
     * closeOverlay = false -> overlay stays on screen (idle, with Start button)
     */
    fun stopAndSave(context: Context, closeOverlay: Boolean) {

        val app = context.applicationContext

        app.stopService(Intent(app, FightTrackerService::class.java))

        if (closeOverlay) {
            app.stopService(Intent(app, OverlayService::class.java))
        }

        val endTime = System.currentTimeMillis()

        val durationMillis = endTime - FightState.startTime

        val seconds = durationMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        val duration =
            String.format(
                "%02d:%02d:%02d",
                hours,
                minutes % 60,
                seconds % 60
            )

        val date =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            ).format(Date())

        val runid = System.currentTimeMillis().toString()

        RunStorage.saveRun(
            app,
            RunRecord(
                runid,
                date,
                duration,
                FightState.gold,
                FightState.orns,
                FightState.exp,
                FightState.shards,
                "",
                FightState.proofs.toMap()
            )
        )

        FightState.gold = 0L
        FightState.orns = 0L
        FightState.exp = 0L
        FightState.shards = 0L
        FightState.proofs.clear()

        isRunning = false

        // keeps the Tracker tab's STOP button in sync
        recording = 0
    }
}