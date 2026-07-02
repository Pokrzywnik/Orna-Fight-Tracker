package com.frodo.fighttracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer



class FightTrackerService : Service() {

    companion object {
        const val CHANNEL_ID = "fight_tracker_channel"
        var startTime = 0L
    }

    private var totalGold = 0L
    private var totalOrns = 0L
    private var totalExp = 0L
    private var totalShards = 0L

    private var lastShards = -1L
    private var lastGold = -1L
    private var lastOrns = -1L
    private var lastExp = -1L


    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        createNotificationChannel()

        val stopIntent = Intent(this, FightTrackerService::class.java)
        stopIntent.action = "STOP"

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

        val overlayIntent = Intent(this, CropOverlayService::class.java)

        val overlayPending = PendingIntent.getService(
            this,
            2,
            overlayIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fight Tracker Running")
                .setContentText("Tracking rewards...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(
                    android.R.drawable.ic_menu_crop,
                    "SET CROP",
                    overlayPending
                )
//                .addAction(
//                    android.R.drawable.ic_media_pause,
//                    "STOP",
//                    stopPendingIntent
//                )
                .build()

        startForeground(1, notification)

        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode =
            intent?.getIntExtra("resultCode", -1) ?: -1

        val data =
            intent?.getParcelableExtra<Intent>("data")

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            projectionManager.getMediaProjection(resultCode, data!!)

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            },
            Handler(Looper.getMainLooper())
        )
        startTime = System.currentTimeMillis()
        startCapture()

        return START_STICKY
    }

    private fun startCapture() {
        val interval = SettingsStore.getScanInterval(this@FightTrackerService)

        val metrics = resources.displayMetrics

        imageReader =
            ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "FightTracker",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

        handler.postDelayed(object : Runnable {

            override fun run() {

                scanScreen()

                handler.postDelayed(this, interval)
            }

        }, interval)
    }

//    private fun removeIconsAndEnhance(src: Bitmap): Bitmap {
//
//        val width = src.width
//        val height = src.height
//
//        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//
//        val pixels = IntArray(width * height)
//
//        src.getPixels(pixels, 0, width, 0, 0, width, height)
//
//        for (i in pixels.indices) {
//
//            val pixel = pixels[i]
//
//            val r = (pixel shr 16) and 0xff
//            val g = (pixel shr 8) and 0xff
//            val b = pixel and 0xff
//
//            val isWhite =
//                r > 170 &&
//                        g > 170 &&
//                        b > 170
//
//            val isPureWhite =
//                r > 245 &&
//                        g > 245 &&
//                        b > 245
//
//            pixels[i] =
//                if (isWhite)
//                    android.graphics.Color.WHITE
//                else
//                    android.graphics.Color.BLACK
//        }
//
//        out.setPixels(pixels, 0, width, 0, 0, width, height)
//
//        return out
//    }

    private fun applyUserCrop(fullBitmap: Bitmap): Bitmap {

        val rect = CropStore.load(this)

        // if user didn't set crop → fallback to full screen
        if (rect == null) return fullBitmap

        val left = (fullBitmap.width * rect.left).toInt()
        val top = (fullBitmap.height * rect.top).toInt()
        val right = (fullBitmap.width * rect.right).toInt()
        val bottom = (fullBitmap.height * rect.bottom).toInt()

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        Log.d("OCR_CROP", "User crop: L=$left T=$top W=$width H=$height")

        return Bitmap.createBitmap(fullBitmap, left, top, width, height)
    }

    private fun scanScreen() {

        val image: Image =
            imageReader?.acquireLatestImage() ?: return

        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val fullBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        fullBitmap.copyPixelsFromBuffer(buffer)
        image.close()

        // -----------------------------
        // 1. LOAD USER CROP (0..1)
        // -----------------------------
        val savedCrop = CropStore.load(this)

        val crop = savedCrop ?: RectF(0.2f, 0.2f, 0.8f, 0.8f)

        val left = (crop.left * fullBitmap.width).toInt()
        val top = (crop.top * fullBitmap.height).toInt()
        val right = (crop.right * fullBitmap.width).toInt()
        val bottom = (crop.bottom * fullBitmap.height).toInt()

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap,
            left,
            top,
            width,
            height
        )

        // -----------------------------
        // 2. OPTIONAL SCALING
        // -----------------------------
        val scaling =
            if (SettingsStore.getHighQualityOcr(this)) 1 else 2

        val scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            croppedBitmap.width / scaling,
            croppedBitmap.height / scaling,
            false
        )

        // -----------------------------
        // 3. OCR INPUT
        // -----------------------------
        val inputImage = InputImage.fromBitmap(scaledBitmap, 0)

        Log.d("OCR_BITMAP", "Full: ${fullBitmap.width}x${fullBitmap.height}")
        Log.d("OCR_BITMAP", "Crop: L=$left T=$top W=$width H=$height")
        Log.d("OCR_BITMAP", "Final OCR bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->

                if (DebugController.enabled) {
                    OcrDebugBuffer.lastText = visionText.text
                }

                Log.d("OCR_RAW", "================ OCR FRAME ================")
                Log.d("OCR_RAW", visionText.text)

                val text = visionText.text.lowercase()

                if (
                    text.contains("victory") ||
                    text.contains("experience") ||
                    text.contains("party") ||
                    text.contains("orns") ||
                    text.contains("gold") ||
                    text.contains("here's what you found") ||
                    text.contains("heres what you found") ||
                    text.contains("defeated") ||
                    text.contains("dungeon complete") ||
                    text.contains("boss defeated") ||
                    text.contains("complete") ||
                    text.contains("zwcięstwo") ||
                    text.contains("otrzymano")
                ) {
                    parseRewards(text)
                }

                fullBitmap.recycle()
                croppedBitmap.recycle()
                scaledBitmap.recycle()
            }
            .addOnFailureListener { e ->
                Log.e("OCR_RAW", "OCR FAILED", e)
            }
    }

    private fun parseRewards(text: String) {

        val lines = text.lines()

        var sessionGold = 0L
        var sessionOrns = 0L
        var sessionExp = 0L
        var sessionShards = 0L

        for (line in lines) {

            val clean = line.replace(",", "").lowercase()


            if (
                clean.contains("tower shard") || clean.contains("tower shards") ||                                                //english
                clean.contains("fragmentos da torre") ||                                                                                //portuguese
                clean.contains("fragmenty wież") || clean.contains("fragmenty wieź") || clean.contains("fragmenty wiez")    //polish
            ) {
                sessionShards += extractNumber(clean)
            }

            if ((clean.contains("gold") && !clean.contains("kingdom")) ||       //english
                ((clean.contains("ouro") || clean.contains("0uro")) && !clean.contains("reino")) ||         //portuguese
                ((clean.contains("zlota") || clean.contains("złota") || (clean.contains("złtota")))  && !clean.contains("królestwa"))) {    //polish
                sessionGold += extractNumber(clean)
            }

            if (clean.contains("orns") || clean.contains("0rns") || clean.contains("0rn5") || clean.contains("orn5") ||                                     //english+portuguese
                clean.contains("ornów") || clean.contains("orndw") || clean.contains("ornỐW") || clean.contains("orn")) {   //polish
                sessionOrns += extractNumber(clean)
            }

            if (clean.contains("xp") || clean.contains("party xp") ||                                                                    //english
                clean.contains("doświadczenia") || clean.contains("doświadczenia drużynowego") ||  clean.contains("drużynowego")    //polish
            ) {
                sessionExp += extractNumber(clean)
            }
        }

        // duplicate check
        if (
            sessionGold == lastGold &&
            sessionOrns == lastOrns &&
            sessionExp == lastExp &&
            sessionShards == lastShards
        ) {
            return
        }

        // store last seen values
        lastGold = sessionGold
        lastOrns = sessionOrns
        lastExp = sessionExp
        lastShards = sessionShards

        // add ONLY new results
        totalGold += sessionGold
        totalOrns += sessionOrns
        totalExp += sessionExp
        totalShards += sessionShards

        FightState.gold = totalGold
        FightState.orns = totalOrns
        FightState.exp = totalExp
        FightState.shards = totalShards

        android.util.Log.d(
            "FIGHT_TOTALS",
            "STATE UPDATE -> EXP=$totalExp GOLD=$totalGold ORNS=$totalOrns SHARDS=$totalShards"
        )
    }

    private fun extractNumber(text: String): Long {

        // keep digits only
        val digits = text.filter { it.isDigit() }

        return digits.toLongOrNull() ?: 0L
    }

    override fun onDestroy() {

        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Materials Forecast",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}