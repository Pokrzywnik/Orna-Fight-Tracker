package com.frodo.fighttracker

import android.app.Service
import android.content.Context
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
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

class ItemQualityOverlayService : Service() {

    private val handler =
        Handler(Looper.getMainLooper())

    private var fadeRunnable: Runnable? = null

    companion object {

        private var instance: ItemQualityOverlayService? = null

        fun showAssessment(
            context: Context,
            assessment: ItemAssessor.Assessment
        ) {

            val intent =
                Intent(
                    context,
                    ItemQualityOverlayService::class.java
                ).apply {

                    putExtra(
                        "itemName",
                        assessment.itemName
                    )

                    putExtra(
                        "upgradeLevel",
                        assessment.upgradeLevel
                    )

                    putExtra(
                        "exactQuality",
                        assessment.exactQuality
                            ?: Double.NaN
                    )

                    putExtra(
                        "qualityMin",
                        assessment.qualityRangeMin
                    )

                    putExtra(
                        "qualityMax",
                        assessment.qualityRangeMax
                    )

                    putExtra(
                        "quality",
                        assessment.quality
                            ?: ""
                    )
                }

            context.startService(intent)
        }

        fun hide(context: Context) {

            context.stopService(
                Intent(
                    context,
                    ItemQualityOverlayService::class.java
                )
            )
        }
        const val ORNATE_COLOR = "#E53935"
        const val LEGENDARY_COLOR = "#AB47BC"
        const val FAMED_COLOR = "#42A5F5"
        const val SUPERIOR_COLOR = "#66BB6A"
        const val NORMAL_COLOR = "#FFFFFF"
        const val POOR_BROKEN_COLOR = "#A56A43"

        const val DEFAULT_BACKGROUND = "#DD171717"
    }

    private lateinit var windowManager: WindowManager

    private var overlayView: View? = null

    private var clipboardText: String = ""


    private fun buildClipboardText(
        itemName: String,
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double
    ): String {

        val qualityText =
            if (
                exactQuality.isNaN() &&
                qualityMin == 0.0 &&
                qualityMax == 0.0
            ) {

                "Unknown"

            } else if (!exactQuality.isNaN()) {

                "%.0f%%".format(
                    exactQuality * 100.0
                )

            } else {

                "%.0f–%.0f%%".format(
                    qualityMin * 100.0,
                    qualityMax * 100.0
                )
            }

        return "$itemName $qualityText"
    }

    private fun copyToClipboard() {

        if (clipboardText.isBlank()) {
            return
        }

        val clipboard =
            getSystemService(
                CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        val clip =
            android.content.ClipData.newPlainText(
                "Item Assessment",
                clipboardText
            )

        clipboard.setPrimaryClip(
            clip
        )
    }

    private fun qualityFromPercentage(
        percentage: Double
    ): String {

        return when {

            percentage >= 170.0 ->
                "ornate"

            percentage >= 140.0 ->
                "legendary"

            percentage >= 120.0 ->
                "famed"

            percentage >= 101.0 ->
                "superior"

            percentage >= 100.0 ->
                "normal"

            percentage >= 90.0 ->
                "poor"

            percentage >= 70.0 ->
                "broken"

            else ->
                "broken"
        }
    }

    private fun resolveQuality(
        quality: String,
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double
    ): String {

        if (quality.isNotBlank()) {

            val normalized =
                quality
                    .trim()
                    .lowercase()
            return when {

                normalized.contains("ornate") ->
                    "ornate"

                normalized.contains("legendary") ->
                    "legendary"

                normalized.contains("famed") ->
                    "famed"

                normalized.contains("superior") ->
                    "superior"

                normalized.contains("normal") ||
                        normalized.contains("common") ->
                    "normal"

                normalized.contains("poor") ->
                    "poor"

                normalized.contains("broken") ->
                    "broken"

                else -> {
                    resolveQualityFromPercentage(
                        exactQuality,
                        qualityMin,
                        qualityMax
                    )
                }
            }

        }

        return resolveQualityFromPercentage(
            exactQuality,
            qualityMin,
            qualityMax
        )
    }

    private fun resolveQualityFromPercentage(
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double
    ): String {

        if (!exactQuality.isNaN()) {

            return qualityFromPercentage(
                exactQuality * 100.0
            )
        }
        val min =
            qualityMin * 100.0

        val max =
            qualityMax * 100.0

        val middle =
            (min + max) / 2.0

        return qualityFromPercentage(
            middle
        )
    }


    private fun qualityColor(
        quality: String
    ): Int {

        return Color.parseColor(
            when (quality) {

                "ornate" ->
                    ORNATE_COLOR

                "legendary" ->
                    LEGENDARY_COLOR

                "famed" ->
                    FAMED_COLOR

                "superior" ->
                    SUPERIOR_COLOR

                "normal" ->
                    NORMAL_COLOR

                "poor",
                "broken" ->
                    POOR_BROKEN_COLOR

                else ->
                    NORMAL_COLOR
            }
        )
    }

    private fun applyQualityStyle(
        view: View,
        quality: String
    ) {

        val color =
            qualityColor(
                quality
            )
        view.findViewById<TextView>(
            R.id.itemName
        ).setTextColor(color)
        val background =
            GradientDrawable().apply {

                setColor(
                    Color.parseColor(
                        DEFAULT_BACKGROUND
                    )
                )

                setStroke(
                    dpToPx(2),
                    color
                )

                cornerRadius =
                    dpToPx(12).toFloat()
            }

        view.background =
            background
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
                dp *
                        resources.displayMetrics.density
                ).roundToInt()
    }

    private fun scheduleFadeOut() {

        fadeRunnable?.let {
            handler.removeCallbacks(it)
        }

        fadeRunnable =
            Runnable {

                overlayView
                    ?.animate()
                    ?.alpha(0f)
                    ?.setDuration(400L)
                    ?.withEndAction {

                        removeOverlay()
                    }
                    ?.start()
            }

        handler.postDelayed(
            fadeRunnable!!,
            5000L
        )
    }

    override fun onCreate() {

        super.onCreate()

        instance = this

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            return START_NOT_STICKY
        }

        val itemName =
            intent.getStringExtra(
                "itemName"
            )
                ?: return START_NOT_STICKY

        val upgradeLevel =
            intent.getStringExtra(
                "upgradeLevel"
            )
                ?: ""

        val exactQuality =
            intent.getDoubleExtra(
                "exactQuality",
                Double.NaN
            )

        val qualityMin =
            intent.getDoubleExtra(
                "qualityMin",
                0.0
            )

        val qualityMax =
            intent.getDoubleExtra(
                "qualityMax",
                0.0
            )

        val quality =
            intent.getStringExtra(
                "quality"
            )
                ?: ""



        showOverlay(
            itemName = itemName,
            upgradeLevel = upgradeLevel,
            exactQuality = exactQuality,
            qualityMin = qualityMin,
            qualityMax = qualityMax,
            quality = quality
        )

        return START_NOT_STICKY
    }

    private fun showOverlay(
        itemName: String,
        upgradeLevel: String,
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double,
        quality: String
    ) {

        clipboardText =
            buildClipboardText(
                itemName = itemName,
                exactQuality = exactQuality,
                qualityMin = qualityMin,
                qualityMax = qualityMax
            )


        val resolvedQuality =
            resolveQuality(
                quality = quality,
                exactQuality = exactQuality,
                qualityMin = qualityMin,
                qualityMax = qualityMax
            )

        if (overlayView != null) {

            updateOverlay(
                itemName = itemName,
                upgradeLevel = upgradeLevel,
                exactQuality = exactQuality,
                qualityMin = qualityMin,
                qualityMax = qualityMax,
                quality = resolvedQuality
            )

            overlayView
                ?.animate()
                ?.cancel()

            overlayView?.alpha = 1f

            scheduleFadeOut()

            return
        }

        val view =
            LayoutInflater
                .from(this)
                .inflate(
                    R.layout.overlay_item_quality,
                    null
                )

        val itemNameView =
            view.findViewById<TextView>(
                R.id.itemName
            )

        val levelView =
            view.findViewById<TextView>(
                R.id.itemLevel
            )

        val qualityView =
            view.findViewById<TextView>(
                R.id.itemQuality
            )
        itemNameView.text =
            itemName

        levelView.text =
            formatUpgradeLevel(
                upgradeLevel
            )

        qualityView.text =
            formatQuality(
                exactQuality,
                qualityMin,
                qualityMax
            )

        applyQualityStyle(
            view,
            resolvedQuality
        )

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {

                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

                } else {

                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL

        params.y = 120

        view.alpha = 0f

        windowManager.addView(
            view,
            params
        )

        overlayView =
            view

        view.setOnClickListener {

            copyToClipboard()

            view.animate()
                .cancel()

            view.alpha = 1f

            scheduleFadeOut()
        }

        view.animate()
            .alpha(1f)
            .setDuration(250L)
            .start()
        scheduleFadeOut()
    }

    private fun updateOverlay(
        itemName: String,
        upgradeLevel: String,
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double,
        quality: String
    ) {

        clipboardText =
            buildClipboardText(
                itemName = itemName,
                exactQuality = exactQuality,
                qualityMin = qualityMin,
                qualityMax = qualityMax
            )
        val view =
            overlayView
                ?: return

        val itemNameView =
            view.findViewById<TextView>(
                R.id.itemName
            )

        val levelView =
            view.findViewById<TextView>(
                R.id.itemLevel
            )

        val qualityView =
            view.findViewById<TextView>(
                R.id.itemQuality
            )

        itemNameView.text =
            itemName

        levelView.text =
            formatUpgradeLevel(
                upgradeLevel
            )

        qualityView.text =
            formatQuality(
                exactQuality,
                qualityMin,
                qualityMax
            )

        applyQualityStyle(
            view,
            quality
        )
    }


    private fun formatUpgradeLevel(
        level: String
    ): String {

        val normalized =
            level
                .trim()
                .lowercase()

        return when {

            normalized == "gf" ||
                    normalized == "godforged" -> {

                "GodForged"
            }

            normalized == "mf" ||
                    normalized == "masterforged" -> {

                "MasterForged"
            }

            normalized == "df" ||
                    normalized == "demonforged" -> {

                "Demonforged"
            }

            else -> {

                if (
                    normalized.startsWith(
                        "level"
                    )
                ) {

                    level

                } else {

                    "Level $level"
                }
            }
        }
    }

    private fun formatQuality(
        exactQuality: Double,
        qualityMin: Double,
        qualityMax: Double
    ): String {

        return if (
            exactQuality.isNaN() &&
            qualityMin == 0.0 &&
            qualityMax == 0.0
        ) {

            "Unknown"

        } else if (
            !exactQuality.isNaN()
        ) {

            "%.0f%%".format(
                exactQuality * 100.0
            )

        } else {

            "%.0f–%.0f%%".format(
                qualityMin * 100.0,
                qualityMax * 100.0
            )
        }
    }

    private fun removeOverlay() {

        fadeRunnable?.let {
            handler.removeCallbacks(it)
        }

        fadeRunnable = null

        overlayView?.let {

            try {

                windowManager.removeView(
                    it
                )

            } catch (_: Exception) {
            }
        }

        overlayView = null
    }

    override fun onDestroy() {

        removeOverlay()

        instance = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}