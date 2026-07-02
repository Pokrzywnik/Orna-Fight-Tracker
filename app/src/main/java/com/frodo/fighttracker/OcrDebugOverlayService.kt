package com.frodo.fighttracker

import android.app.Service
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.TextView
import android.content.Intent

class OcrDebugOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlay: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {

            overlay.text = OcrDebugBuffer.lastText ?: "waiting OCR..."

            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlay = TextView(this).apply {
            setPadding(24, 24, 24, 24)
            textSize = 12f
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

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
        params.x = 50
        params.y = 200

        windowManager.addView(overlay, params)

        handler.post(updater)
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(updater)

        if (::overlay.isInitialized) {
            windowManager.removeView(overlay)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}