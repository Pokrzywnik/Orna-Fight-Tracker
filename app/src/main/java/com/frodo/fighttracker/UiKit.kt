package com.frodo.fighttracker

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import java.util.Locale

/**
 * Small helpers shared by the Tracker / Materials screens
 * (number formatting, dp conversion, proof icon views).
 */
object UiKit {

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** 1234567 -> "1 234 567" (same grouping the app always used). */
    fun fmt(value: Long): String =
        value.toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()

    /** 1_234_567.0 -> "1.2M", 12_300.0 -> "12.3k", 950.0 -> "950". */
    fun compact(value: Double): String {

        val abs = kotlin.math.abs(value)

        fun one(v: Double, suffix: String) =
            String.format(Locale.US, "%.1f", v).removeSuffix(".0") + suffix

        return when {
            abs >= 1_000_000_000.0 -> one(value / 1_000_000_000.0, "B")
            abs >= 1_000_000.0 -> one(value / 1_000_000.0, "M")
            abs >= 1_000.0 -> one(value / 1_000.0, "k")
            else -> String.format(Locale.US, "%.0f", value)
        }
    }

    /** "01:23:45" -> 83.75 (minutes). Returns 0.0 when it cannot be parsed. */
    fun durationMinutes(hms: String): Double {

        val parts = hms.split(":").mapNotNull { it.trim().toIntOrNull() }

        if (parts.size != 3) return 0.0

        return parts[0] * 60.0 + parts[1] + parts[2] / 60.0
    }

    /** Milliseconds -> "HH:MM:SS". */
    fun timer(elapsedMs: Long): String {

        val total = (elapsedMs / 1000).coerceAtLeast(0L)

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            total / 3600,
            (total / 60) % 60,
            total % 60
        )
    }

    private fun loadIcon(view: ImageView, url: String) {
        view.load(url) {
            crossfade(false)
            error(android.R.drawable.ic_menu_help)
        }
    }

    /**
     * Current-run proof: 28dp icon + big counter.
     * The returned TextView is the counter (updated every tick).
     */
    fun proofCell(ctx: Context, iconUrl: String, count: Long): Pair<View, TextView> {

        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        loadIcon(icon, iconUrl)

        val counter = TextView(ctx).apply {
            setTextAppearance(R.style.TextAppearance_FT_Number)
            textSize = 16f
            text = fmt(count)
            maxLines = 1
            setPadding(dp(ctx, 6), 0, dp(ctx, 4), 0)
        }

        cell.addView(icon)
        cell.addView(counter)

        return cell to counter
    }

    /** History proof chip: 20dp icon + number. */
    fun proofChip(ctx: Context, iconUrl: String, count: Long): View {

        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_proof_chip)
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        loadIcon(icon, iconUrl)

        val number = TextView(ctx).apply {
            setTextAppearance(R.style.TextAppearance_FT_Number)
            textSize = 12f
            text = fmt(count)
            maxLines = 1
            setPadding(dp(ctx, 5), 0, 0, 0)
        }

        chip.addView(icon)
        chip.addView(number)

        return chip
    }
}
