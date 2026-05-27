package com.example.fighttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MaterialAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        Log.d("MaterialAlarm", "Fired at 7:00 AM")

        MaterialNotifier.check(context)

        // schedule next day
        MaterialAlarmScheduler.schedule(context)
    }
}