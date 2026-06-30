package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import android.util.Log
import android.view.View

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View

    private lateinit var ornsText: TextView
    private lateinit var xpText: TextView
    private lateinit var goldText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {

        override fun run() {

            val minutes =
                ((System.currentTimeMillis() - FightState.startTime) / 60000.0)
                    .coerceAtLeast(1.0 / 60.0)

            ornsText.text =
                "${format((FightState.orns / minutes).toLong())}/min"

            xpText.text =
                "${format((FightState.exp / minutes).toLong())}/min"

            goldText.text =
                "${format((FightState.gold / minutes).toLong())}/min"

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {

        super.onCreate()
        Log.d("OverlayService", "===== onCreate =====")

        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_tracker, null)

        Log.d("OverlayService", "Layout inflated")

        ornsText = overlayView.findViewById(R.id.ornsText)
        xpText = overlayView.findViewById(R.id.xpText)
        goldText = overlayView.findViewById(R.id.goldText)

        val params = WindowManager.LayoutParams(

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

        params.x = 100    // 24 px from left
        params.y = 420   // lower from the top

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        Log.d("OverlayService", "About to add overlay")
        windowManager.addView(overlayView, params)
        Log.d("OverlayService", "Overlay successfully added")

        overlayView.setOnTouchListener(object : View.OnTouchListener {

            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View?, event: android.view.MotionEvent): Boolean {

                when (event.action) {

                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()

                        params.x = initialX + dx
                        params.y = initialY + dy

                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
        handler.post(updater)
    }

    override fun onDestroy() {

        super.onDestroy()

        handler.removeCallbacks(updater)

        if (::overlayView.isInitialized)
            windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun format(value: Long): String {

        return value.toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
    }
}