    package com.frodo.fighttracker
    
    import android.app.Notification
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.app.Service
    import android.content.Context
    import android.content.Intent
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
    import android.util.Log
    import android.app.Activity
    
    class ItemAssessmentService : Service() {
    
        companion object {
    
            private const val TAG =
                "ItemAssessmentService"
    
            private const val CHANNEL_ID =
                "item_assessment"
    
            private const val NOTIFICATION_ID =
                2002
    
            private const val SCAN_INTERVAL =
                5000L
        }
    
        private lateinit var projectionManager:
                MediaProjectionManager
    
        private var mediaProjection:
                MediaProjection? = null
    
        private var virtualDisplay:
                VirtualDisplay? = null
    
        private var imageReader:
                ImageReader? = null
    
        private val handler =
            Handler(Looper.getMainLooper())
    
        private lateinit var pipeline:
                ItemAssessmentPipeline
    
        private var processing = false
    
        private val scanRunnable =
            object : Runnable {
    
                override fun run() {
    
                    captureAndAssess()
    
                    handler.postDelayed(
                        this,
                        SCAN_INTERVAL
                    )
                }
            }
    
        override fun onCreate() {
    
            super.onCreate()
            FloatingTrackerService.setScanning(true)
            Log.d(
                TAG,
                "===== ITEM ASSESSMENT SERVICE START ====="
            )
    
            createNotificationChannel()
    
            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )
    
            projectionManager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager
    
            pipeline =
                ItemAssessmentPipeline
                    .ItemAssessmentManager
                    .getPipeline(this)
        }
    
        override fun onStartCommand(
            intent: Intent?,
            flags: Int,
            startId: Int
        ): Int {
    
            Log.d(TAG, "========================================")
            Log.d(TAG, "ItemAssessmentService onStartCommand")
            Log.d(TAG, "intent=$intent")
    
            val resultCode =
                intent?.getIntExtra(
                    "resultCode",
                    -1
                ) ?: -1
    
            val data: Intent? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    
                    intent?.getParcelableExtra(
                        "data",
                        Intent::class.java
                    )
    
                } else {
    
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra<Intent>(
                        "data"
                    )
                }
    
            Log.d(
                TAG,
                "MediaProjection resultCode=$resultCode"
            )
    
            Log.d(
                TAG,
                "MediaProjection data=${data != null}"
            )
    
            if (
                resultCode != Activity.RESULT_OK ||
                data == null
            ) {
    
                Log.e(
                    TAG,
                    "Invalid MediaProjection data: resultCode=$resultCode data=${data != null}"
                )
    
                stopSelf()
    
                return START_NOT_STICKY
            }
    
            if (data == null) {
    
                Log.e(
                    TAG,
                    "Invalid MediaProjection data: Intent is null"
                )
    
                stopSelf()
    
                return START_NOT_STICKY
            }
    
            startProjection(
                resultCode,
                data
            )
    
            return START_NOT_STICKY
        }
    
        private fun startProjection(
            resultCode: Int,
            data: Intent
        ) {
    
            if (mediaProjection != null) {
    
                Log.d(
                    TAG,
                    "Projection already running"
                )
    
                return
            }
    
            mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    data
                )
    
            if (mediaProjection == null) {
    
                Log.e(
                    TAG,
                    "Could not create MediaProjection"
                )
    
                stopSelf()
    
                return
            }
    
            mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
    
                    override fun onStop() {
    
                        Log.d(
                            TAG,
                            "MediaProjection stopped"
                        )
    
                        stopSelf()
                    }
                },
                handler
            )
    
            val metrics =
                resources.displayMetrics
    
            val width =
                metrics.widthPixels
    
            val height =
                metrics.heightPixels
    
            val density =
                metrics.densityDpi
    
            Log.d(
                TAG,
                "DISPLAY: ${width}x${height} density=$density"
            )
    
            imageReader =
                ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    2
                )
    
            virtualDisplay =
                mediaProjection!!.createVirtualDisplay(
                    "ItemAssessment",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null,
                    handler
                )
    
            Log.d(
                TAG,
                "VirtualDisplay created"
            )
    
            handler.postDelayed(
                scanRunnable,
                1000L
            )
        }
    
        private fun captureAndAssess() {
    
            if (processing) {
    
                Log.d(
                    TAG,
                    "Previous scan still processing"
                )
    
                return
            }
    
            val reader =
                imageReader
                    ?: return
    
            val image =
                reader.acquireLatestImage()
                    ?: run {
    
                        Log.d(
                            TAG,
                            "No image available"
                        )
    
                        return
                    }
    
            processing = true
    
            try {
    
                val bitmap =
                    imageToBitmap(image)
    
                image.close()
    
                if (bitmap == null) {
    
                    Log.e(
                        TAG,
                        "Could not convert Image to Bitmap"
                    )
    
                    return
                }
    
                Log.d(
                    TAG,
                    "SCREENSHOT: ${bitmap.width}x${bitmap.height}"
                )
    
                val result =
                    pipeline.processBitmap(
                        bitmap
                    )
    
                if (
                    result.item != null &&
                    result.assessment != null
                ) {
    
                    val assessment =
                        result.assessment
    
                    Log.d(
                        TAG,
                        "================================"
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
                        "================================"
                    )
    
                } else {
    
                    Log.d(
                        TAG,
                        "No item detected"
                    )
                }
    
                bitmap.recycle()
    
            } catch (e: Exception) {
    
                Log.e(
                    TAG,
                    "Error during item assessment",
                    e
                )
    
            } finally {
    
                processing = false
            }
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
    
        private fun createNotificationChannel() {
    
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
    
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Item Assessment",
                        NotificationManager.IMPORTANCE_LOW
                    )
    
                val manager =
                    getSystemService(
                        NotificationManager::class.java
                    )
    
                manager.createNotificationChannel(
                    channel
                )
            }
        }
    
        private fun createNotification():
                Notification {
    
            return if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
    
                Notification.Builder(
                    this,
                    CHANNEL_ID
                )
                    .setContentTitle(
                        "Fight Tracker"
                    )
                    .setContentText(
                        "Scanning items..."
                    )
                    .setSmallIcon(
                        android.R.drawable.ic_menu_search
                    )
                    .setOngoing(true)
                    .build()
    
            } else {
    
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle(
                        "Fight Tracker"
                    )
                    .setContentText(
                        "Scanning items..."
                    )
                    .setSmallIcon(
                        android.R.drawable.ic_menu_search
                    )
                    .setOngoing(true)
                    .build()
            }
        }
    
        override fun onDestroy() {
    
            Log.d(
                TAG,
                "===== ITEM ASSESSMENT SERVICE STOP ====="
            )
    
            handler.removeCallbacks(
                scanRunnable
            )
    
            virtualDisplay?.release()
            virtualDisplay = null
    
            imageReader?.close()
            imageReader = null
    
            mediaProjection?.stop()
            mediaProjection = null
    
    
            FloatingTrackerService.setScanning(false)
            super.onDestroy()
        }
    
        override fun onBind(
            intent: Intent?
        ): IBinder? = null
    }