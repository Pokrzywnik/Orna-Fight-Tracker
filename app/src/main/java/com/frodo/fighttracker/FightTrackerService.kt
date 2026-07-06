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
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min


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


    private var rewardScreenSeen = false
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


    private fun detectLeftEdge(bitmap: Bitmap): Int {

        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val score = IntArray(w)

        // Count "text-like" pixels in every column
        for (x in 0 until w) {

            var count = 0

            for (y in 0 until h) {

                val c = pixels[y * w + x]

                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255

                val brightness = (r + g + b) / 3

                if (brightness > 170)
                    count++
            }

            score[x] = count
        }

        // Smooth with moving average
        val smooth = IntArray(w)

        for (x in 0 until w) {

            var sum = 0
            var n = 0

            for (k in max(0, x - 4)..min(w - 1, x + 4)) {
                sum += score[k]
                n++
            }

            smooth[x] = sum / n
        }

        // Average activity
        val avg = smooth.average()

        val threshold = max(8.0, avg * 1.35)

        // Find first sustained block of text
        for (x in 0 until w - 20) {

            var good = true

            for (k in 0 until 20) {

                if (smooth[x + k] < threshold) {
                    good = false
                    break
                }
            }

            if (good)
                return x
        }

        return 0
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
    private fun detectXpBookmark(bitmap: Bitmap): Rect? {
        Log.d("BOOKMARK", "detectXpBookmark() called")

        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val searchWidth = w
        val searchHeight = h

        var bestX = -1
        var bestStartY = -1
        var bestRun = 0

        for (x in 0 until searchWidth) {

            var run = 0
            var startY = 0

            for (y in 0 until searchHeight) {


                val c = pixels[y * w + x]

                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255

//                if (r > 140 && g < 120 && b < 120) {              //debug info - show pixels coords
//
//                    Log.d(
//                        "BOOKMARK_PIXEL",
//                        "x=$x y=$y  rgb=($r,$g,$b)"
//                    )
//                }

                val red =
                    r >= 150 &&
                            g <= 70 &&
                            b <= 70 &&
                            r >= g + 70 &&
                            r >= b + 70

                if (red) {

                    if (run == 0)
                        startY = y

                    run++

                    if (run > bestRun) {
                        bestRun = run
                        bestX = x
                        bestStartY = startY
                    }

                } else {

                    run = 0
                }
            }
        }

        Log.d(
            "BOOKMARK",
            "bestRun=$bestRun bestX=$bestX bestY=$bestStartY"
        )

        if (bestRun < 3) {
            Log.d("BOOKMARK", "FAILED bestRun=$bestRun")
            return null
        }

        return Rect(
            bestX,
            bestStartY,
            bestX + 1,
            bestStartY + bestRun
        )
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

        DebugOverlayData.userCropLeft = left
        DebugOverlayData.userCropRight = right
        DebugOverlayData.userCropTop = top
        DebugOverlayData.userCropBottom = bottom

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap,
            left,
            top,
            width,
            height
        )

        // Remember user's crop for debug overlay
        OcrDebugBuffer.userCrop = android.graphics.Rect(
            left,
            top,
            right,
            bottom
        )

    // Detect where text actually begins
        val bookmark = detectXpBookmark(croppedBitmap)

        if (bookmark != null) {

            OcrDebugBuffer.bookmarkRect = Rect(
                left + bookmark.left,
                top + bookmark.top,
                left + bookmark.right,
                top + bookmark.bottom
            )

        } else {

            OcrDebugBuffer.bookmarkRect = null
        }

        var adjustedLeft =
            if (bookmark != null)
                bookmark.centerX() + 8
            else
                max(0, detectLeftEdge(croppedBitmap) - 20)

        adjustedLeft = adjustedLeft.coerceIn(
            0,
            croppedBitmap.width - 20
        )

        DebugOverlayData.detectedLeft = adjustedLeft
        DebugOverlayData.bitmapWidth = croppedBitmap.width

        val finalBitmap = Bitmap.createBitmap(
            croppedBitmap,
            adjustedLeft,
            0,
            croppedBitmap.width - adjustedLeft,
            croppedBitmap.height
        )

        // Debug overlay
        OcrDebugBuffer.detectedLeft =
            left + adjustedLeft

        OcrDebugBuffer.finalCrop =
            android.graphics.Rect(
                left + adjustedLeft,
                top,
                right,
                bottom
            )

        // -----------------------------
        // 2. OPTIONAL SCALING
        // -----------------------------
        val scaling =
            if (SettingsStore.getHighQualityOcr(this)) 1 else 2

        val scaledBitmap = Bitmap.createScaledBitmap(
            finalBitmap,
            finalBitmap.width / scaling,
            finalBitmap.height / scaling,
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

                val isRewardScreen =
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

                if (isRewardScreen) {

                    if (!rewardScreenSeen) {
                        rewardScreenSeen = true
                        parseRewards(text)
                    }

                } else {
                    rewardScreenSeen = false
                }

                fullBitmap.recycle()
                croppedBitmap.recycle()
                finalBitmap.recycle()
                scaledBitmap.recycle()
            }
            .addOnFailureListener { e ->
                Log.e("OCR_RAW", "OCR FAILED", e)
            }
    }

    private fun extractBefore(text: String, keyword: String): Long {

        val index = text.indexOf(keyword)
        if (index == -1) return 0

        val left = text.substring(0, index)

        val match = Regex("""(\d[\d\s.,]*)$""").find(left)
            ?: return 0

        return match.value
            .filter(Char::isDigit)
            .toLongOrNull() ?: 0
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
                sessionOrns += extractBefore(clean, "orns")
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

        // add only new results
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