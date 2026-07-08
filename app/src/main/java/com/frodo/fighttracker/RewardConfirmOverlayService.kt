package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class RewardConfirmOverlayService : Service() {

    companion object {
        var pendingOrns = 0L
        var onResult: ((Boolean) -> Unit)? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View

    private var timer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()

        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_reward_confirm, null)

        val valueText = overlayView.findViewById<TextView>(R.id.rewardValue)
        val infoText = overlayView.findViewById<TextView>(R.id.infoText)
        val acceptBtn = overlayView.findViewById<Button>(R.id.acceptButton)
        val denyBtn = overlayView.findViewById<Button>(R.id.denyButton)

        valueText.text = "${format(pendingOrns)} Orns"

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.7f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (resources.displayMetrics.heightPixels * 0.18f).toInt()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, params)

        acceptBtn.setOnClickListener {
            accept()
        }

        denyBtn.setOnClickListener {
            deny()
        }

        timer = object : CountDownTimer(4000, 1000) {

            override fun onTick(millisUntilFinished: Long) {

                val seconds = kotlin.math.ceil(
                    millisUntilFinished / 1000.0
                ).toInt()

                infoText.text = "Auto-accept in ${seconds}s"
            }

            override fun onFinish() {
                infoText.text = "Accepting..."
                accept()
            }

        }.start()
    }

    private fun accept() {

        timer?.cancel()

        FightState.ornsConfirmationThreshold =
            pendingOrns + 1_000_000L

        onResult?.invoke(true)

        stopSelf()
    }

    private fun deny() {

        timer?.cancel()

        onResult?.invoke(false)

        stopSelf()
    }

    override fun onDestroy() {

        super.onDestroy()

        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }

        onResult = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun format(value: Long): String {

        val v = value.toDouble()

        return when {

            v >= 1_000_000_000 ->
                String.format("%.1f B", v / 1_000_000_000)

            v >= 1_000_000 ->
                String.format("%.1f M", v / 1_000_000)

            v >= 1_000 ->
                String.format("%.1f K", v / 1_000)

            else ->
                value.toString()
        }
    }
}