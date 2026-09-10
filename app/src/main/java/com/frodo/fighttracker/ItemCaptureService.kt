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

class ItemCaptureService : Service() {

    companion object {


        private const val TAG = "ITEM_CAPTURE"

        private const val CHANNEL_ID =
            "item_capture"

        private const val NOTIFICATION_ID =
            3001

        private const val SCAN_INTERVAL =
            5000L
    }

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader

    private val handler =
        Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var processing = false

    private val scanRunnable =
        object : Runnable {

            override fun run() {

                captureScreenshot()

                handler.postDelayed(
                    this,
                    SCAN_INTERVAL
                )
            }
        }

    override fun onCreate() {

        super.onCreate()

        Log.d(TAG, "ItemCaptureService created")

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        val metrics =
            resources.displayMetrics

        screenWidth =
            metrics.widthPixels

        screenHeight =
            metrics.heightPixels

        screenDensity =
            metrics.densityDpi
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode =
            intent?.getIntExtra(
                "resultCode",
                -1
            ) ?: -1

        val data =
            intent?.getParcelableExtra<Intent>(
                "data"
            )

        if (
            resultCode != -1 &&
            data != null
        ) {

            startProjection(
                resultCode,
                data
            )
        }

        return START_NOT_STICKY
    }

    private fun startProjection(
        resultCode: Int,
        data: Intent
    ) {

        Log.d(
            TAG,
            "Starting MediaProjection"
        )

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            manager.getMediaProjection(
                resultCode,
                data
            )

        imageReader =
            ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

        virtualDisplay =
            mediaProjection.createVirtualDisplay(
                "FightTrackerItemCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

        Log.d(
            TAG,
            "VirtualDisplay created: ${screenWidth}x${screenHeight}"
        )

        handler.removeCallbacks(scanRunnable)

        handler.post(scanRunnable)
    }

    private fun captureScreenshot() {

        if (processing) {
            Log.d(
                TAG,
                "Previous screenshot still processing"
            )
            return
        }

        val image =
            imageReader.acquireLatestImage()
                ?: run {
                    Log.d(
                        TAG,
                        "No image available"
                    )
                    return
                }

        processing = true

        try {

            val plane =
                image.planes[0]

            val buffer =
                plane.buffer

            val pixelStride =
                plane.pixelStride

            val rowStride =
                plane.rowStride

            val rowPadding =
                rowStride -
                        pixelStride * screenWidth

            val bitmapWidth =
                screenWidth +
                        rowPadding / pixelStride

            val bitmap =
                Bitmap.createBitmap(
                    bitmapWidth,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )

            bitmap.copyPixelsFromBuffer(buffer)

            image.close()

            val croppedBitmap =
                if (bitmapWidth != screenWidth) {

                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        screenWidth,
                        screenHeight
                    ).also {
                        bitmap.recycle()
                    }

                } else {
                    bitmap
                }

            Log.d(
                TAG,
                "Screenshot captured: ${croppedBitmap.width}x${croppedBitmap.height}"
            )

            assessItem(
                croppedBitmap
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Screenshot failed",
                e
            )

            image.close()

            processing = false
        }
    }

    private fun assessItem(bitmap: Bitmap) {

        try {

            Log.d(
                TAG,
                "Sending screenshot to ItemAssessmentPipeline"
            )


            val pipeline =
                ItemAssessmentPipeline(this)


            Log.d(
                TAG,
                "Item screenshot ready for assessment"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Item assessment failed",
                e
            )

        } finally {

            bitmap.recycle()

            processing = false
        }
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "Stopping ItemCaptureService"
        )

        handler.removeCallbacks(
            scanRunnable
        )

        if (::virtualDisplay.isInitialized) {
            virtualDisplay.release()
        }

        if (::imageReader.isInitialized) {
            imageReader.close()
        }

        if (::mediaProjection.isInitialized) {
            mediaProjection.stop()
        }

        FloatingTrackerService.setScanning(false)

        super.onDestroy()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Item Scanner",
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

    private fun createNotification(): Notification {

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
                    "Item scanner is running"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(
                    "Fight Tracker"
                )
                .setContentText(
                    "Item scanner is running"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .build()
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}