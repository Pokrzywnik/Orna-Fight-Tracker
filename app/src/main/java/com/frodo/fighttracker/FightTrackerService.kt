package com.frodo.fighttracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
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

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fight Tracker Running")
                .setContentText("Tracking rewards...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "STOP",
                    stopPendingIntent
                )
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

    private fun removeIconsAndEnhance(src: Bitmap): Bitmap {

        val width = src.width
        val height = src.height

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {

                val pixel = src.getPixel(x, y)

                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                //  keep only near-white pixels
                val isWhite =
                    r > 170 && g > 170 && b > 170

                val isPureWhite =
                    r > 245 &&
                    g > 245 &&
                    b > 245

                if (isWhite) {
                    out.setPixel(x, y, android.graphics.Color.WHITE)
                } else {
                    out.setPixel(x, y, android.graphics.Color.BLACK)
                }
            }
        }

        return out
    }

    private fun scanScreen() {

        val image: Image =
            imageReader?.acquireLatestImage() ?: return

        val plane = image.planes[0]

        val buffer: ByteBuffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding =
            rowStride - pixelStride * image.width

        val fullBitmap =
            Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )

        fullBitmap.copyPixelsFromBuffer(buffer)

        //  crop only reward region (bottom + right side)
        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap,
            (fullBitmap.width * 0.25).toInt(),   // skip left icons completely
            (fullBitmap.height * 0.35).toInt(),  // skip fight log
            (fullBitmap.width * 0.75).toInt(),   // keep right side
            (fullBitmap.height * 0.65).toInt()   // reward block
        )

        image.close()


        val scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            croppedBitmap.width / 2,
            croppedBitmap.height / 2,
            false
        )

        val cleanedBitmap = removeIconsAndEnhance(scaledBitmap)

        val inputImage =
            InputImage.fromBitmap(cleanedBitmap, 0)



        Log.d(
            "OCR_BITMAP",
            "Processed bitmap: ${cleanedBitmap.width}x${cleanedBitmap.height}"
        )


        runAssessOCR(fullBitmap)


        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->

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
                    text.contains("DEFEATED") ||
                    text.contains("DUNGEON COMPLETE") ||
                    text.contains("COMPLETE")
                ) {
                    parseRewards(text)
                }
                fullBitmap.recycle()
                croppedBitmap.recycle()
                scaledBitmap.recycle()
                cleanedBitmap.recycle()
            }
            .addOnFailureListener { e ->
                Log.e("OCR_RAW", "OCR FAILED", e)
            }


    }

    private fun runAssessOCR(bitmap: Bitmap) {
        Log.d("ASSESS", "runAssessOCR called")
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width * 0.15).toInt(),
            (bitmap.height * 0.10).toInt(),
            (bitmap.width * 0.70).toInt(),
            (bitmap.height * 0.75).toInt()
        )

        Log.d(
            "ASSESS",
            "crop=${cropped.width}x${cropped.height}"
        )
        val input = InputImage.fromBitmap(cropped, 0)

        recognizer.process(input)
            .addOnSuccessListener { result ->

                Log.d(
                    "ASSESS_RAW",
                    "================ FULL OCR FRAME ================"
                )

                Log.d(
                    "ASSESS_RAW",
                    result.text
                )
                val parsed = AssessParser.parse(result.text)
                Log.d(
                    "ASSESS",
                    "parsed=$parsed"
                )
                parsed?.let { evaluateItem(it) }

                cropped.recycle()
            }
            .addOnFailureListener {
                cropped.recycle()
            }
    }

    private fun evaluateItem(item: ParsedItem) {

        Log.d("ASSESS_ITEM", "name=${item.name}")

        val id = normalize(item.name)

        Log.d("ASSESS_ITEM", "normalized=$id")

        val base = CodexRepository.get(id)

        Log.d("ASSESS_ITEM", "repository result=$base")

        if (base == null) {
            Log.d("ASSESS_ITEM", "ITEM NOT FOUND")
            return
        }

        val qualityRange =
            estimateQualityRange(item, base)

        updateAssessOverlay(
            item.name,
            qualityRange
        )
    }

    private fun normalize(name: String): String {

        return name
            .lowercase()
            .replace("legendary ", "")
            .replace("ornate ", "")
            .replace("famed ", "")
            .replace("superior ", "")
            .replace("poor ", "")
            .replace("broken ", "")
            .replace("common ", "")
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace("'", "")
            .replace(" ", "-")
            .replace(Regex("[^a-z0-9-]"), "")
    }
    private fun estimateQualityRange(
        item: ParsedItem,
        base: CodexItem
    ): String {

        val attack = item.stats["att"] ?: return "???"
        val baseAttack = base.stats["attack"] ?: return "???"

        Log.d("QUALITY", "ocrAttack=$attack")
        Log.d("QUALITY", "baseAttack=$baseAttack")

        var foundMin: Int? = null
        var foundMax: Int? = null

        for (q in 70..200) {

            val calculated =
                kotlin.math.ceil(baseAttack * (q / 100.0)).toInt()

            if (calculated == attack) {

                if (foundMin == null)
                    foundMin = q

                foundMax = q
            }
        }

        Log.d("QUALITY", "foundMin=$foundMin foundMax=$foundMax")

        if (foundMin == null || foundMax == null)
            return "???"

        return if (foundMin == foundMax)
            "$foundMin%"
        else
            "$foundMin-$foundMax%"
    }

    private fun updateAssessOverlay(
        itemName: String,
        qualityRange: String
    ) {

        AssessState.lastOverlay =
            "$itemName\n$qualityRange"

        AssessState.lastUpdate =
            System.currentTimeMillis()

        AssessState.visible = true

        AuraNotifier.refresh?.invoke()
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
                clean.contains("tower shard") ||
                clean.contains("tower shards")
            ) {
                sessionShards += extractNumber(clean)
            }

            if (clean.contains("gold") && !clean.contains("kingdom")) {
                sessionGold += extractNumber(clean)
            }

            if (clean.contains("orns")) {
                sessionOrns += extractNumber(clean)
            }

            if (clean.contains("experience") || clean.contains("party xp")) {
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
                    "Fight Tracker",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}