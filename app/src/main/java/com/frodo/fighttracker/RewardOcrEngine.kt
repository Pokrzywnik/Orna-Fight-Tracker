package com.frodo.fighttracker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * PaddleOCR engine used by the experimental reward scanner.
 *
 * Owns its OWN detector + recognizer, so item-assess cancellation can
 * never kill a reward scan (and the other way round).
 *
 * SPEED: recognition is by far the most expensive part (the drop list
 * below the rewards is ~25 wide lines). Boxes are therefore recognised
 * top -> bottom in small chunks and the caller can stop as soon as it
 * has what it needs (stopWhen), so the drop list is never read.
 *
 * Coordinates are relative to the scanned bitmap.
 */
object RewardOcrEngine {

    private const val TAG = "RewardOcrEngine"

    /**
     * Detector input = DET_SCALE x longest side of the scanned bitmap.
     * Recognition always uses the ORIGINAL resolution.
     */
    const val DET_SCALE = 0.40f

    private const val DET_MIN_SIDE = 320
    private const val DET_MAX_SIDE = 1024

    /** Boxes smaller than this (original px) are noise - never recognised. */
    private const val MIN_BOX_W = 12f
    private const val MIN_BOX_H = 12f

    /** boxes recognised per step before stopWhen is asked */
    private const val CHUNK_SIZE = 4

    private const val ROW_OVERLAP_RATIO = 0.5f

    data class OcrBox(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerY: Int get() = (top + bottom) / 2
    }

    data class OcrRow(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    data class ScanResult(
        val boxes: List<OcrBox>,
        val rows: List<OcrRow>,
        val boxCount: Int,
        val detectMs: Double,
        val recognizeMs: Double,
        val recognizedCount: Int = 0,
        val stoppedEarly: Boolean = false
    )

    private val lock = Any()

    @Volatile
    private var detector: PaddleOcrDetector? = null

    @Volatile
    private var recognizer: PaddleOcrRecognizer? = null

    fun warmUp(context: Context) {
        ensureLoaded(context)
    }

    private fun ensureLoaded(
        context: Context
    ): Pair<PaddleOcrDetector, PaddleOcrRecognizer> {

        synchronized(lock) {

            val appContext = context.applicationContext

            val det =
                detector
                    ?: PaddleOcrDetector(appContext).also {
                        detector = it
                    }

            val rec =
                recognizer
                    ?: PaddleOcrRecognizer(appContext).also {
                        recognizer = it
                    }

            return Pair(det, rec)
        }
    }

    /** Requests termination of any running ONNX inference. */
    fun cancel() {

        detector?.cancelCurrentRun()
        recognizer?.cancelCurrentRuns()
    }

    /**
     * Detector + progressive recognizer. Blocking - call from a background thread.
     *
     * stopWhen is called with everything recognised so far (after every
     * chunk of CHUNK_SIZE boxes, top -> bottom). Return true to stop.
     */
    fun scan(
        context: Context,
        bitmap: Bitmap,
        stopWhen: ((ScanResult) -> Boolean)? = null
    ): ScanResult {

        val (det, rec) = ensureLoaded(context)

        val maxSide =
            (max(bitmap.width, bitmap.height) * DET_SCALE)
                .toInt()
                .coerceIn(DET_MIN_SIDE, DET_MAX_SIDE)

        val detStart = System.nanoTime()

        val boxes =
            det.detect(bitmap, maxSide)
                .filter { b ->

                    if (b.points.isEmpty()) {
                        false
                    } else {

                        val w =
                            b.points.maxOf { it.x } - b.points.minOf { it.x }

                        val h =
                            b.points.maxOf { it.y } - b.points.minOf { it.y }

                        w >= MIN_BOX_W && h >= MIN_BOX_H
                    }
                }

        val detectMs =
            (System.nanoTime() - detStart) / 1_000_000.0

        if (
            boxes.isEmpty() ||
            Thread.currentThread().isInterrupted
        ) {

            return ScanResult(
                boxes = emptyList(),
                rows = emptyList(),
                boxCount = boxes.size,
                detectMs = detectMs,
                recognizeMs = 0.0
            )
        }

        // top -> bottom: header, proofs, xp, gold, orns come BEFORE the drop list
        val ordered =
            boxes.sortedBy { b -> b.points.minOf { it.y } }

        val items = mutableListOf<OcrBox>()

        var recognizeMs = 0.0
        var recognized = 0
        var stoppedEarly = false

        for (chunk in ordered.chunked(CHUNK_SIZE)) {

            if (Thread.currentThread().isInterrupted) {
                break
            }

            val recStart = System.nanoTime()

            val results =
                rec.recognizeBoxes(
                    bitmap,
                    chunk
                )

            recognizeMs +=
                (System.nanoTime() - recStart) / 1_000_000.0

            recognized += chunk.size

            for (result in results) {
                toOcrBox(result)?.let { items.add(it) }
            }

            if (stopWhen != null && items.isNotEmpty()) {

                val partial =
                    ScanResult(
                        boxes = items.toList(),
                        rows = groupIntoRows(items),
                        boxCount = boxes.size,
                        detectMs = detectMs,
                        recognizeMs = recognizeMs,
                        recognizedCount = recognized,
                        stoppedEarly = false
                    )

                if (stopWhen(partial)) {
                    stoppedEarly = true
                    break
                }
            }
        }

        val rows =
            groupIntoRows(items)

        Log.d(
            TAG,
            "SCAN ${bitmap.width}x${bitmap.height} detSide=$maxSide " +
                    "boxes=${boxes.size} recognised=$recognized " +
                    "early=$stoppedEarly rows=${rows.size} " +
                    "det=%.0fms rec=%.0fms".format(
                        detectMs,
                        recognizeMs
                    )
        )

        return ScanResult(
            boxes = items,
            rows = rows,
            boxCount = boxes.size,
            detectMs = detectMs,
            recognizeMs = recognizeMs,
            recognizedCount = recognized,
            stoppedEarly = stoppedEarly
        )
    }

    private fun toOcrBox(
        result: PaddleOcrRecognizer.RecognitionResult
    ): OcrBox? {

        val text =
            result.text.trim()

        if (text.isEmpty()) {
            return null
        }

        val box =
            result.box
                ?: return null

        if (box.points.isEmpty()) {
            return null
        }

        return OcrBox(
            text = text,
            left = box.points.minOf { it.x }.toInt(),
            top = box.points.minOf { it.y }.toInt(),
            right = box.points.maxOf { it.x }.toInt(),
            bottom = box.points.maxOf { it.y }.toInt()
        )
    }

    private class RowBuilder(
        var top: Int,
        var bottom: Int
    ) {
        val items = mutableListOf<OcrBox>()
    }

    /**
     * Merges boxes on the same visual row (left -> right),
     * rows ordered top -> bottom. Used for debug only.
     */
    private fun groupIntoRows(
        boxes: List<OcrBox>
    ): List<OcrRow> {

        val sortedBoxes =
            boxes.sortedBy { it.centerY }

        val builders =
            mutableListOf<RowBuilder>()

        for (item in sortedBoxes) {

            val current =
                builders.lastOrNull()

            if (current != null) {

                val overlap =
                    min(item.bottom, current.bottom) -
                            max(item.top, current.top)

                val minHeight =
                    min(
                        item.height,
                        current.bottom - current.top
                    ).coerceAtLeast(1)

                if (overlap >= minHeight * ROW_OVERLAP_RATIO) {

                    current.items.add(item)
                    current.top = min(current.top, item.top)
                    current.bottom = max(current.bottom, item.bottom)

                    continue
                }
            }

            builders.add(
                RowBuilder(item.top, item.bottom).also {
                    it.items.add(item)
                }
            )
        }

        return builders.map { row ->

            val sorted =
                row.items.sortedBy { it.left }

            OcrRow(
                text = sorted.joinToString(" ") { it.text },
                left = sorted.minOf { it.left },
                top = row.top,
                right = sorted.maxOf { it.right },
                bottom = row.bottom
            )
        }
    }
}