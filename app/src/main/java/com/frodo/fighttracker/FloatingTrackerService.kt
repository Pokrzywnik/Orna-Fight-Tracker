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

class FloatingTrackerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.floating_button, null)

        val button = floatingView.findViewById<ImageView>(R.id.floatingButton)

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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        windowManager.addView(floatingView, params)

        setupButton(button)
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

                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY

                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        moved = true
                    }

                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()

                    windowManager.updateViewLayout(floatingView, params)

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!moved) {

                        val intent = Intent(this, MainActivity::class.java)

                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP

                        if (!TrackingController.isRunning) {

                            TrackingController.start()

                            intent.action = MainActivity.ACTION_START_TRACKING

                            startActivity(intent)

                            button.setImageResource(android.R.drawable.presence_online)

                        } else {

                            TrackingController.stop()

                            intent.action = MainActivity.ACTION_STOP_TRACKING

                            startActivity(intent)

                            button.setImageResource(android.R.drawable.presence_invisible)
                        }
                    }

                    true
                }

                else -> false
            }
        }

    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}