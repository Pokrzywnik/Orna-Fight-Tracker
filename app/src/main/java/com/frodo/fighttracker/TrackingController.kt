package com.frodo.fighttracker

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackingController {

    var isRunning = false
        private set
    var isStarting = false
        private set
    var historyVersion = 0L
        private set
    var startTime = 0L
        private set

    fun requestStart(): Boolean {
        if (isRunning || isStarting) return false
        isStarting = true
        return true
    }

    fun cancelStart() {
        isStarting = false
        if (!isRunning) recording = 0
    }

    fun start() {
        if (isRunning) return
        isStarting = false
        isRunning = true
        startTime = System.currentTimeMillis()
        FightState.startTime = startTime
        recording = 1
        FightState.ornsConfirmationThreshold = 3_000_000L
    }

    /**
     * Stops tracking, saves nonempty runs to history and resets FightState.
     * Can be called from anywhere (MainActivity, overlay, ...).
     *
     * closeOverlay = true  -> also stops the stats overlay
     * closeOverlay = false -> overlay stays on screen (idle, with Start button)
     */
    fun stopAndSave(context: Context, closeOverlay: Boolean) {

        val app = context.applicationContext
        // Ignore late OCR callbacks before taking the final snapshot.
        FightTrackerService.instance?.prepareToStop()
        finishSession(app, closeOverlay)
        app.stopService(Intent(app, FightTrackerService::class.java))
    }

    /** Also used when Android destroys the capture service. Safe to call twice. */
    fun finishSession(context: Context, closeOverlay: Boolean) {
        val app = context.applicationContext
        cancelStart()

        if (closeOverlay) {
            app.stopService(Intent(app, OverlayService::class.java))
        }

        if (!isRunning) return
        isRunning = false
        recording = 0
        RewardConfirmOverlayService.onResult = null
        app.stopService(Intent(app, RewardConfirmOverlayService::class.java))

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

        val proofs = FightState.proofs.toMap()

        // best guess of what was farmed; can be changed in the rename dialog
        val type = RunType.infer(proofs, FightState.shards)?.key

        val saved = RunStorage.saveRun(
            app,
            RunRecord(
                runid = runid,
                date = date,
                duration = duration,
                gold = FightState.gold,
                orns = FightState.orns,
                exp = FightState.exp,
                shards = FightState.shards,
                name = "",
                proofs = proofs,
                type = type
            )
        )

        FightState.gold = 0L
        FightState.orns = 0L
        FightState.exp = 0L
        FightState.shards = 0L
        FightState.proofs.clear()

        FightState.lastGold = 0L
        FightState.lastOrns = 0L
        FightState.lastExp = 0L
        FightState.lastShards = 0L
        FightState.lastProofs = emptyMap()
        FightState.startTime = 0L
        startTime = 0L
        if (saved) historyVersion++
    }
}
