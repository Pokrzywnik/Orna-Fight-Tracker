package com.frodo.fighttracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle

/** Activity-local spotlight: never forwards tutorial taps to the highlighted controls. */
internal class FirstStartTutorial(
    private val activity: AppCompatActivity,
    restoredStep: Int,
    private val onGleamingPreviewChanged: (Boolean) -> Unit,
    private val navigate: (Int) -> Unit
) {
    private data class Step(
        val page: Int,
        val targets: List<Int>,
        @StringRes val title: Int,
        @StringRes val description: Int
    )

    private val steps = listOf(
        Step(0, listOf(R.id.rewardsOverlayToggle, R.id.overlayToggle),
            R.string.tutorial_overlays_title, R.string.tutorial_overlays_description),
        Step(0, listOf(R.id.settingsButton),
            R.string.tutorial_settings_title, R.string.tutorial_settings_description),
        Step(0, listOf(R.id.trackingRow),
            R.string.tutorial_tracking_title, R.string.tutorial_tracking_description),
        Step(4, listOf(R.id.nav_guides),
            R.string.tutorial_guides_title, R.string.tutorial_guides_description),
        Step(3, listOf(R.id.nav_gleaming),
            R.string.tutorial_gleaming_title, R.string.tutorial_gleaming_description),
        Step(2, listOf(R.id.nav_codex),
            R.string.tutorial_codex_title, R.string.tutorial_codex_description),
        Step(1, listOf(R.id.nav_materials),
            R.string.tutorial_materials_title, R.string.tutorial_materials_description),
        Step(1, listOf(R.id.tutorial_material_calendar),
            R.string.tutorial_calendar_title, R.string.tutorial_calendar_description),
        Step(1, listOf(R.id.tutorial_material_guilds),
            R.string.tutorial_guilds_title, R.string.tutorial_guilds_description),
        Step(1, listOf(R.id.tutorial_material_watchlist),
            R.string.tutorial_watchlist_title, R.string.tutorial_watchlist_description)
    )
    private val progress = TutorialProgress(steps.size, restoredStep)
    val currentStep: Int get() = progress.step

    private val root = activity.window.decorView as ViewGroup
    private val content = activity.findViewById<View>(android.R.id.content)
    private val originalAccessibility = content.importantForAccessibility
    private val handler = Handler(Looper.getMainLooper())
    private val overlay = SpotlightView(activity, ::advance)
    private var active = false
    private var disposed = false
    private var targetViews = emptyList<View>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = advance()
    }
    private val updateBounds = ViewTreeObserver.OnGlobalLayoutListener {
        if (active && targetViews.isNotEmpty()) overlay.updateHighlight(targetViews)
    }
    private val present = Runnable { presentStep() }
    private val unlock = Runnable {
        if (active && progress.canAdvance(SystemClock.uptimeMillis())) {
            overlay.showReady(progress.step == steps.lastIndex)
        }
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
    }

    fun resume() {
        if (disposed || active || !activity.hasWindowFocus() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        active = true
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        backCallback.isEnabled = true
        if (overlay.parent == null) {
            root.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.viewTreeObserver.addOnGlobalLayoutListener(updateBounds)
        }
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        prepareStep()
    }

    fun pause() {
        active = false
        onGleamingPreviewChanged(false)
        content.importantForAccessibility = originalAccessibility
        backCallback.isEnabled = false
        progress.pause()
        handler.removeCallbacksAndMessages(null)
        overlay.visibility = View.INVISIBLE
    }

    fun dispose() {
        pause()
        disposed = true
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(updateBounds)
        }
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        backCallback.remove()
    }

    private fun prepareStep() {
        handler.removeCallbacksAndMessages(null)
        progress.pause()
        targetViews = emptyList()
        overlay.waitForTarget()
        onGleamingPreviewChanged(steps[progress.step].page == 3)
        navigate(steps[progress.step].page)
        handler.post(present)
    }

    private fun presentStep() {
        if (!active || disposed) return
        val step = steps[progress.step]
        val views = step.targets.mapNotNull { activity.findViewById<View>(it) }
        if (overlay.width == 0 || overlay.height == 0 || views.size != step.targets.size ||
            views.any { !it.isShown || it.width == 0 || it.height == 0 }) {
            handler.postDelayed(present, 32L)
            return
        }
        // Keep the tracking-options row visible even on small screens / large UI scales.
        views.forEach { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), true) }
        if (views.any { !it.getGlobalVisibleRect(Rect()) }) {
            handler.postDelayed(present, 32L)
            return
        }
        targetViews = views
        overlay.showStep(progress.step + 1, steps.size, step.title, step.description, views)
        overlay.doOnPreDraw {
            if (active && !disposed && targetViews === views) {
                progress.markVisible(SystemClock.uptimeMillis())
                handler.postDelayed(unlock, TutorialProgress.READING_DELAY_MS)
            }
        }
    }

    private fun advance() {
        if (!active || !progress.advance(SystemClock.uptimeMillis())) return
        if (progress.isFinished) {
            FirstRunStore.markShown(activity)
            dispose()
            navigate(0)
        } else {
            prepareStep()
        }
    }
}

/** Draws the dimming as a path with a hole, without bitmap layers or CLEAR blending. */
private class SpotlightView(
    private val activity: AppCompatActivity,
    private val onAdvance: () -> Unit
) : FrameLayout(activity) {
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2B544.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val mask = Path()
    private var highlight: RectF? = null
    private val counter = label(12f, 0xFFF2B544.toInt())
    private val title = label(21f, Color.WHITE).apply { setTypeface(typeface, Typeface.BOLD) }
    private val description = label(16f, 0xFFE4E7ED.toInt()).apply {
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private val hint = label(12f, 0xFFAFB6C3.toInt())
    private val card = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = GradientDrawable().apply {
            setColor(0xFF181D26.toInt())
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFF394250.toInt())
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        card.addView(counter, rowParams(0))
        card.addView(title, rowParams(10))
        card.addView(description, rowParams(12))
        card.addView(hint, rowParams(18))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        card.visibility = View.INVISIBLE
    }

    private fun dp(value: Int): Int = UiKit.dp(activity, value)

    private fun label(size: Float, textColor: Int) = TextView(activity).apply {
        gravity = Gravity.CENTER
        textSize = size
        setTextColor(textColor)
    }

    private fun rowParams(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    fun waitForTarget() {
        card.visibility = View.INVISIBLE
        highlight = null
        invalidate()
    }

    fun showStep(number: Int, total: Int, @StringRes titleRes: Int,
                 @StringRes descriptionRes: Int, targets: List<View>) {
        counter.text = activity.getString(R.string.tutorial_progress, number, total)
        title.setText(titleRes)
        description.setText(descriptionRes)
        hint.setText(R.string.tutorial_wait)
        updateHighlight(targets)
        card.visibility = View.VISIBLE
    }

    fun showReady(lastStep: Boolean) {
        hint.setText(if (lastStep) R.string.tutorial_finish else R.string.tutorial_next)
        announceForAccessibility(title.text.toString() + ". " + description.text + ". " + hint.text)
    }

    fun updateHighlight(targets: List<View>) {
        val origin = IntArray(2)
        getLocationOnScreen(origin)
        val bounds = RectF()
        for (target in targets) {
            val position = IntArray(2)
            target.getLocationOnScreen(position)
            val rect = RectF(
                (position[0] - origin[0]).toFloat(), (position[1] - origin[1]).toFloat(),
                (position[0] - origin[0] + target.width).toFloat(),
                (position[1] - origin[1] + target.height).toFloat()
            )
            if (bounds.isEmpty) bounds.set(rect) else bounds.union(rect)
        }
        bounds.inset(-dp(5).toFloat(), -dp(5).toFloat())
        bounds.intersect(0f, 0f, width.toFloat(), height.toFloat())
        highlight = bounds
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mask.reset()
        mask.fillType = Path.FillType.EVEN_ODD
        mask.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        val radius = dp(14).toFloat()
        highlight?.let { mask.addRoundRect(it, radius, radius, Path.Direction.CW) }
        canvas.drawPath(mask, dimPaint)
        highlight?.let { canvas.drawRoundRect(it, radius, radius, borderPaint) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onAdvance()
        return true
    }
}
