package com.example.fighttracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar
import android.util.Log

object MaterialAlarmScheduler {

    fun schedule(context: Context) {

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent =
            Intent(context, MaterialAlarmReceiver::class.java)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pendingIntent)

        val calendar = Calendar.getInstance().apply {

            val hour = SettingsStore.getMaterialHour(context)
            val minute = SettingsStore.getMaterialMinute(context)

            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            Log.d("MaterialAlarm", "Scheduled at $hour $minute")
            // if already past 7:00 today → schedule tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                Log.d("MaterialAlarm", "Scheduled next day at $hour $minute")
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )


    }
}