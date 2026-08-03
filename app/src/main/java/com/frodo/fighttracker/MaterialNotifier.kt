package com.frodo.fighttracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import android.app.PendingIntent
import android.content.Intent

object MaterialNotifier {

    private const val CHANNEL_ID = "materials_notify"

    fun check(context: Context) {

        Log.d("Notifications", "check() started")

        val enabled =
            SettingsStore.getMaterialNotifications(context)

        Log.d("Notifications", "enabled = $enabled")


        if (!SettingsStore.getMaterialNotifications(context)) {
            Log.d("Notifications", "notifications disabled")
            return
        }

        SelectedMaterials.load(context)

        val selected = SelectedMaterials.selected

        if (selected.isEmpty()) return

        SelectedGuilds.load(context)

        val files = listOf(
            "remembrance.csv",
            "coral.csv",
            when (SettingsStore.getAnguishVersion(context)) {
                "2.0" -> "anguish_2_0.csv"
                else -> "anguish_1_0.csv"
            },
            "sparring.csv",
            "trials.csv",
            "towers.csv",
            "towers_hoa.csv"
        ).filter {
            SelectedGuilds.isEnabled(it)
        }

        val todayItems = mutableSetOf<String>()
        val tomorrowItems = mutableSetOf<String>()

        val today = getDateString(0)
        val tomorrow = getDateString(1)

        files.forEach { file ->

            val input = context.assets.open(file)

            val reader =
                BufferedReader(InputStreamReader(input))

            reader.readLine()

            reader.forEachLine { line ->

                val parts = line.split(",", limit = 3)

                if (parts.size >= 3) {

                    val date = parts[1]

                    val items = parts[2]
                        .replace("\"", "")
                        .split(",")
                        .map { it.trim() }

                    if (date.equals(today, true)) {
                        todayItems.addAll(items)
                    }

                    if (date.equals(tomorrow, true)) {
                        tomorrowItems.addAll(items)
                    }
                }
            }
        }

        val todayMatches =
            selected.filter { todayItems.contains(it) }

        val tomorrowMatches =
            selected.filter { tomorrowItems.contains(it) }

        android.util.Log.d(
            "MATERIAL_NOTIFY",
            "Matches: $todayMatches $tomorrowMatches"
        )

        if (
            todayMatches.isNotEmpty() ||
            tomorrowMatches.isNotEmpty()
        ) {

            Log.d("Notifications", "found some materials")

            createChannel(context)

            val style = NotificationCompat.InboxStyle()

            if (todayMatches.isNotEmpty()) {

                style.addLine("🟢 TODAY")

                todayMatches.forEach {
                    style.addLine("   • $it")
                }
            }

            if (tomorrowMatches.isNotEmpty()) {

                style.addLine("")
                style.addLine("🟡 TOMORROW")

                tomorrowMatches.forEach {
                    style.addLine("   • $it")
                }
            }


            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra("open_tab", 1)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                999,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("FightTracker")
                    .setStyle(style)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

            val manager =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.notify(999, notification)

        } else {

            Log.d("Notifications", "materials not found")
        }
    }

    private fun getDateString(offset: Long): String {

        val date = LocalDate.now().plusDays(offset)

        val month = date.month.getDisplayName(
            TextStyle.FULL,
            Locale.ENGLISH
        )

        return "$month ${date.dayOfMonth}"
    }

    private fun createChannel(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Material Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )

            val manager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }
}