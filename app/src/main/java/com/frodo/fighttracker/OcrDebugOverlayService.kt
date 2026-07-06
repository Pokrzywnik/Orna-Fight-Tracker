package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView

class OcrDebugOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var overlayText: TextView
    private lateinit var overlayCanvas: DebugCanvas

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {

            overlayText.text =
                OcrDebugBuffer.lastText ?: "waiting OCR..."

            overlayCanvas.invalidate()

            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        root = FrameLayout(this)

        overlayCanvas = DebugCanvas(this)

        overlayText = TextView(this).apply {

            setTextColor(Color.WHITE)

            textSize = 11f

            setPadding(20,20,20,20)
        }

        root.addView(
            overlayCanvas,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(overlayText)

        val params =
            WindowManager.LayoutParams(

                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

                PixelFormat.TRANSLUCENT
            )

        windowManager.addView(root, params)

        handler.post(updater)
    }

    override fun onDestroy() {

        handler.removeCallbacks(updater)

        windowManager.removeView(root)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class DebugCanvas(context: android.content.Context)
        : View(context) {

        private val green = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val blue = Paint().apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val red = Paint().apply {
            color = Color.RED
            strokeWidth = 6f
        }

        private val yellow = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        override fun onDraw(canvas: Canvas) {

            OcrDebugBuffer.userCrop?.let {
                canvas.drawRect(it, green)
            }

            OcrDebugBuffer.finalCrop?.let {
                canvas.drawRect(it, blue)
            }

            if (OcrDebugBuffer.detectedLeft >= 0) {

                canvas.drawLine(
                    OcrDebugBuffer.detectedLeft.toFloat(),
                    0f,
                    OcrDebugBuffer.detectedLeft.toFloat(),
                    height.toFloat(),
                    red
                )
            }

            OcrDebugBuffer.bookmarkRect?.let {
                canvas.drawRect(it, yellow)

            }
        }
    }
}