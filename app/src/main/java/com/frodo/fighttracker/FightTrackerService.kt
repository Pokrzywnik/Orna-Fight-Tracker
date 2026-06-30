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

        val pixels = IntArray(width * height)

        // Read all pixels in one native call
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {

            val pixel = pixels[i]

            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            val brightness = (r + g + b) / 3

            pixels[i] =
                if (brightness > 150)
                    android.graphics.Color.WHITE
                else
                    android.graphics.Color.BLACK
        }

        // Write all pixels back in one native call
        out.setPixels(pixels, 0, width, 0, 0, width, height)

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
//        val croppedBitmap = Bitmap.createBitmap(
//            fullBitmap,
//            (fullBitmap.width * 0.22).toInt(),   // skip left icons completely
//            (fullBitmap.height * 0.35).toInt(),  // skip fight log
//            (fullBitmap.width * 0.56).toInt(),   // keep right side
//            (fullBitmap.height * 0.45).toInt()   // reward block
//        )

        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap,
            (fullBitmap.width * 0.22).toInt(),   // start at 22% width
            (fullBitmap.height * 0.10).toInt(),  // Starts at 10% height
            (fullBitmap.width * 0.56).toInt(),   // covers right 56% width
            (fullBitmap.height * 0.512).toInt()  // Covers down 51,2% height
        )

        image.close()


        val scaling =
            if (SettingsStore.getHighQualityOcr(this))
                1
            else
                2

        val scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            croppedBitmap.width / scaling,
            croppedBitmap.height / scaling,
            false
        )

        val cleanedBitmap = removeIconsAndEnhance(scaledBitmap)

        val inputImage =
            InputImage.fromBitmap(cleanedBitmap, 0)



        Log.d(
            "OCR_BITMAP",
            "Processed bitmap: ${cleanedBitmap.width}x${cleanedBitmap.height}"
        )




        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->

                Log.d("OCR_RAW", "================ OCR FRAME ================")
                Log.d("OCR_RAW", visionText.text)

                val text = visionText.text.lowercase()

                if (
                    text.contains("victory") ||
                    text.contains("VITORIA") ||
                    text.contains("VÍCTORY!") ||
                    text.contains("experience") ||
                    text.contains("party") ||
                    text.contains("orns") ||
                    text.contains("gold") ||
                    text.contains("here's what you found") ||
                    text.contains("heres what you found") ||
                    text.contains("DEFEATED") ||
                    text.contains("DUNGEON COMPLETE") ||
                    text.contains("BOSS DEFEATED") ||
                    text.contains("Aqui está o que") ||
                    text.contains("Aqui está") ||
                    text.contains("ZWYCIĘSTWO") ||
                    text.contains("Otrzymano") ||
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

            if (clean.contains("orns") || clean.contains("0rns") ||                                     //english+portuguese
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