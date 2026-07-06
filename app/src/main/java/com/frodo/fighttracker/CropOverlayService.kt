package com.frodo.fighttracker

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.*
import android.widget.ImageButton

class CropOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: CropView
    private lateinit var saveBtn: ImageButton

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        view = CropView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(view, params)

        saveBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setBackgroundColor(0xAAAAAAAA.toInt())
            setOnClickListener {
                CropStore.save(this@CropOverlayService, view.rect)
                stopSelf()
            }
        }

        val btnParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        btnParams.gravity = Gravity.TOP or Gravity.END

        wm.addView(saveBtn, btnParams)
    }

    override fun onDestroy() {
        wm.removeView(view)
        wm.removeView(saveBtn)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class CropView(context: android.content.Context) : View(context) {

        val rect = RectF(0.2f, 0.2f, 0.8f, 0.8f)

        private val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val fill = Paint().apply {
            color = 0x4400FF00
            style = Paint.Style.FILL
        }

        private val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private var mode = 0
        private var lastX = 0f
        private var lastY = 0f

        override fun onDraw(canvas: Canvas) {
            val l = rect.left * width
            val t = rect.top * height
            val r = rect.right * width
            val b = rect.bottom * height

            canvas.drawRect(l, t, r, b, fill)
            canvas.drawRect(l, t, r, b, paint)

            // 4 corner handles
            drawHandle(canvas, l, t)
            drawHandle(canvas, r, t)
            drawHandle(canvas, l, b)
            drawHandle(canvas, r, b)
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
            canvas.drawCircle(x, y, 18f, handlePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            val x = event.x
            val y = event.y

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    mode = detectHandle(x, y)
                    lastX = x
                    lastY = y
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = (x - lastX) / width
                    val dy = (y - lastY) / height

                    when (mode) {
                        0 -> move(dx, dy)
                        1 -> resizeLeftTop(dx, dy)
                        2 -> resizeRightTop(dx, dy)
                        3 -> resizeLeftBottom(dx, dy)
                        4 -> resizeRightBottom(dx, dy)
                    }

                    clamp()
                    invalidate()

                    lastX = x
                    lastY = y
                }
            }

            return true
        }

        private fun move(dx: Float, dy: Float) {
            rect.left += dx
            rect.right += dx
            rect.top += dy
            rect.bottom += dy
        }

        private fun resizeLeftTop(dx: Float, dy: Float) {
            rect.left += dx
            rect.top += dy
        }

        private fun resizeRightTop(dx: Float, dy: Float) {
            rect.right += dx
            rect.top += dy
        }

        private fun resizeLeftBottom(dx: Float, dy: Float) {
            rect.left += dx
            rect.bottom += dy
        }

        private fun resizeRightBottom(dx: Float, dy: Float) {
            rect.right += dx
            rect.bottom += dy
        }

        private fun detectHandle(x: Float, y: Float): Int {
            val l = rect.left * width
            val t = rect.top * height
            val r = rect.right * width
            val b = rect.bottom * height

            val d = 80

            return when {
                near(x, y, l, t, d) -> 1
                near(x, y, r, t, d) -> 2
                near(x, y, l, b, d) -> 3
                near(x, y, r, b, d) -> 4
                else -> 0
            }
        }

        private fun near(x: Float, y: Float, hx: Float, hy: Float, d: Int): Boolean {
            return kotlin.math.abs(x - hx) < d && kotlin.math.abs(y - hy) < d
        }

        private fun clamp() {
            rect.left = rect.left.coerceIn(0f, 1f)
            rect.top = rect.top.coerceIn(0f, 1f)
            rect.right = rect.right.coerceIn(0f, 1f)
            rect.bottom = rect.bottom.coerceIn(0f, 1f)
        }
    }
}