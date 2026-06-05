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
import android.widget.TextView
import android.os.Handler
import android.os.Looper

class FloatingTrackerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var assessView: View
    private lateinit var assessText: TextView

    private val overlayHandler = Handler(Looper.getMainLooper())

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

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
        createAssessOverlay()
    }

    private fun createAssessOverlay() {

        assessView = LayoutInflater.from(this)
            .inflate(R.layout.floating_assess, null)

        assessText =
            assessView.findViewById(R.id.assessText)

        val assessParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        assessParams.gravity = Gravity.TOP or Gravity.START
        assessParams.x = 220
        assessParams.y = 300

        windowManager.addView(
            assessView,
            assessParams
        )

        assessView.setOnTouchListener(object : View.OnTouchListener {

            var initialX = 0
            var initialY = 0

            var touchX = 0f
            var touchY = 0f

            override fun onTouch(
                v: View,
                event: MotionEvent
            ): Boolean {

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {

                        initialX = assessParams.x
                        initialY = assessParams.y

                        touchX = event.rawX
                        touchY = event.rawY

                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        assessParams.x =
                            initialX + (event.rawX - touchX).toInt()

                        assessParams.y =
                            initialY + (event.rawY - touchY).toInt()

                        windowManager.updateViewLayout(
                            assessView,
                            assessParams
                        )

                        return true
                    }
                }

                return false
            }
        })

        overlayHandler.post(object : Runnable {

            override fun run() {

                if (
                    System.currentTimeMillis() -
                    AssessState.lastUpdate > 5000
                ) {

                    assessView.visibility = View.GONE

                } else {

                    assessView.visibility = View.VISIBLE

                    assessText.text =
                        AssessState.lastOverlay
                }

                overlayHandler.postDelayed(
                    this,
                    500
                )
            }
        })
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

        if (::assessView.isInitialized) {
            windowManager.removeView(assessView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}