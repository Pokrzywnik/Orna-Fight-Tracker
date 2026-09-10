package com.frodo.fighttracker

import android.app.Activity
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
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

private const val ASSESSMENT_CROP_BOTTOM_RATIO = 0.50f

class ItemAssessmentCaptureService : Service() {

    /*
     * Pipeline initialization ma osobny executor.
     *
     * Nie używamy assessmentExecutor do preloadowania pipeline,
     * ponieważ assessmentExecutor jest tworzony osobno dla każdego
     * aktualnego assessmentu.
     */
    private val pipelineInitExecutor =
        Executors.newSingleThreadExecutor()

    /*
     * Aktualny worker assessmentu.
     *
     * Przy zmianie fingerprintu:
     *
     * 1. cancel(true)
     * 2. shutdownNow()
     * 3. tworzony jest NOWY executor
     * 4. assessment startuje natychmiast
     *
     * Dzięki temu nie tworzymy kolejki:
     *
     * item A -> CANCEL
     * item B -> CANCEL
     * item C -> CANCEL
     * item D -> ASSESS
     */
    @Volatile
    private var currentAssessmentExecutor: ExecutorService? = null

    @Volatile
    private var currentAssessmentFuture: Future<*>? = null

    /*
     * Każda zmiana fingerprintu dostaje nową generację.
     *
     * Dzięki temu nawet gdy stary worker nie zdąży fizycznie
     * zatrzymać się w kodzie natywnym, jego wynik zostanie
     * odrzucony i nie pokaże starego itemu.
     */
    private val assessmentGeneration =
        AtomicLong(0L)

    private lateinit var pipeline

            : ItemAssessmentPipeline

    private var lastFrameFingerprint: IntArray? = null

    private val fingerprintLock = Any()

    companion object {

        private const val TAG =
            "ItemAssessmentCapture"

        const val EXTRA_RESULT_CODE =
            "resultCode"

        const val EXTRA_DATA =
            "data"

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

    /*
     * Frame acquisition działa na osobnym wątku,
     * żeby callback ImageReader nie obciążał main thread.
     */
    private lateinit var captureThread: HandlerThread

    private lateinit var captureHandler: Handler

    private var isCapturing =
        false

    private var screenWidth =
        0

    private var screenHeight =
        0

    private var screenDensity =
        0

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
            HandlerThread(
                "ItemAssessmentCapture"
            )

        captureThread.start()

        captureHandler =
            Handler(
                captureThread.looper
            )

        createNotificationChannel()

        startForegroundServiceNotification()

        /*
         * Pipeline powinien być już preloadowany przez
         * FloatingTrackerService.
         *
         * Tutaj tylko pobieramy gotową instancję.
         */
        pipelineInitExecutor.execute {

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

        Log.d(
            TAG,
            "onStartCommand"
        )

        if (intent == null) {

            Log.e(
                TAG,
                "Intent is null"
            )

            stopSelf()

            return START_NOT_STICKY
        }

        val resultCode =
            intent.getIntExtra(
                EXTRA_RESULT_CODE,
                -1
            )

        val projectionData =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                intent.getParcelableExtra(
                    EXTRA_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent.getParcelableExtra(
                    EXTRA_DATA
                )
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

            startForegroundServiceNotification()

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
            .getRealMetrics(
                metrics
            )

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

        isCapturing =
            true

        FloatingTrackerService.setScanning(
            true
        )

        Log.d(
            TAG,
            "Item assessment capture started"
        )
    }

    private fun imageToBitmap(
        image: Image
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
                    rowPadding /
                    pixelStride

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

        if (
            bitmapWidth !=
            image.width
        ) {

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

    /**
     * Tani fingerprint ekranu.
     *
     * Celowo patrzymy tylko na lewą i prawą część ekranu,
     * ponieważ środek zawiera animowaną aurę itemu.
     *
     * 4 próbki poziomo x 32 pionowo
     * dla każdej strony.
     *
     * RGB jest kwantyzowane do 5 bitów/channel.
     */
    private fun frameFingerprint(
        image: Image
    ): IntArray {

        val plane =
            image.planes.firstOrNull()
                ?: return intArrayOf(
                    0,
                    0
                )

        val buffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        val width =
            image.width

        val height =
            image.height

        val sideWidth =
            width / 4

        val horizontalSamples =
            4

        val verticalSamples =
            32

        var leftHash =
            0

        var rightHash =
            0

        /*
         * LEFT + RIGHT
         */
        for (side in 0..1) {

            var hash =
                0

            for (yIndex in 0 until verticalSamples) {

                val y =
                    (
                            yIndex *
                                    (height - 1)
                            ) /
                            (verticalSamples - 1)

                for (
                xIndex in 0 until horizontalSamples
                ) {

                    val localX =
                        (
                                xIndex *
                                        (sideWidth - 1)
                                ) /
                                (horizontalSamples - 1)

                    val x =
                        if (side == 0) {
                            localX
                        } else {
                            width -
                                    sideWidth +
                                    localX
                        }

                    val offset =
                        y * rowStride +
                                x * pixelStride

                    if (
                        offset < 0 ||
                        offset + 2 >= buffer.limit()
                    ) {
                        continue
                    }

                    val r =
                        buffer.get(
                            offset
                        ).toInt() and 0xFF

                    val g =
                        buffer.get(
                            offset + 1
                        ).toInt() and 0xFF

                    val b =
                        buffer.get(
                            offset + 2
                        ).toInt() and 0xFF

                    /*
                     * 5 bit/channel.
                     */
                    val qr =
                        r shr 3

                    val qg =
                        g shr 3

                    val qb =
                        b shr 3

                    hash =
                        hash * 31 +
                                (qr shl 10) +
                                (qg shl 5) +
                                qb
                }
            }

            if (side == 0) {
                leftHash = hash
            } else {
                rightHash = hash
            }
        }

        return intArrayOf(
            leftHash,
            rightHash
        )
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

                var bitmap:
                        Bitmap? = null

                var cropped:
                        Bitmap? = null

                try {

                    /*
                     * NAJPIERW fingerprint bez tworzenia Bitmapy.
                     *
                     * Jeżeli ekran się nie zmienił,
                     * kończymy tutaj.
                     */
                    val fingerprint =
                        frameFingerprint(
                            image
                        )

                    val generation =
                        synchronized(
                            fingerprintLock
                        ) {

                            if (
                                fingerprint.contentEquals(
                                    lastFrameFingerprint
                                )
                            ) {

                                Log.d(
                                    TAG,
                                    "Fingerprint unchanged - skipping"
                                )

                                return@setOnImageAvailableListener
                            }

                            /*
                             * Fingerprint się zmienił.
                             *
                             * Nowa generacja = wszystkie wyniki
                             * starszych assessmentów są nieważne.
                             */
                            lastFrameFingerprint =
                                fingerprint

                            assessmentGeneration.incrementAndGet()
                        }

                    Log.d(
                        TAG,
                        "Fingerprint CHANGED -> cancelling previous assessment"
                    )

                    /*
                     * NATYCHMIAST anulujemy poprzedni worker.
                     */
                    cancelCurrentAssessment()

                    /*
                     * Dopiero teraz tworzymy Bitmapę.
                     */
                    bitmap =
                        imageToBitmap(
                            image
                        )
                            ?: return@setOnImageAvailableListener

                    val cropHeight =
                        (
                                bitmap.height *
                                        ASSESSMENT_CROP_BOTTOM_RATIO
                                ).toInt()

                    cropped =
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            cropHeight
                        )

                    Log.d(
                        TAG,
                        "Assessment crop: ${cropped.width}x${cropped.height}"
                    )

                    /*
                     * Assessment STARTUJE NATYCHMIAST.
                     *
                     * ŻADNEGO:
                     * postDelayed(...)
                     * debounce
                     * stability timer
                     * czekania 2300 ms.
                     */
                    startAssessment(
                        cropped,
                        generation
                    )

                    /*
                     * Ownership Bitmapy przejmuje worker.
                     */
                    cropped = null

                } catch (
                    e: Exception
                ) {

                    Log.e(
                        TAG,
                        "Failed to capture screenshot",
                        e
                    )

                    cropped?.let {

                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }

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

    /**
     * Uruchamia dokładnie JEDEN aktualny assessment.
     *
     * Każdy poprzedni worker jest wcześniej anulowany.
     */
    private fun startAssessment(
        cropped: Bitmap,
        generation: Long
    ) {

        val executor =
            Executors.newSingleThreadExecutor()

        currentAssessmentExecutor =
            executor

        try {

            val future =
                executor.submit {

                    try {

                        /*
                         * Jeżeli w międzyczasie pojawił się
                         * nowszy fingerprint, ten worker jest
                         * już nieaktualny.
                         */
                        if (
                            assessmentGeneration.get() !=
                            generation
                        ) {

                            Log.d(
                                TAG,
                                "Assessment $generation already stale"
                            )

                            return@submit
                        }

                        if (
                            Thread.currentThread()
                                .isInterrupted
                        ) {

                            Log.d(
                                TAG,
                                "Assessment $generation interrupted before start"
                            )

                            return@submit
                        }

                        if (!::pipeline.isInitialized) {

                            Log.d(
                                TAG,
                                "Pipeline not initialized yet - loading"
                            )

                            pipeline =
                                ItemAssessmentPipeline
                                    .ItemAssessmentManager
                                    .getPipeline(
                                        applicationContext
                                    )
                        }

                        if (
                            assessmentGeneration.get() !=
                            generation ||
                            Thread.currentThread()
                                .isInterrupted
                        ) {

                            Log.d(
                                TAG,
                                "Assessment $generation cancelled before pipeline"
                            )

                            return@submit
                        }

                        Log.d(
                            TAG,
                            "START ASSESSMENT generation=$generation"
                        )

                        val result =
                            pipeline.processBitmap(
                                cropped
                            )

                        /*
                         * NAJWAŻNIEJSZE:
                         *
                         * Nawet jeżeli cancel(true) nie przerwie
                         * natychmiast kodu natywnego ONNX,
                         * stary wynik nie może trafić na overlay.
                         */
                        if (
                            assessmentGeneration.get() !=
                            generation
                        ) {

                            Log.d(
                                TAG,
                                "Ignoring stale assessment result generation=$generation"
                            )

                            return@submit
                        }

                        if (
                            Thread.currentThread()
                                .isInterrupted
                        ) {

                            Log.d(
                                TAG,
                                "Ignoring interrupted assessment generation=$generation"
                            )

                            return@submit
                        }

                        Log.d(
                            TAG,
                            "ASSESSMENT FINISHED generation=$generation"
                        )

                        handleAssessmentResult(
                            result,
                            generation
                        )

                    } catch (
                        e: Exception
                    ) {

                        /*
                         * Cancellation przez Future.cancel(true)
                         * może objawić się wyjątkiem.
                         * Nie traktujemy tego jako błędu aplikacji,
                         * jeżeli worker jest już nieaktualny.
                         */
                        if (
                            assessmentGeneration.get() ==
                            generation
                        ) {

                            Log.e(
                                TAG,
                                "Background assessment failed",
                                e
                            )
                        } else {

                            Log.d(
                                TAG,
                                "Old assessment terminated: generation=$generation"
                            )
                        }

                    } finally {

                        if (
                            !cropped.isRecycled
                        ) {

                            cropped.recycle()
                        }

                        /*
                         * Ten worker nie uruchamia żadnego
                         * następnego assessmentu.
                         *
                         * Następny assessment może uruchomić
                         * WYŁĄCZNIE kolejna zmiana fingerprintu.
                         */
                    }
                }

            currentAssessmentFuture =
                future

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Could not start assessment worker",
                e
            )

            if (
                !cropped.isRecycled
            ) {

                cropped.recycle()
            }

            executor.shutdownNow()

            if (
                currentAssessmentExecutor ===
                executor
            ) {

                currentAssessmentExecutor =
                    null
            }
        }
    }

    /**
     * Anuluje absolutnie aktualny assessment.
     *
     * Nie zostawia go w kolejce.
     */
    private fun cancelCurrentAssessment() {

        /*
         * NAJPIERW zatrzymujemy natywne ONNX Runtime.
         *
         * Future.cancel(true) przerywa wątek Javy, ale samo w sobie
         * nie gwarantuje natychmiastowego przerwania ortSession.run().
         *
         * Pipeline ma własne RunOptions dla aktywnych inference.
         * setTerminate(true) wysyła bezpośrednią prośbę do ONNX Runtime
         * o zakończenie aktualnego inference.
         */
        if (::pipeline.isInitialized) {

            Log.d(
                TAG,
                "Requesting ONNX inference termination"
            )

            try {

                pipeline.cancelCurrentInference()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to cancel current ONNX inference",
                    e
                )
            }

        } else {

            Log.d(
                TAG,
                "Pipeline not initialized - no ONNX inference to cancel"
            )
        }

        /*
         * Następnie anulujemy worker Javy.
         *
         * To nadal jest potrzebne, ponieważ po wyjściu z ONNX
         * worker może wykonywać jeszcze kod Kotlin/Java.
         */
        val future =
            currentAssessmentFuture

        val executor =
            currentAssessmentExecutor

        currentAssessmentFuture =
            null

        currentAssessmentExecutor =
            null

        if (future != null) {

            Log.d(
                TAG,
                "Cancelling previous assessment"
            )

            future.cancel(
                true
            )
        }

        if (executor != null) {

            Log.d(
                TAG,
                "Shutting down previous assessment worker"
            )

            executor.shutdownNow()
        }
    }

    private fun handleAssessmentResult(
        result:
        ItemAssessmentPipeline.PipelineResult,
        generation: Long
    ) {

        /*
         * Jeszcze jedna kontrola przed pokazaniem overlay.
         */
        if (
            assessmentGeneration.get() !=
            generation
        ) {

            Log.d(
                TAG,
                "Ignoring stale result before overlay"
            )

            return
        }

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
                    assessment.exactQuality *
                            100.0
                )
            )

        } else {

            Log.d(
                TAG,
                "QUALITY RANGE: %.2f%% - %.2f%%".format(
                    assessment.qualityRangeMin *
                            100.0,
                    assessment.qualityRangeMax *
                            100.0
                )
            )
        }

        Log.d(
            TAG,
            "========================================"
        )

        /*
         * Ostatni check tuż przed overlay.
         *
         * Jeżeli użytkownik zmienił item podczas
         * przetwarzania, wynik jest ignorowany.
         */
        if (
            assessmentGeneration.get() !=
            generation
        ) {

            Log.d(
                TAG,
                "Fingerprint changed before overlay - ignoring result"
            )

            return
        }

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

        if (
            virtualDisplay == null
        ) {

            Log.e(
                TAG,
                "Failed to create VirtualDisplay"
            )

            stopSelf()
        }
    }

    private fun stopCapture() {

        /*
         * Najpierw anulujemy aktualny assessment.
         */
        assessmentGeneration.incrementAndGet()

        cancelCurrentAssessment()

        if (!isCapturing) {
            return
        }

        Log.d(
            TAG,
            "Stopping item assessment capture"
        )

        isCapturing =
            false

        FloatingTrackerService.setScanning(
            false
        )

        virtualDisplay?.release()

        virtualDisplay =
            null

        imageReader?.close()

        imageReader =
            null

        mediaProjection?.unregisterCallback(
            projectionCallback
        )

        mediaProjection?.stop()

        mediaProjection =
            null

        synchronized(
            fingerprintLock
        ) {

            lastFrameFingerprint =
                null
        }
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
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    this,
                    CHANNEL_ID
                )

            } else {

                Notification.Builder(
                    this
                )
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
                .setOngoing(
                    true
                )
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

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

        pipelineInitExecutor.shutdownNow()

        captureHandler.removeCallbacksAndMessages(
            null
        )

        captureThread.quitSafely()

        FloatingTrackerService.setScanning(
            false
        )

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? =
        null

    private val windowManager:
            android.view.WindowManager
        get() =
            getSystemService(
                WINDOW_SERVICE
            ) as android.view.WindowManager
}
