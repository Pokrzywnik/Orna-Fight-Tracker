package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.view.View
import coil.load

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View

    private lateinit var ornsText: TextView
    private lateinit var xpText: TextView
    private lateinit var goldText: TextView
    private lateinit var headerText: TextView
    private lateinit var toggleButton: TextView
    private lateinit var closeButton: TextView
    private lateinit var undoButton: TextView
    private lateinit var proofsContainer: LinearLayout

    private lateinit var ornsRow: View
    private lateinit var xpRow: View
    private lateinit var goldRow: View

    /** proof id -> counter TextView shown in the overlay */
    private val proofViews = mutableMapOf<String, TextView>()

    /** which proofs are currently built (rebuild rows only when it changes) */
    private var proofsSignature: String? = null

    private var displayMode = 0

    /** null = button state not applied yet */
    private var lastRunningState: Boolean? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Values captured at the moment tracking was stopped from the overlay,
     * so they stay visible (FightState is reset after saving the run).
     */
    private data class Snapshot(
        val orns: Long,
        val exp: Long,
        val gold: Long,
        val lastOrns: Long,
        val lastExp: Long,
        val lastGold: Long,
        val proofs: Map<String, Long>,
        val lastProofs: Map<String, Long>,
        val minutes: Double
    )

    private var frozenSnapshot: Snapshot? = null

    private fun liveSnapshot(): Snapshot {

        val minutes =
            ((System.currentTimeMillis() - FightState.startTime) / 60000.0)
                .coerceAtLeast(1.0 / 60.0)

        return Snapshot(
            orns = FightState.orns,
            exp = FightState.exp,
            gold = FightState.gold,
            lastOrns = FightState.lastOrns,
            lastExp = FightState.lastExp,
            lastGold = FightState.lastGold,
            proofs = FightState.proofs.toMap(),
            lastProofs = FightState.lastProofs,
            minutes = minutes
        )
    }

    private val updater = object : Runnable {

        override fun run() {

            render()

            handler.postDelayed(this, 1000)
        }
    }

    private fun render() {

        if (!::overlayView.isInitialized) return

        val running = TrackingController.isRunning

        updateButtons(running)
        updateCurrencyRows()

        val snapshot =
            if (running) {
                frozenSnapshot = null
                liveSnapshot()
            } else {
                // idle: show frozen values of the last run (if any)
                frozenSnapshot ?: return
            }

        when (displayMode) {

            // Totals
            0 -> {

                headerText.text = "Total"

                ornsText.text = formatShort(snapshot.orns)
                xpText.text = formatShort(snapshot.exp)
                goldText.text = formatShort(snapshot.gold)
            }

            // Per minute
            1 -> {

                headerText.text = "Average"

                ornsText.text =
                    "${formatShort((snapshot.orns / snapshot.minutes).toLong())}/m"

                xpText.text =
                    "${formatShort((snapshot.exp / snapshot.minutes).toLong())}/m"

                goldText.text =
                    "${formatShort((snapshot.gold / snapshot.minutes).toLong())}/m"
            }

            // Last reward
            2 -> {

                headerText.text = "Last Reward"

                ornsText.text =
                    "+${formatShort(snapshot.lastOrns)}"

                xpText.text =
                    "+${formatShort(snapshot.lastExp)}"

                goldText.text =
                    "+${formatShort(snapshot.lastGold)}"
            }
        }

        // proofs / keys rows (after the when, not inside it)
        renderProofs(snapshot)
    }

    /** Shows / hides Orns, EXP, Gold rows according to the Track dialog. */
    private fun updateCurrencyRows() {

        fun vis(c: CurrencyTracking.Currency) =
            if (CurrencyTracking.shownInOverlay(this, c)) View.VISIBLE else View.GONE

        ornsRow.visibility = vis(CurrencyTracking.Currency.ORNS)
        xpRow.visibility = vis(CurrencyTracking.Currency.EXP)
        goldRow.visibility = vis(CurrencyTracking.Currency.GOLD)
    }

    private fun renderProofs(s: Snapshot) {

        val proofs = ProofTracking.overlayProofs(this)
        val signature = proofs.joinToString(",") { it.id }

        // rebuild rows only when the set of proofs changed
        // (avoids reloading icons every second)
        if (signature != proofsSignature) {

            proofsSignature = signature
            proofsContainer.removeAllViews()
            proofViews.clear()

            for (p in proofs) {

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, 0)
                }

                val icon = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    load(p.iconUrl) {
                        crossfade(false)
                        error(android.R.drawable.ic_menu_help)
                    }
                }

                val tv = TextView(this).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(dp(8), 0, 0, 0)
                }

                row.addView(icon)
                row.addView(tv)
                proofsContainer.addView(row)
                proofViews[p.id] = tv
            }

            proofsContainer.visibility =
                if (proofs.isEmpty()) View.GONE else View.VISIBLE
        }

        for ((id, tv) in proofViews) {

            tv.text = when (displayMode) {

                0 -> formatShort(s.proofs[id] ?: 0L)

                1 -> String.format("%.1f/m", (s.proofs[id] ?: 0L) / s.minutes)

                else -> "+${formatShort(s.lastProofs[id] ?: 0L)}"
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buttonBackground(color: Int): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun updateButtons(running: Boolean) {

        if (lastRunningState == running) return

        lastRunningState = running

        if (running) {

            toggleButton.text = "■ Stop"
            toggleButton.background =
                buttonBackground(Color.parseColor("#C62828"))

            undoButton.visibility = View.VISIBLE

        } else {

            toggleButton.text = "▶ Start"
            toggleButton.background =
                buttonBackground(Color.parseColor("#2E7D32"))

            undoButton.visibility = View.GONE
        }

        undoButton.background =
            buttonBackground(Color.parseColor("#455A64"))
    }

    private fun onToggleClicked() {

        if (TrackingController.isRunning) {

            // remember final numbers before FightState is reset
            frozenSnapshot = liveSnapshot()

            TrackingController.stopAndSave(
                applicationContext,
                closeOverlay = false
            )

            render()

        } else {

            // MediaProjection permission must be requested by an Activity
            val intent =
                Intent(this, MainActivity::class.java).apply {

                    action = MainActivity.ACTION_START_TRACKING

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            startActivity(intent)
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
        headerText = overlayView.findViewById(R.id.headerText)
        toggleButton = overlayView.findViewById(R.id.toggleButton)
        closeButton = overlayView.findViewById(R.id.closeButton)
        undoButton = overlayView.findViewById(R.id.undoButton)
        proofsContainer = overlayView.findViewById(R.id.proofsContainer)

        ornsRow = overlayView.findViewById(R.id.ornsRow)
        xpRow = overlayView.findViewById(R.id.xpRow)
        goldRow = overlayView.findViewById(R.id.goldRow)

        toggleButton.setOnClickListener {
            onToggleClicked()
        }

        undoButton.setOnClickListener {

            val ok = FightTrackerService.undoLast()

            Toast.makeText(
                this,
                if (ok) "Last reward removed" else "Nothing to undo",
                Toast.LENGTH_SHORT
            ).show()

            render()
        }

        closeButton.setOnClickListener {
            stopSelf()
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

        params.x = 100
        params.y = 420

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        Log.d("OverlayService", "About to add overlay")
        windowManager.addView(overlayView, params)
        Log.d("OverlayService", "Overlay successfully added")

        // Drag + tap-to-cycle on the whole overlay.
        // The buttons are clickable children, so they get their touches first.
        overlayView.setOnTouchListener(object : View.OnTouchListener {

            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var downTime = 0L

            override fun onTouch(v: View?, event: android.view.MotionEvent): Boolean {

                when (event.action) {

                    android.view.MotionEvent.ACTION_DOWN -> {
                        downTime = System.currentTimeMillis()
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

                    android.view.MotionEvent.ACTION_UP -> {

                        val moved =
                            kotlin.math.abs(event.rawX - touchX) > 20 ||
                                    kotlin.math.abs(event.rawY - touchY) > 20

                        val shortTap =
                            System.currentTimeMillis() - downTime < 250

                        if (!moved && shortTap) {
                            displayMode = (displayMode + 1) % 3
                            render()
                        }

                        return true
                    }
                }
                return false
            }
        })

        render()
        handler.postDelayed(updater, 1000)
    }

    /**
     * Called again every time tracking (re)starts and MainActivity
     * calls startService(OverlayService) on an already running overlay.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        frozenSnapshot = null
        render()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {

        super.onDestroy()

        handler.removeCallbacks(updater)

        if (::overlayView.isInitialized)
            windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatShort(value: Long): String {

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