package com.frodo.fighttracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.app.Activity
import android.os.HandlerThread

private const val ASSESSMENT_CROP_BOTTOM_RATIO = 0.50f

class ItemAssessmentCaptureService : Service() {

    private val assessmentExecutor =
        Executors.newSingleThreadExecutor()

    private val processingFrame =
        AtomicBoolean(false)

    private var lastFrameFingerprint: IntArray? = null

    private var pendingBitmap: Bitmap? = null

    private val pendingFrameLock = Any()

    private lateinit var pipeline: ItemAssessmentPipeline

    companion object {

        private const val TAG = "ItemAssessmentCapture"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID =
            "item_assessment_capture"

        private const val NOTIFICATION_ID =
            2001

    }

    private lateinit var projectionManager: MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler =
        Handler(Looper.getMainLooper())

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var isCapturing = false

    private var lastFrameHash: LongArray? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    /**
     * Zapobiega wielokrotnemu przetwarzaniu tej samej klatki.
     */



    override fun onCreate() {

        super.onCreate()

        Log.d(
            TAG,
            "===== ItemAssessmentCaptureService onCreate ====="
        )

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        captureThread =
            HandlerThread("ItemAssessmentCapture")

        captureThread.start()

        captureHandler =
            Handler(captureThread.looper)

        createNotificationChannel()

        startForegroundServiceNotification()

        assessmentExecutor.execute {

            try {

                pipeline =
                    ItemAssessmentPipeline
                        .ItemAssessmentManager
                        .getPipeline(
                            applicationContext
                        )

                Log.d(
                    TAG,
                    "Pipeline available for live assessment"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Could not initialize pipeline",
                    e
                )
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(TAG, "onStartCommand")

        if (intent == null) {
            Log.e(TAG, "Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode =
            intent.getIntExtra(
                EXTRA_RESULT_CODE,
                -1
            )

        val projectionData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                intent.getParcelableExtra(
                    EXTRA_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }
        Log.d(
            TAG,
            "resultCode=$resultCode"
        )

        Log.d(
            TAG,
            "projectionData=$projectionData"
        )

        Log.d(
            TAG,
            "extras=${intent.extras?.keySet()}"
        )

        Log.d(
            TAG,
            "extrasSize=${intent.extras?.size()}"
        )

        if (
            resultCode != Activity.RESULT_OK ||
            projectionData == null
        ) {

            Log.e(
                TAG,
                "Invalid MediaProjection data"
            )

            stopSelf()
            return START_NOT_STICKY
        }

        if (!isCapturing) {

            startProjection(
                resultCode,
                projectionData
            )
        }

        return START_NOT_STICKY
    }

    private fun startProjection(
        resultCode: Int,
        projectionData: Intent
    ) {

        Log.d(
            TAG,
            "Starting MediaProjection"
        )

        val metrics =
            DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager
            .defaultDisplay
            .getRealMetrics(metrics)

        screenWidth =
            metrics.widthPixels

        screenHeight =
            metrics.heightPixels

        screenDensity =
            metrics.densityDpi

        Log.d(
            TAG,
            "Screen: ${screenWidth}x${screenHeight}, density=$screenDensity"
        )

        mediaProjection =
            projectionManager.getMediaProjection(
                resultCode,
                projectionData
            )

        if (mediaProjection == null) {

            Log.e(
                TAG,
                "MediaProjection is null"
            )

            stopSelf()
            return
        }

        mediaProjection?.registerCallback(
            projectionCallback,
            handler
        )

        createImageReader()

        createVirtualDisplay()

        isCapturing = true

        FloatingTrackerService.setScanning(true)

        Log.d(
            TAG,
            "Item assessment capture started"
        )
    }
    private fun imageToBitmap(
        image: android.media.Image
    ): Bitmap? {

        val plane =
            image.planes.firstOrNull()
                ?: return null

        val buffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        val rowPadding =
            rowStride -
                    pixelStride *
                    image.width

        val bitmapWidth =
            image.width +
                    rowPadding / pixelStride

        val bitmap =
            Bitmap.createBitmap(
                bitmapWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )

        buffer.rewind()

        bitmap.copyPixelsFromBuffer(
            buffer
        )

        if (bitmapWidth != image.width) {

            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                image.width,
                image.height
            ).also {

                bitmap.recycle()
            }
        }

        return bitmap
    }

    private fun frameFingerprint(
        bitmap: Bitmap
    ): IntArray {

        val cells = 16

        val scaled =
            Bitmap.createScaledBitmap(
                bitmap,
                cells,
                cells,
                true
            )

        val pixels =
            IntArray(cells * cells)

        scaled.getPixels(
            pixels,
            0,
            cells,
            0,
            0,
            cells,
            cells
        )

        scaled.recycle()

        return pixels
    }

    private fun createImageReader() {

        imageReader =
            ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->

                val image =
                    reader.acquireLatestImage()
                        ?: return@setOnImageAvailableListener

                var bitmap: Bitmap? = null

                try {

                    bitmap =
                        imageToBitmap(image)
                            ?: return@setOnImageAvailableListener

                    val cropHeight =
                        (bitmap.height * ASSESSMENT_CROP_BOTTOM_RATIO)
                            .toInt()

                    val cropped =
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            cropHeight
                        )

                    Log.d(
                        TAG,
                        "Assessment crop: " +
                                "${cropped.width}x${cropped.height}"
                    )

                    val fingerprint =
                        frameFingerprint(cropped)

                    var accepted = false

                    synchronized(pendingFrameLock) {

                        when {


                            fingerprint.contentEquals(
                                lastFrameFingerprint
                            ) -> {
                                Log.d(
                                    TAG,
                                    "Frame unchanged - skipping"
                                )
                            }

                            processingFrame.get() -> {
                                Log.d(
                                    TAG,
                                    "Busy - storing newest frame"
                                )

                                lastFrameFingerprint =
                                    fingerprint

                                pendingBitmap?.recycle()

                                pendingBitmap = cropped

                                accepted = true
                            }

                            else -> {
                                lastFrameFingerprint =
                                    fingerprint

                                processingFrame.set(true)

                                accepted = true

                                runAssessment(cropped)
                            }
                        }
                    }

                    if (!accepted) {
                        cropped.recycle()
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed to capture screenshot",
                        e
                    )

                    processingFrame.set(false)

                } finally {

                    image.close()

                    bitmap?.let {

                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                }
            },
            captureHandler
        )
    }

    private fun runAssessment(cropped: Bitmap) {

        assessmentExecutor.execute {

            try {

                if (!::pipeline.isInitialized) {

                    pipeline =
                        ItemAssessmentPipeline
                            .ItemAssessmentManager
                            .getPipeline(
                                applicationContext
                            )
                }

                val result =
                    pipeline.processBitmap(cropped)

                handleAssessmentResult(result)

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Background assessment failed",
                    e
                )

            } finally {

                if (!cropped.isRecycled) {
                    cropped.recycle()
                }

                val next = synchronized(pendingFrameLock) {

                    val drained = pendingBitmap
                    pendingBitmap = null
                    processingFrame.set(drained != null)
                    drained
                }

                if (next != null) {
                    runAssessment(next)
                }
            }
        }
    }

    private fun handleAssessmentResult(
        result: ItemAssessmentPipeline.PipelineResult
    ) {

        if (
            result.item == null ||
            result.assessment == null
        ) {

            Log.d(
                TAG,
                "No item detected"
            )

            return
        }

        val assessment =
            result.assessment

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "ITEM DETECTED"
        )

        Log.d(
            TAG,
            "ITEM: ${assessment.itemName}"
        )

        Log.d(
            TAG,
            "LEVEL: ${assessment.upgradeLevel}"
        )

        if (
            assessment.exactQuality != null
        ) {

            Log.d(
                TAG,
                "QUALITY: %.2f%%".format(
                    assessment.exactQuality * 100.0
                )
            )

        } else {

            Log.d(
                TAG,
                "QUALITY RANGE: %.2f%% - %.2f%%".format(
                    assessment.qualityRangeMin * 100.0,
                    assessment.qualityRangeMax * 100.0
                )
            )
        }

        Log.d(
            TAG,
            "========================================"
        )

        ItemQualityOverlayService.showAssessment(
            context = applicationContext,
            assessment = assessment
        )
    }

    private fun createVirtualDisplay() {

        Log.d(
            TAG,
            "Creating VirtualDisplay"
        )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "ItemAssessmentCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

        if (virtualDisplay == null) {

            Log.e(
                TAG,
                "Failed to create VirtualDisplay"
            )

            stopSelf()
        }
    }

    private fun stopCapture() {

        synchronized(pendingFrameLock) {
            pendingBitmap?.recycle()
            pendingBitmap = null
        }

        if (!isCapturing) {
            return
        }

        Log.d(
            TAG,
            "Stopping item assessment capture"
        )

        isCapturing = false

        FloatingTrackerService.setScanning(false)


        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.unregisterCallback(
            projectionCallback
        )

        mediaProjection?.stop()
        mediaProjection = null

        processingFrame.set(false)
    }

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {

                Log.d(
                    TAG,
                    "MediaProjection callback onStop"
                )

                stopCapture()
            }
        }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Item Assessment",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Screen capture for item assessment"

            val manager =
                getSystemService(
                    NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun startForegroundServiceNotification() {

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                Notification.Builder(
                    this,
                    CHANNEL_ID
                )

            } else {

                Notification.Builder(this)
            }
                .setContentTitle(
                    "Fight Tracker"
                )
                .setContentText(
                    "Item assessment is running"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "===== ItemAssessmentCaptureService onDestroy ====="
        )

        stopCapture()

        assessmentExecutor.shutdownNow()
        captureHandler.removeCallbacksAndMessages(null)

        captureThread.quitSafely()
        FloatingTrackerService.setScanning(false)

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    private val windowManager: android.view.WindowManager
        get() =
            getSystemService(
                WINDOW_SERVICE
            ) as android.view.WindowManager
}