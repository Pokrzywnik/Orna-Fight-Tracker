package com.frodo.fighttracker

import android.content.Context
import android.content.Intent
import android.net.Uri

object OrnaLauncher {

    private const val PACKAGE = "playorna.com.orna"

    fun launch(context: Context) {

        val pm = context.packageManager

        val intent = pm.getLaunchIntentForPackage(PACKAGE)
            ?: Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$PACKAGE")
            }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}