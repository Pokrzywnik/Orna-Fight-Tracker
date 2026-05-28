package com.example.fighttracker

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.util.Log

object MaterialScheduler {

    fun schedule(context: Context) {

        val request =
            PeriodicWorkRequestBuilder<MaterialWorker>(
                12, TimeUnit.MINUTES
            )
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "material_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        Log.d("MaterialScheduler", "SCHEDULED")
    }
}