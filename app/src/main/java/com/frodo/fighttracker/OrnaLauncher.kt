package com.frodo.fighttracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object OrnaLauncher {

    private const val PACKAGE = "playorna.com.orna"
    private const val TAG = "OrnaLauncher"

    /**
     * Tries to open Orna. Never throws.
     *
     * Order:
     *  1. launch the installed game
     *  2. open the Play Store app (market://)
     *  3. open the Play Store website in a browser
     *
     * Returns true if the game itself was launched.
     * Tracking should continue either way - the user can
     * switch to the game manually.
     */
    fun launch(context: Context): Boolean {

        val pm = context.packageManager

        // 1. Installed game
        try {

            val launchIntent = pm.getLaunchIntentForPackage(PACKAGE)

            if (launchIntent != null) {

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

                return true
            }

            Log.w(
                TAG,
                "Launch intent is null (not installed, or hidden by package visibility)"
            )

        } catch (e: Exception) {

            Log.e(TAG, "Could not launch Orna", e)
        }

        // 2. Play Store app
        try {

            val market =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$PACKAGE")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(market)

            return false

        } catch (e: Exception) {

            Log.w(TAG, "No Play Store app available", e)
        }

        // 3. Browser
        try {

            val web =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(web)

        } catch (e: Exception) {

            Log.e(TAG, "Could not open store page either", e)
        }

        return false
    }
}