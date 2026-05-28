package com.example.fighttracker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.util.Log

class MaterialWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("MaterialWorker", "WORKER RUNNING")
        MaterialNotifier.check(applicationContext)
        Log.d("MaterialWorker", "RUN at ${System.currentTimeMillis()}")

        return Result.success()
    }
}