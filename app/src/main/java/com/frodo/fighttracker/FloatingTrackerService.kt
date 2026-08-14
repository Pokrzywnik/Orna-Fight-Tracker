package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.util.Log
import java.util.concurrent.Executors

class FloatingTrackerService : Service() {

    private val backgroundExecutor = Executors.newSingleThreadExecutor()



    companion object {

        @Volatile
        private var scanning = false

        private var instance: FloatingTrackerService? = null

        fun setScanning(value: Boolean) {

            scanning = value

            instance?.updateScanIcon()
        }

        fun isScanning(): Boolean {
            return scanning
        }

        var isRunning = false

        const val ACTION_START_ITEM_ASSESSMENT =
            "com.frodo.fighttracker.ACTION_START_ITEM_ASSESSMENT"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
        isRunning = true
        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView =
            LayoutInflater.from(this)
                .inflate(R.layout.floating_button, null)

        val button =
            floatingView.findViewById<ImageView>(
                R.id.floatingButton
            )

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.x = 100
        params.y = 300

        windowManager.addView(
            floatingView,
            params
        )

        setupButton(button)
        updateScanIcon()
        backgroundExecutor.execute {

            Log.d(
                "FloatingTracker",
                "========================================"
            )

            Log.d(
                "FloatingTracker",
                "BACKGROUND PIPELINE PRELOAD START"
            )

            try {

                ItemAssessmentPipeline.ItemAssessmentManager
                    .initialize(applicationContext)

                Log.d(
                    "FloatingTracker",
                    "BACKGROUND PIPELINE PRELOAD READY"
                )

            } catch (e: Exception) {

                Log.e(
                    "FloatingTracker",
                    "Pipeline preload failed",
                    e
                )
            }
        }
    }

    private fun updateScanIcon() {

        if (!::floatingView.isInitialized) {
            return
        }

        val button =
            floatingView.findViewById<ImageView>(
                R.id.floatingButton
            )

        if (scanning) {

            button.setImageResource(
                android.R.drawable.ic_media_pause
            )

        } else {

            button.setImageResource(
                android.R.drawable.ic_media_play
            )
        }
    }

    private fun toggleLiveAssessment() {

        if (scanning) {

            Log.d(
                "FloatingTracker",
                "Stopping Live Item Assessment"
            )

            stopService(
                Intent(
                    this,
                    ItemAssessmentCaptureService::class.java
                )
            )

        } else {

            Log.d(
                "FloatingTracker",
                "Starting Live Item Assessment"
            )

            val intent =
                Intent(
                    this,
                    MainActivity::class.java
                )

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

            intent.action =
                ACTION_START_ITEM_ASSESSMENT

            startActivity(intent)
        }
    }

    private fun setupButton(button: ImageView) {

        var initialX = 0
        var initialY = 0

        var touchX = 0f
        var touchY = 0f

        var moved = false

        button.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    initialX = params.x
                    initialY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    moved = false

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        event.rawX - touchX

                    val dy =
                        event.rawY - touchY

                    if (
                        kotlin.math.abs(dx) > 10 ||
                        kotlin.math.abs(dy) > 10
                    ) {
                        moved = true
                    }

                    params.x =
                        initialX + dx.toInt()

                    params.y =
                        initialY + dy.toInt()

                    windowManager.updateViewLayout(
                        floatingView,
                        params
                    )

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!moved) {

                        toggleLiveAssessment()
                    }

                    true
                }

                else -> false
            }
        }
    }


    override fun onDestroy() {
        scanning = false
        instance = null
        if (::floatingView.isInitialized) {

            try {
                windowManager.removeView(floatingView)
            } catch (_: Exception) {
            }
        }

        isRunning = false

        super.onDestroy()
        backgroundExecutor.shutdownNow()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}