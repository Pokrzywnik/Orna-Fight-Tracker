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

        /** ML Kit refuses images smaller than 32x32 px. */
        private const val MIN_OCR_SIZE = 32

        /** true = wyłącza automatyczne ucinanie lewej strony (czerwony pixel / ikona gold) */
        private const val DISABLE_LEFT_CROP = true

        @Volatile
        var instance: FightTrackerService? = null

        /** Called from the overlay. Returns false when there is nothing to undo. */
        fun undoLast(): Boolean = instance?.undoLastReward() ?: false
    }

    /** One applied reward (used by the undo button). */
    private data class RewardDelta(
        val gold: Long,
        val orns: Long,
        val exp: Long,
        val shards: Long,
        val proofs: Map<String, Long>
    )

    private val rewardHistory = ArrayDeque<RewardDelta>()

    private var totalGold = 0L
    private var totalOrns = 0L
    private var totalExp = 0L
    private var totalShards = 0L
    private val totalProofs = mutableMapOf<String, Long>()

    private var lastShards = -1L
    private var lastGold = -1L
    private var lastOrns = -1L
    private var lastExp = -1L
    private var lastProofs: Map<String, Long> = emptyMap()

    private var ocrBusy = false

    private var rewardScreenSeen = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        TrackerLogger.startNewSession(this)
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

    /**
     * Loads the user's crop and converts it to pixels.
     *
     * Protects against broken saved crops:
     *  - inverted rectangles (left > right / top > bottom)
     *  - rectangles smaller than ML Kit's 32x32 px minimum
     *
     * In those cases the default 20%-80% crop is used.
     */
    private fun resolveCrop(fullWidth: Int, fullHeight: Int): Rect {

        val saved = CropStore.load(this)

        var l = 0.2f
        var t = 0.2f
        var r = 0.8f
        var b = 0.8f

        if (saved != null) {

            l = min(saved.left, saved.right)
            r = max(saved.left, saved.right)
            t = min(saved.top, saved.bottom)
            b = max(saved.top, saved.bottom)
        }

        var rect = Rect(
            (l * fullWidth).toInt().coerceIn(0, fullWidth - 1),
            (t * fullHeight).toInt().coerceIn(0, fullHeight - 1),
            (r * fullWidth).toInt().coerceIn(1, fullWidth),
            (b * fullHeight).toInt().coerceIn(1, fullHeight)
        )

        if (
            rect.width() < MIN_OCR_SIZE ||
            rect.height() < MIN_OCR_SIZE
        ) {

            Log.w(
                "CROP",
                "Saved crop too small (${rect.width()}x${rect.height()}) - using default"
            )

            rect = Rect(
                (0.2f * fullWidth).toInt(),
                (0.2f * fullHeight).toInt(),
                (0.8f * fullWidth).toInt(),
                (0.8f * fullHeight).toInt()
            )
        }

        return rect
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

    private fun detectXpBookmarklegacy(bitmap: Bitmap): Rect? {
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

    private fun detectXpBookmark(
        bitmap: Bitmap,
        xpRect: Rect
    ): Rect? {

        val strong = findRedBookmark(
            bitmap,
            xpRect,
            false
        )

        if (strong != null)
            return strong


        // weaker red fallback
        return findRedBookmark(
            bitmap,
            xpRect,
            true
        )
    }


    private fun findRedBookmark(
        bitmap: Bitmap,
        xpRect: Rect,
        weak: Boolean
    ): Rect? {
        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // START FROM 0: The absolute left edge of your cropped bitmap
        val searchLeft = 0
        // END RIGHT BEFORE THE TEXT: Leave a small buffer before the characters start
        val searchRight = max(1, xpRect.left - 5)

        val centerY = xpRect.centerY()
        val searchTop = max(0, centerY - 50)
        val searchBottom = min(h, centerY + 50)

        var bestX = -1
        var bestStartY = -1
        var bestRun = 0

        for (x in searchLeft until searchRight) {
            var run = 0
            var startY = 0

            for (y in searchTop until searchBottom) {
                val c = pixels[y * w + x]

                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255

                val red = if (!weak) {
                    r >= 150 && g <= 70 && b <= 70 && r >= g + 70 && r >= b + 70
                } else {
                    r >= 120 && r >= g + 40 && r >= b + 40 && g <= 130 && b <= 130
                }

                if (red) {
                    if (run == 0) startY = y
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

        Log.d("BOOKMARK", "Search Range: Left=$searchLeft Right=$searchRight | weak=$weak run=$bestRun x=$bestX y=$bestStartY")

        if (bestRun < 2) return null

        return Rect(bestX, bestStartY, bestX + 1, bestStartY + bestRun)
    }

    private fun recycleBitmapSafe(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun scanScreen() {

        if (SettingsStore.getLegacyOcr(this)) {
            scanScreenLegacy()
        } else {
            scanScreenModern()
        }
    }

    private fun scanScreenLegacy() {

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
        // 1. LOAD USER CROP (validated)
        // -----------------------------
        val cropRect = resolveCrop(fullBitmap.width, fullBitmap.height)

        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom

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
        val bookmark = detectXpBookmarklegacy(croppedBitmap)

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

        // keep at least MIN_OCR_SIZE px of width
        adjustedLeft = adjustedLeft.coerceIn(
            0,
            (croppedBitmap.width - MIN_OCR_SIZE).coerceAtLeast(0)
        )
        if (DISABLE_LEFT_CROP) adjustedLeft = 0

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
        var scaling =
            if (SettingsStore.getHighQualityOcr(this)) 1 else 2

        // never scale below ML Kit's minimum size
        if (
            finalBitmap.width / scaling < MIN_OCR_SIZE ||
            finalBitmap.height / scaling < MIN_OCR_SIZE
        ) {
            scaling = 1
        }

        if (
            finalBitmap.width < MIN_OCR_SIZE ||
            finalBitmap.height < MIN_OCR_SIZE
        ) {

            Log.w("OCR_BITMAP", "Legacy OCR bitmap too small - skipping frame")

            fullBitmap.recycle()
            croppedBitmap.recycle()
            finalBitmap.recycle()

            return
        }

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

                recycleBitmapSafe(fullBitmap)
                recycleBitmapSafe(croppedBitmap)
                recycleBitmapSafe(finalBitmap)
                recycleBitmapSafe(scaledBitmap)
            }
    }

    private fun scanScreenModern() {

        // do not run next OCR, if previous
        // still doing first or second pass.
        if (ocrBusy) {
            return
        }

        ocrBusy = true

        var fullBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null

        try {

            val image: Image =
                imageReader?.acquireLatestImage()
                    ?: run {
                        ocrBusy = false
                        return
                    }

            try {

                val plane = image.planes[0]
                val buffer: ByteBuffer = plane.buffer

                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding =
                    rowStride - pixelStride * image.width

                fullBitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )

                buffer.rewind()

                fullBitmap!!.copyPixelsFromBuffer(buffer)

            } finally {

                image.close()
            }

            // 1. LOAD USER CROP (validated, min 32x32 px)

            val full = fullBitmap
                ?: run {
                    ocrBusy = false
                    return
                }

            val cropRect =
                resolveCrop(full.width, full.height)

            val left = cropRect.left
            val top = cropRect.top
            val right = cropRect.right
            val bottom = cropRect.bottom

            DebugOverlayData.userCropLeft = left
            DebugOverlayData.userCropRight = right
            DebugOverlayData.userCropTop = top
            DebugOverlayData.userCropBottom = bottom

            val width =
                (right - left).coerceAtLeast(1)

            val height =
                (bottom - top).coerceAtLeast(1)

            croppedBitmap =
                Bitmap.createBitmap(
                    full,
                    left,
                    top,
                    width,
                    height
                )

            // Remember user's crop for debug overlay
            OcrDebugBuffer.userCrop =
                android.graphics.Rect(
                    left,
                    top,
                    right,
                    bottom
                )

            // FIRST OCR PASS - FIND REWARD ROWS

            val firstInput =
                InputImage.fromBitmap(
                    croppedBitmap!!,
                    0
                )

            recognizer.process(firstInput)

                .addOnSuccessListener { firstVisionText ->

                    var finalBitmap: Bitmap? = null
                    var scaledBitmap: Bitmap? = null

                    try {

                        var xpRect: Rect? = null
                        var goldRectFirstPass: Rect? = null

                        // Locate both XP and Gold text lines
                        for (block in firstVisionText.textBlocks) {

                            for (line in block.lines) {

                                val txt =
                                    line.text.lowercase()

                                val box =
                                    line.boundingBox
                                        ?: continue

                                if (box.top < 80)
                                    continue

                                if (
                                    txt.contains("xp") ||
                                    txt.contains("experience") ||
                                    txt.contains("doświadczenia") ||
                                    txt.contains("drużynowego")
                                ) {
                                    xpRect = box
                                }

                                if (
                                    (
                                            txt.contains("gold") &&
                                                    !txt.contains("kingdom")
                                            ) ||
                                    (
                                            (
                                                    txt.contains("ouro") ||
                                                            txt.contains("0uro")
                                                    ) &&
                                                    !txt.contains("reino")
                                            ) ||
                                    (
                                            (
                                                    txt.contains("zlota") ||
                                                            txt.contains("złota") ||
                                                            txt.contains("złtota")
                                                    ) &&
                                                    !txt.contains("królestwa")
                                            )
                                ) {
                                    goldRectFirstPass = box
                                }
                            }
                        }

                        var adjustedLeft = 0

                        // Fallback baseline text pointer if pixel scan misses completely
                        val baseTextLeft =
                            when {
                                goldRectFirstPass != null ->
                                    goldRectFirstPass!!.left

                                xpRect != null ->
                                    xpRect!!.left

                                else ->
                                    0
                            }

                        // If Gold row is identified,
                        // find its yellow icon batch
                        if (goldRectFirstPass != null) {

                            val w =
                                croppedBitmap!!.width

                            val h =
                                croppedBitmap!!.height

                            val pixels =
                                IntArray(w * h)

                            croppedBitmap!!.getPixels(
                                pixels,
                                0,
                                w,
                                0,
                                0,
                                w,
                                h
                            )

                            val searchLeft = 0

                            val searchRight =
                                max(
                                    1,
                                    goldRectFirstPass!!.left - 5
                                )

                            val centerY =
                                goldRectFirstPass!!.centerY()

                            val searchTop =
                                max(
                                    0,
                                    centerY - 30
                                )

                            val searchBottom =
                                min(
                                    h,
                                    centerY + 30
                                )

                            var bestX = -1
                            var bestRun = 0

                            for (x in searchLeft until searchRight) {

                                var run = 0

                                for (y in searchTop until searchBottom) {

                                    val c =
                                        pixels[y * w + x]

                                    val r =
                                        (c shr 16) and 255

                                    val g =
                                        (c shr 8) and 255

                                    val b =
                                        c and 255

                                    // Looking for the vibrant yellow
                                    // color profile of the gold coin/pouch icon
                                    val isYellow =
                                        r >= 160 &&
                                                g >= 140 &&
                                                b <= 90 &&
                                                (r - b) >= 70

                                    if (isYellow) {

                                        run++

                                        if (run > bestRun) {

                                            bestRun = run
                                            bestX = x
                                        }

                                    } else {

                                        run = 0
                                    }
                                }
                            }

                            Log.d(
                                "GOLD_ICON_SCAN",
                                "Scan result: bestX=$bestX run=$bestRun " +
                                        "(Search window: Right=$searchRight)"
                            )

                            TrackerLogger.write(
                                "GOLD_ICON_SCAN",
                                "Scan result: bestX=$bestX run=$bestRun " +
                                        "(Search window: Right=$searchRight)"
                            )

                            if (
                                bestRun >= 2 &&
                                bestX != -1
                            ) {

                                // Found yellow icon:
                                // step right past its center point
                                adjustedLeft =
                                    bestX + 10

                            } else {

                                // Fallback using text boundary directly
                                if (baseTextLeft > 0) {

                                    adjustedLeft =
                                        baseTextLeft + 10

                                } else {

                                    adjustedLeft =
                                        max(
                                            0,
                                            detectLeftEdge(
                                                croppedBitmap!!
                                            ) - 100
                                        )
                                }
                            }

                        } else if (xpRect != null) {

                            // Fallback to text position if Gold
                            // row wasn't caught in first pass
                            adjustedLeft =
                                xpRect!!.left + 10

                        } else {

                            adjustedLeft =
                                max(
                                    0,
                                    detectLeftEdge(
                                        croppedBitmap!!
                                    ) - 20
                                )
                        }

                        // Protect safety boundary constraints.
                        // Keep at least MIN_OCR_SIZE px of width.
                        adjustedLeft =
                            adjustedLeft.coerceIn(
                                0,
                                (croppedBitmap!!.width - MIN_OCR_SIZE)
                                    .coerceAtLeast(0)
                            )
                        if (DISABLE_LEFT_CROP) adjustedLeft = 0

                        DebugOverlayData.detectedLeft =
                            adjustedLeft

                        DebugOverlayData.bitmapWidth =
                            croppedBitmap!!.width

                        finalBitmap =
                            Bitmap.createBitmap(
                                croppedBitmap!!,
                                adjustedLeft,
                                0,
                                croppedBitmap!!.width - adjustedLeft,
                                croppedBitmap!!.height
                            )

                        OcrDebugBuffer.detectedLeft =
                            left + adjustedLeft

                        OcrDebugBuffer.detectedY =
                            if (xpRect != null)
                                top + xpRect!!.centerY()
                            else
                                -1

                        OcrDebugBuffer.finalCrop =
                            Rect(
                                left + adjustedLeft,
                                top,
                                right,
                                bottom
                            )

                        OcrDebugBuffer.bookmarkRect = null

                        // SECOND OCR PASS - REAL TRACKING

                        var scaling =
                            if (
                                SettingsStore.getHighQualityOcr(this)
                            ) {
                                1
                            } else {
                                2
                            }

                        // never scale below ML Kit's minimum size
                        if (
                            finalBitmap!!.width / scaling < MIN_OCR_SIZE ||
                            finalBitmap!!.height / scaling < MIN_OCR_SIZE
                        ) {
                            scaling = 1
                        }

                        if (
                            finalBitmap!!.width < MIN_OCR_SIZE ||
                            finalBitmap!!.height < MIN_OCR_SIZE
                        ) {

                            Log.w(
                                "OCR_RAW",
                                "Second pass bitmap too small " +
                                        "(${finalBitmap!!.width}x${finalBitmap!!.height}) - skipping frame"
                            )

                            recycleBitmapSafe(scaledBitmap)
                            recycleBitmapSafe(finalBitmap)
                            recycleBitmapSafe(croppedBitmap)
                            recycleBitmapSafe(fullBitmap)

                            scaledBitmap = null
                            finalBitmap = null
                            croppedBitmap = null
                            fullBitmap = null

                            ocrBusy = false

                            return@addOnSuccessListener
                        }

                        scaledBitmap =
                            Bitmap.createScaledBitmap(
                                finalBitmap!!,
                                finalBitmap!!.width / scaling,
                                finalBitmap!!.height / scaling,
                                false
                            )

                        val inputImage =
                            InputImage.fromBitmap(
                                scaledBitmap!!,
                                0
                            )

                        recognizer.process(inputImage)

                            .addOnSuccessListener { visionText ->

                                try {

                                    Log.d(
                                        "OCR_BITMAP",
                                        "Full: ${fullBitmap!!.width}x${fullBitmap!!.height}"
                                    )

                                    TrackerLogger.write(
                                        "OCR_BITMAP",
                                        "Full=${fullBitmap!!.width}x${fullBitmap!!.height} " +
                                                "Crop=L=$left T=$top W=$width H=$height " +
                                                "Final=${scaledBitmap!!.width}x${scaledBitmap!!.height}"
                                    )

                                    Log.d(
                                        "OCR_BITMAP",
                                        "Crop: L=$left T=$top W=$width H=$height"
                                    )

                                    TrackerLogger.write(
                                        "OCR_BITMAP",
                                        "Crop: L=$left T=$top W=$width H=$height"
                                    )

                                    Log.d(
                                        "OCR_BITMAP",
                                        "Final OCR bitmap: " +
                                                "${scaledBitmap!!.width}x${scaledBitmap!!.height}"
                                    )

                                    TrackerLogger.write(
                                        "OCR_BITMAP",
                                        "Final OCR bitmap: " +
                                                "${scaledBitmap!!.width}x${scaledBitmap!!.height}"
                                    )

                                    if (DebugController.enabled) {

                                        OcrDebugBuffer.lastText =
                                            visionText.text
                                    }

                                    Log.d(
                                        "OCR_RAW",
                                        "================ OCR FRAME ================"
                                    )

                                    TrackerLogger.write(
                                        "OCR_RAW",
                                        "================ OCR FRAME ================"
                                    )

                                    Log.d(
                                        "OCR_RAW",
                                        visionText.text
                                    )

                                    TrackerLogger.write(
                                        "OCR_RAW",
                                        visionText.text
                                    )

                                    OcrDebugBuffer.xpLeft = -1
                                    OcrDebugBuffer.xpTop = -1
                                    OcrDebugBuffer.ornsLeft = -1
                                    OcrDebugBuffer.ornsTop = -1
                                    OcrDebugBuffer.goldLeft = -1
                                    OcrDebugBuffer.goldTop = -1

                                    for (block in visionText.textBlocks) {

                                        for (line in block.lines) {

                                            // Clean up character
                                            // misidentifications
                                            val txt =
                                                line.text
                                                    .lowercase()
                                                    .replace(
                                                        "0rns",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "0rn5",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "orn5",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "0uro",
                                                        "ouro"
                                                    )

                                            val box =
                                                line.boundingBox
                                                    ?: continue

                                            val screenRect =
                                                Rect(
                                                    left +
                                                            adjustedLeft +
                                                            box.left *
                                                            scaling,

                                                    top +
                                                            box.top *
                                                            scaling,

                                                    left +
                                                            adjustedLeft +
                                                            box.right *
                                                            scaling,

                                                    top +
                                                            box.bottom *
                                                            scaling
                                                )

                                            when {

                                                txt.contains("xp") ||
                                                        txt.contains("experience") ||
                                                        txt.contains("doświadczenia") ||
                                                        txt.contains("drużynowego") -> {

                                                    OcrDebugBuffer.xpLeft =
                                                        screenRect.left

                                                    OcrDebugBuffer.xpTop =
                                                        screenRect.top
                                                }

                                                txt.contains("orns") ||
                                                        txt.contains("orn") ||
                                                        txt.contains("ornów") -> {

                                                    OcrDebugBuffer.ornsLeft =
                                                        screenRect.left

                                                    OcrDebugBuffer.ornsTop =
                                                        screenRect.top
                                                }

                                                txt.contains("gold") ||
                                                        txt.contains("ouro") ||
                                                        txt.contains("złota") -> {

                                                    OcrDebugBuffer.goldLeft =
                                                        screenRect.left

                                                    OcrDebugBuffer.goldTop =
                                                        screenRect.top
                                                }
                                            }
                                        }
                                    }

                                    val text =
                                        visionText.text.lowercase()

                                    // Debug OCR bounding boxes
                                    OcrDebugBuffer.xpRect = null
                                    OcrDebugBuffer.ornsRect = null
                                    OcrDebugBuffer.goldRect = null

                                    for (block in visionText.textBlocks) {

                                        for (line in block.lines) {

                                            val txt =
                                                line.text
                                                    .lowercase()
                                                    .replace(
                                                        "0rns",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "0rn5",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "orn5",
                                                        "orns"
                                                    )
                                                    .replace(
                                                        "0uro",
                                                        "ouro"
                                                    )

                                            val box =
                                                line.boundingBox
                                                    ?: continue

                                            val screenRect =
                                                Rect(
                                                    left +
                                                            adjustedLeft +
                                                            box.left *
                                                            scaling,

                                                    top +
                                                            box.top *
                                                            scaling,

                                                    left +
                                                            adjustedLeft +
                                                            box.right *
                                                            scaling,

                                                    top +
                                                            box.bottom *
                                                            scaling
                                                )

                                            if (
                                                txt.contains("xp") ||
                                                txt.contains("party xp") ||
                                                txt.contains("doświadczenia") ||
                                                txt.contains("drużynowego")
                                            ) {

                                                OcrDebugBuffer.xpRect =
                                                    screenRect
                                            }

                                            if (
                                                txt.contains("orns") ||
                                                txt.contains("orn") ||
                                                txt.contains("ornów")
                                            ) {

                                                OcrDebugBuffer.ornsRect =
                                                    screenRect
                                            }

                                            if (
                                                (
                                                        txt.contains("gold") &&
                                                                !txt.contains("kingdom")
                                                        ) ||
                                                (
                                                        txt.contains("ouro") &&
                                                                !txt.contains("reino")
                                                        ) ||
                                                (
                                                        (
                                                                txt.contains("zlota") ||
                                                                        txt.contains("złota") ||
                                                                        txt.contains("złtota")
                                                                ) &&
                                                                !txt.contains("królestwa")
                                                        )
                                            ) {

                                                OcrDebugBuffer.goldRect =
                                                    screenRect
                                            }
                                        }
                                    }

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

                                } finally {

                                    // second OCR finished.
                                    // recycle all Bitmaps.
                                    recycleBitmapSafe(scaledBitmap)
                                    recycleBitmapSafe(finalBitmap)
                                    recycleBitmapSafe(croppedBitmap)
                                    recycleBitmapSafe(fullBitmap)

                                    scaledBitmap = null
                                    finalBitmap = null
                                    croppedBitmap = null
                                    fullBitmap = null

                                    ocrBusy = false
                                }
                            }

                            .addOnFailureListener { e ->

                                Log.e(
                                    "OCR_RAW",
                                    "OCR FAILED - SECOND PASS",
                                    e
                                )

                                recycleBitmapSafe(scaledBitmap)
                                recycleBitmapSafe(finalBitmap)
                                recycleBitmapSafe(croppedBitmap)
                                recycleBitmapSafe(fullBitmap)

                                scaledBitmap = null
                                finalBitmap = null
                                croppedBitmap = null
                                fullBitmap = null

                                ocrBusy = false
                            }

                    } catch (e: Exception) {

                        Log.e(
                            "OCR_RAW",
                            "Error during modern OCR first pass callback",
                            e
                        )

                        recycleBitmapSafe(scaledBitmap)
                        recycleBitmapSafe(finalBitmap)
                        recycleBitmapSafe(croppedBitmap)
                        recycleBitmapSafe(fullBitmap)

                        scaledBitmap = null
                        finalBitmap = null
                        croppedBitmap = null
                        fullBitmap = null

                        ocrBusy = false
                    }
                }

                .addOnFailureListener { e ->

                    Log.e(
                        "OCR_RAW",
                        "OCR FAILED - FIRST PASS",
                        e
                    )

                    recycleBitmapSafe(croppedBitmap)
                    recycleBitmapSafe(fullBitmap)

                    croppedBitmap = null
                    fullBitmap = null

                    ocrBusy = false
                }

        } catch (e: Exception) {

            Log.e(
                "OCR_RAW",
                "Error starting modern OCR",
                e
            )

            recycleBitmapSafe(croppedBitmap)
            recycleBitmapSafe(fullBitmap)

            croppedBitmap = null
            fullBitmap = null

            ocrBusy = false
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

    /**
     * Counts proofs / skeleton keys for the currently selected
     * tracking mode.
     *
     * Works on the WHOLE OCR text with all whitespace collapsed,
     * so names wrapped to two lines still match:
     *
     *   "Proof of Beast"
     *   "Slain (3)"
     *
     * "(3)" after the name = +3, no number = +1.
     */
    private fun parseProofs(rawText: String): Map<String, Long> {

        val result = mutableMapOf<String, Long>()

        val flat =
            rawText
                .lowercase()
                .replace(Regex("\\s+"), " ")

        // optional "(3)" - brackets tolerate typical OCR mistakes
        val countPart =
            """(?:\s*[(\[{]\s*(\d{1,4})\s*[)\]}]?)?"""

        for (proof in ProofTracking.activeProofs(this)) {

            val regex =
                Regex(proof.pattern + countPart)

            var total = 0L

            for (match in regex.findAll(flat)) {

                total +=
                    match.groupValues[1].toLongOrNull() ?: 1L
            }

            if (total > 0L) {
                result[proof.id] = total
            }
        }

        return result
    }

    /** Copies service totals into FightState (read by the UI / overlay). */
    private fun publishTotals() {

        FightState.gold = totalGold
        FightState.orns = totalOrns
        FightState.exp = totalExp
        FightState.shards = totalShards

        FightState.proofs.clear()
        FightState.proofs.putAll(totalProofs)
    }

    /** Adds one reward to the totals and remembers it for undo. */
    private fun applyReward(d: RewardDelta) {

        totalGold += d.gold
        totalOrns += d.orns
        totalExp += d.exp
        totalShards += d.shards

        for ((id, c) in d.proofs) {
            totalProofs[id] = (totalProofs[id] ?: 0L) + c
        }

        publishTotals()

        rewardHistory.addLast(d)
        if (rewardHistory.size > 20) rewardHistory.removeFirst()

        FightState.lastGold = d.gold
        FightState.lastOrns = d.orns
        FightState.lastExp = d.exp
        FightState.lastShards = d.shards
        FightState.lastProofs = d.proofs

        val msg =
            "STATE UPDATE -> +${d.exp} EXP +${d.gold} Gold +${d.orns} Orns " +
                    "+${d.shards} Shards proofs=${d.proofs}"

        val totals =
            "STATE UPDATE -> EXP=$totalExp GOLD=$totalGold ORNS=$totalOrns " +
                    "SHARDS=$totalShards PROOFS=$totalProofs"

        Log.d("TRACKER_LOG", msg)
        TrackerLogger.write("TRACKER_LOG", msg)
        Log.d("TRACKER_LOG", totals)
        TrackerLogger.write("TRACKER_LOG", totals)
    }

    /** Removes the most recently applied reward. */
    fun undoLastReward(): Boolean {

        val d = rewardHistory.removeLastOrNull() ?: return false

        totalGold = (totalGold - d.gold).coerceAtLeast(0L)
        totalOrns = (totalOrns - d.orns).coerceAtLeast(0L)
        totalExp = (totalExp - d.exp).coerceAtLeast(0L)
        totalShards = (totalShards - d.shards).coerceAtLeast(0L)

        for ((id, c) in d.proofs) {

            val left = (totalProofs[id] ?: 0L) - c

            if (left > 0L) {
                totalProofs[id] = left
            } else {
                totalProofs.remove(id)
            }
        }

        publishTotals()

        // "Last reward" view falls back to the previous one
        val prev = rewardHistory.lastOrNull()

        FightState.lastGold = prev?.gold ?: 0L
        FightState.lastOrns = prev?.orns ?: 0L
        FightState.lastExp = prev?.exp ?: 0L
        FightState.lastShards = prev?.shards ?: 0L
        FightState.lastProofs = prev?.proofs ?: emptyMap()

        Log.d("TRACKER_LOG", "UNDO -> removed $d")
        TrackerLogger.write("TRACKER_LOG", "UNDO -> removed $d")

        return true
    }

    private fun parseRewards(text: String) {

        val lines = text.lines()

        var sessionGold = 0L
        var sessionOrns = 0L
        var sessionExp = 0L
        var sessionShards = 0L

        for (line in lines) {

            // 1. Standardize formatting and fix messy OCR typos upfront
            val clean = line.replace(",", "")
                .lowercase()
                .replace("0rns", "orns")
                .replace("0rn5", "orns")
                .replace("orn5", "orns")
                .replace("0uro", "ouro")


            if (
                clean.contains("tower shard") || clean.contains("tower shards") ||                                                //english
                clean.contains("fragmentos da torre") ||                                                                                //portuguese
                clean.contains("fragmenty wież") || clean.contains("fragmenty wieź") || clean.contains("fragmenty wiez")    //polish
            ) {
                sessionShards += extractNumber(clean)
            }

            if ((clean.contains("gold") && !clean.contains("kingdom")) ||       //english
                (clean.contains("ouro") && !clean.contains("reino")) ||         //portuguese
                ((clean.contains("zlota") || clean.contains("złota") || (clean.contains("złtota")))  && !clean.contains("królestwa"))) {    //polish
                sessionGold += extractNumber(clean)
            }

            // 2. This works reliably now because "0rns" has been normalized to "orns"
            if (clean.contains("orns") || clean.contains("orn") ||
                clean.contains("ornów") || clean.contains("orndw") || clean.contains("ornów") || clean.contains("orn")) {
                sessionOrns += extractBefore(clean, "orns")
            }

            if (clean.contains("xp") || clean.contains("party xp") ||                                                                    //english
                clean.contains("doświadczenia") || clean.contains("doświadczenia drużynowego") ||  clean.contains("drużynowego")    //polish
            ) {
                sessionExp += extractNumber(clean)
            }
        }

        // proofs / keys are parsed from the whole text (names can wrap)
        val sessionProofs = parseProofs(text)

        // duplicate check
        if (
            sessionGold == lastGold &&
            sessionOrns == lastOrns &&
            sessionExp == lastExp &&
            sessionShards == lastShards &&
            sessionProofs == lastProofs
        ) {
            return
        }

        // store last seen values
        lastGold = sessionGold
        lastOrns = sessionOrns
        lastExp = sessionExp
        lastShards = sessionShards
        lastProofs = sessionProofs

        val delta = RewardDelta(
            sessionGold,
            sessionOrns,
            sessionExp,
            sessionShards,
            sessionProofs
        )

        // unusually high orns -> ask the user first
        if (sessionOrns > FightState.ornsConfirmationThreshold) {

            RewardConfirmOverlayService.pendingOrns = sessionOrns

            RewardConfirmOverlayService.onResult = { accepted ->

                if (accepted) {
                    applyReward(delta)
                }
                // if denied, do nothing
            }

            startService(
                Intent(this, RewardConfirmOverlayService::class.java)
            )

            return
        }

        applyReward(delta)
    }

    private fun extractNumber(text: String): Long {

        // keep digits only
        val digits = text.filter { it.isDigit() }

        return digits.toLongOrNull() ?: 0L
    }

    override fun onDestroy() {

        instance = null

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