package com.frodo.fighttracker

import android.content.Context
import java.io.File

object TrackerLogger {

    private const val FILE_NAME = "fighttracker_debug_log.txt"

    private var logFile: File? = null


    fun startNewSession(context: Context) {

        logFile = File(context.filesDir, FILE_NAME)

        logFile!!.writeText("=== Fight Tracker Debug Log ===\n\n")
    }


    fun write(tag: String, message: String) {

        if (
            tag != "OCR_RAW" &&
            tag != "TRACKER_LOG" &&
            tag != "OCR_BITMAP" &&
            tag != "GOLD_ICON_SCAN"
        ) {
            return
        }

        val file = logFile ?: return

        file.appendText(
            "[${System.currentTimeMillis()}] [$tag] $message\n"
        )
    }


    fun getFile(context: Context): File {
        return logFile ?: File(context.filesDir, FILE_NAME)
    }
}