package com.frodo.fighttracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class PaddleOcrDetector(
    private val context: Context
) {

    companion object {
        private const val TAG = "PaddleOCR-DET"

        private const val MODEL_FILE =
            "ocr/det/inference.onnx"

        /*
         * PP-OCR DB parameters
         */
        private const val DB_THRESH = 0.20f
        private const val DB_BOX_THRESH = 0.45f
        private const val DB_UNCLIP_RATIO = 1.4f
        private const val DB_MAX_CANDIDATES = 3000

        /*
         * Input resize.
         */
        private const val MAX_SIDE_LEN = 512
        private const val STRIDE = 32
    }

    data class TextBox(
        val points: List<Point>,
        val score: Float
    )

    data class Point(
        val x: Float,
        val y: Float
    )

    private data class ResizeInfo(
        val resizedWidth: Int,
        val resizedHeight: Int,
        val ratioWidth: Float,
        val ratioHeight: Float
    )

    /*
     * IMPORTANT:
     *
     * We keep the REAL output dimensions returned
     * by ONNX instead of assuming H/4 x W/4.
     */
    private data class DetectorOutput(
        val data: FloatArray,
        val width: Int,
        val height: Int
    )

    private data class Component(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val score: Float
    )

    private val ortEnvironment: OrtEnvironment =
        OrtEnvironment.getEnvironment()

    private val ortSession: OrtSession

    init {

        Log.d(TAG, "========================================")
        Log.d(TAG, "Initializing PP-OCRv6 DETECTOR")
        Log.d(TAG, "========================================")

        val modelBytes =
            context.assets.open(MODEL_FILE).use {
                it.readBytes()
            }

        val sessionOptions =
            OrtSession.SessionOptions()

        ortSession =
            ortEnvironment.createSession(
                modelBytes,
                sessionOptions
            )

        Log.d(
            TAG,
            "PP-OCRv6 DETECTOR loaded"
        )

        Log.d(
            TAG,
            "Inputs: ${ortSession.inputInfo.keys}"
        )

        Log.d(
            TAG,
            "Outputs: ${ortSession.outputInfo.keys}"
        )
    }

    /**
     * Detect text regions.
     *
     * Returned points are in ORIGINAL bitmap coordinates.
     */
    fun detect(bitmap: Bitmap): List<TextBox> {

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return emptyList()
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "DETECTION START")
        Log.d(
            TAG,
            "Original image: ${bitmap.width}x${bitmap.height}"
        )

        /*
         * Resize image.
         */
        val resizeInfo =
            calculateResize(
                bitmap.width,
                bitmap.height
            )

        val resizedBitmap =
            Bitmap.createScaledBitmap(
                bitmap,
                resizeInfo.resizedWidth,
                resizeInfo.resizedHeight,
                true
            )

        Log.d(
            TAG,
            "Resized image: " +
                    "${resizedBitmap.width}x${resizedBitmap.height}"
        )

        /*
         * Convert bitmap to CHW float tensor.
         */
        val inputData =
            bitmapToFloatArray(
                resizedBitmap
            )

        val shape =
            longArrayOf(
                1,
                3,
                resizedBitmap.height.toLong(),
                resizedBitmap.width.toLong()
            )

        val inputName =
            ortSession.inputInfo.keys.first()

        val tensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(inputData),
                shape
            )

        val detectorOutput: DetectorOutput

        try {

            tensor.use {

                val inputs =
                    mapOf(
                        inputName to tensor
                    )

                ortSession.run(inputs).use { result ->

                    if (result.size() == 0) {

                        Log.e(
                            TAG,
                            "Detector returned no outputs"
                        )

                        return emptyList()
                    }

                    val output =
                        result[0].value

                    detectorOutput =
                        extractOutput(output)
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Detector inference failed",
                e
            )

            return emptyList()

        } finally {

            if (!resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
        }

        Log.d(
            TAG,
            "Detector output map: " +
                    "${detectorOutput.width}x${detectorOutput.height}"
        )

        Log.d(
            TAG,
            "Detector output elements: " +
                    "${detectorOutput.data.size}"
        )

        /*
         * Safety check.
         *
         * This prevents another ArrayIndexOutOfBoundsException
         * if the ONNX output shape ever changes.
         */
        val expectedSize =
            detectorOutput.width *
                    detectorOutput.height

        if (detectorOutput.data.size < expectedSize) {

            Log.e(
                TAG,
                "Invalid detector output size: " +
                        "data=${detectorOutput.data.size}, " +
                        "expected=$expectedSize"
            )

            return emptyList()
        }

        val boxes =
            dbPostProcess(
                outputArray = detectorOutput.data,
                outputWidth = detectorOutput.width,
                outputHeight = detectorOutput.height,
                resizeInfo = resizeInfo,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height
            )

        Log.d(
            TAG,
            "Detection finished. " +
                    "Found ${boxes.size} text boxes."
        )

        for ((index, box) in boxes.withIndex()) {

            Log.d(
                TAG,
                "BOX $index: " +
                        "score=${"%.3f".format(box.score)} " +
                        "points=${formatPoints(box.points)}"
            )
        }

        Log.d(TAG, "========================================")

        return boxes
    }

    /**
     * Extract ONNX output.
     *
     * Expected structure:
     *
     * [1][1][H][W]
     *
     * IMPORTANT:
     * We return the REAL H/W of the ONNX tensor.
     *
     * Your current model reports:
     *
     * [1,1,960,448]
     *
     * Therefore:
     *
     * height = 960
     * width  = 448
     *
     * We must NOT assume 1/4 resolution.
     */
    private fun extractOutput(
        output: Any?
    ): DetectorOutput {

        if (output == null) {
            throw IllegalStateException(
                "Detector output is null"
            )
        }

        if (output !is Array<*>) {

            throw IllegalStateException(
                "Unexpected detector output type: " +
                        output.javaClass
            )
        }

        if (output.isEmpty()) {

            throw IllegalStateException(
                "Detector output is empty"
            )
        }

        /*
         * [1]
         */
        val batch =
            output[0]

        if (batch !is Array<*>) {

            throw IllegalStateException(
                "Unexpected detector batch type: " +
                        "${batch?.javaClass}"
            )
        }

        if (batch.isEmpty()) {

            throw IllegalStateException(
                "Detector batch is empty"
            )
        }

        /*
         * [1][1]
         */
        val channel =
            batch[0]

        if (channel !is Array<*>) {

            throw IllegalStateException(
                "Unexpected detector channel type: " +
                        "${channel?.javaClass}"
            )
        }

        if (channel.isEmpty()) {

            throw IllegalStateException(
                "Detector channel is empty"
            )
        }

        /*
         * [1][1][H]
         */
        val firstRow =
            channel[0]

        if (firstRow !is FloatArray) {

            throw IllegalStateException(
                "Unexpected detector row type: " +
                        "${firstRow?.javaClass}"
            )
        }

        val height =
            channel.size

        val width =
            firstRow.size

        Log.d(
            TAG,
            "Raw detector output shape: " +
                    "[1,1,$height,$width]"
        )

        /*
         * Allocate exactly H * W.
         */
        val result =
            FloatArray(
                height * width
            )

        for (y in 0 until height) {

            val row =
                channel[y] as? FloatArray
                    ?: throw IllegalStateException(
                        "Invalid detector row at y=$y"
                    )

            if (row.size != width) {

                throw IllegalStateException(
                    "Inconsistent detector row width: " +
                            "row=$y size=${row.size}, " +
                            "expected=$width"
                )
            }

            System.arraycopy(
                row,
                0,
                result,
                y * width,
                width
            )
        }

        /*
         * Verify dimensions.
         */
        if (result.size != height * width) {

            throw IllegalStateException(
                "Output flattening error: " +
                        "data=${result.size}, " +
                        "expected=${height * width}"
            )
        }

        return DetectorOutput(
            data = result,
            width = width,
            height = height
        )
    }

    /**
     * Paddle-style resize.
     *
     * Aspect ratio is preserved as closely as possible,
     * dimensions are aligned to STRIDE.
     */
    private fun calculateResize(
        originalWidth: Int,
        originalHeight: Int
    ): ResizeInfo {

        val maxSide =
            max(
                originalWidth,
                originalHeight
            )

        val scale =
            if (maxSide > MAX_SIDE_LEN) {

                MAX_SIDE_LEN.toFloat() /
                        maxSide.toFloat()

            } else {

                1.0f
            }

        var resizedWidth =
            max(
                STRIDE,
                (originalWidth * scale).toInt()
            )

        var resizedHeight =
            max(
                STRIDE,
                (originalHeight * scale).toInt()
            )

        resizedWidth =
            ((resizedWidth + STRIDE - 1) / STRIDE) *
                    STRIDE

        resizedHeight =
            ((resizedHeight + STRIDE - 1) / STRIDE) *
                    STRIDE

        /*
         * Limit only if rounding pushed us beyond
         * the configured maximum.
         */
        if (resizedWidth > MAX_SIDE_LEN) {

            resizedWidth =
                (MAX_SIDE_LEN / STRIDE) * STRIDE
        }

        if (resizedHeight > MAX_SIDE_LEN) {

            resizedHeight =
                (MAX_SIDE_LEN / STRIDE) * STRIDE
        }

        val ratioWidth =
            resizedWidth.toFloat() /
                    originalWidth.toFloat()

        val ratioHeight =
            resizedHeight.toFloat() /
                    originalHeight.toFloat()

        return ResizeInfo(
            resizedWidth = resizedWidth,
            resizedHeight = resizedHeight,
            ratioWidth = ratioWidth,
            ratioHeight = ratioHeight
        )
    }

    /**
     * PaddleOCR detector preprocessing.
     *
     * RGB -> CHW
     * normalize by /255
     * mean/std normalization
     */
    private fun bitmapToFloatArray(
        bitmap: Bitmap
    ): FloatArray {

        val width =
            bitmap.width

        val height =
            bitmap.height

        val pixelCount =
            width * height

        val pixels =
            IntArray(pixelCount)

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val result =
            FloatArray(
                3 * pixelCount
            )

        /*
         * PaddleOCR normalization.
         */
        val meanR = 0.485f
        val meanG = 0.456f
        val meanB = 0.406f

        val stdR = 0.229f
        val stdG = 0.224f
        val stdB = 0.225f

        val channelSize =
            pixelCount

        for (y in 0 until height) {

            for (x in 0 until width) {

                val pixel =
                    pixels[
                        y * width + x
                    ]

                val r =
                    ((pixel shr 16) and 0xFF) /
                            255.0f

                val g =
                    ((pixel shr 8) and 0xFF) /
                            255.0f

                val b =
                    (pixel and 0xFF) /
                            255.0f

                val index =
                    y * width + x

                /*
                 * RGB -> CHW
                 */
                result[index] =
                    (r - meanR) / stdR

                result[channelSize + index] =
                    (g - meanG) / stdG

                result[
                    2 * channelSize + index
                ] =
                    (b - meanB) / stdB
            }
        }

        return result
    }

    /**
     * DB post-processing.
     *
     * Converts probability map into text boxes.
     *
     * This implementation does not require OpenCV.
     */
    private fun dbPostProcess(
        outputArray: FloatArray,
        outputWidth: Int,
        outputHeight: Int,
        resizeInfo: ResizeInfo,
        originalWidth: Int,
        originalHeight: Int
    ): List<TextBox> {

        /*
         * HARD SAFETY CHECK.
         */
        val expectedSize =
            outputWidth * outputHeight

        if (outputArray.size < expectedSize) {

            Log.e(
                TAG,
                "dbPostProcess: invalid array size " +
                        "actual=${outputArray.size}, " +
                        "expected=$expectedSize"
            )

            return emptyList()
        }

        /*
         * Binary probability map.
         */
        val bitmap =
            BooleanArray(
                expectedSize
            )

        for (i in 0 until expectedSize) {

            bitmap[i] =
                outputArray[i] > DB_THRESH
        }

        /*
         * Connected component map.
         */
        val visited =
            BooleanArray(
                expectedSize
            )

        val candidates =
            mutableListOf<Component>()

        /*
         * Reusable BFS queues.
         */
        val queueX =
            IntArray(
                expectedSize
            )

        val queueY =
            IntArray(
                expectedSize
            )

        for (y in 0 until outputHeight) {

            for (x in 0 until outputWidth) {

                val startIndex =
                    y * outputWidth + x

                if (
                    !bitmap[startIndex] ||
                    visited[startIndex]
                ) {
                    continue
                }

                var head = 0
                var tail = 0

                queueX[tail] = x
                queueY[tail] = y
                tail++

                visited[startIndex] = true

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y

                var pixelCount = 0
                var scoreSum = 0.0

                while (head < tail) {

                    val cx =
                        queueX[head]

                    val cy =
                        queueY[head]

                    head++

                    pixelCount++

                    val index =
                        cy * outputWidth + cx

                    val probability =
                        outputArray[index]

                    scoreSum += probability

                    minX =
                        min(
                            minX,
                            cx
                        )

                    maxX =
                        max(
                            maxX,
                            cx
                        )

                    minY =
                        min(
                            minY,
                            cy
                        )

                    maxY =
                        max(
                            maxY,
                            cy
                        )

                    /*
                     * 8-neighbour connectivity.
                     */
                    for (dy in -1..1) {

                        for (dx in -1..1) {

                            if (
                                dx == 0 &&
                                dy == 0
                            ) {
                                continue
                            }

                            val nx =
                                cx + dx

                            val ny =
                                cy + dy

                            if (
                                nx < 0 ||
                                nx >= outputWidth ||
                                ny < 0 ||
                                ny >= outputHeight
                            ) {
                                continue
                            }

                            val neighbourIndex =
                                ny * outputWidth + nx

                            if (
                                bitmap[neighbourIndex] &&
                                !visited[neighbourIndex]
                            ) {

                                visited[neighbourIndex] = true

                                queueX[tail] = nx
                                queueY[tail] = ny

                                tail++
                            }
                        }
                    }
                }

                /*
                 * Ignore tiny components.
                 */
                if (pixelCount < 3) {
                    continue
                }

                val score =
                    (
                            scoreSum /
                                    pixelCount.toDouble()
                            ).toFloat()

                if (score < DB_BOX_THRESH) {
                    continue
                }

                candidates.add(
                    Component(
                        minX = minX,
                        maxX = maxX,
                        minY = minY,
                        maxY = maxY,
                        score = score
                    )
                )

                if (
                    candidates.size >=
                    DB_MAX_CANDIDATES
                ) {
                    break
                }
            }

            if (
                candidates.size >=
                DB_MAX_CANDIDATES
            ) {
                break
            }
        }

        Log.d(
            TAG,
            "DB candidates: ${candidates.size}"
        )

        val result =
            mutableListOf<TextBox>()

        /*
         * IMPORTANT:
         *
         * The ONNX output is:
         *
         * 448 x 960
         *
         * while the input is:
         *
         * 448 x 960
         *
         * for your current model.
         *
         * Therefore we calculate the mapping dynamically
         * instead of assuming output = input / 4.
         */
        val outputScaleX =
            resizeInfo.resizedWidth.toFloat() /
                    outputWidth.toFloat()

        val outputScaleY =
            resizeInfo.resizedHeight.toFloat() /
                    outputHeight.toFloat()

        for (component in candidates) {

            val width =
                component.maxX -
                        component.minX +
                        1

            val height =
                component.maxY -
                        component.minY +
                        1

            if (
                width < 2 ||
                height < 2
            ) {
                continue
            }

            /*
             * Lightweight DB unclip.
             */
            val expansion =
                (DB_UNCLIP_RATIO - 1.0f) *
                        0.5f

            val expandedWidth =
                width *
                        (1.0f + expansion)

            val expandedHeight =
                height *
                        (1.0f + expansion)

            val centerX =
                (
                        component.minX +
                                component.maxX
                        ) / 2.0f

            val centerY =
                (
                        component.minY +
                                component.maxY
                        ) / 2.0f

            val minX =
                centerX -
                        expandedWidth / 2.0f

            val maxX =
                centerX +
                        expandedWidth / 2.0f

            val minY =
                centerY -
                        expandedHeight / 2.0f

            val maxY =
                centerY +
                        expandedHeight / 2.0f

            /*
             * Output coordinates -> resized image.
             */
            val resizedMinX =
                minX *
                        outputScaleX

            val resizedMaxX =
                maxX *
                        outputScaleX

            val resizedMinY =
                minY *
                        outputScaleY

            val resizedMaxY =
                maxY *
                        outputScaleY

            /*
             * Resized image -> original image.
             */
            val originalMinX =
                clamp(
                    resizedMinX /
                            resizeInfo.ratioWidth,
                    0.0f,
                    originalWidth.toFloat()
                )

            val originalMaxX =
                clamp(
                    resizedMaxX /
                            resizeInfo.ratioWidth,
                    0.0f,
                    originalWidth.toFloat()
                )

            val originalMinY =
                clamp(
                    resizedMinY /
                            resizeInfo.ratioHeight,
                    0.0f,
                    originalHeight.toFloat()
                )

            val originalMaxY =
                clamp(
                    resizedMaxY /
                            resizeInfo.ratioHeight,
                    0.0f,
                    originalHeight.toFloat()
                )

            if (
                originalMaxX <= originalMinX ||
                originalMaxY <= originalMinY
            ) {
                continue
            }

            result.add(
                TextBox(
                    points = listOf(
                        Point(
                            originalMinX,
                            originalMinY
                        ),
                        Point(
                            originalMaxX,
                            originalMinY
                        ),
                        Point(
                            originalMaxX,
                            originalMaxY
                        ),
                        Point(
                            originalMinX,
                            originalMaxY
                        )
                    ),
                    score = component.score
                )
            )
        }

        /*
         * Sort:
         *
         * top -> bottom
         * left -> right
         */
        result.sortWith(
            compareBy<TextBox> {
                it.points.minOf { point ->
                    point.y
                }
            }.thenBy {
                it.points.minOf { point ->
                    point.x
                }
            }
        )

        return result
    }

    private fun clamp(
        value: Float,
        minValue: Float,
        maxValue: Float
    ): Float {

        return max(
            minValue,
            min(
                value,
                maxValue
            )
        )
    }

    private fun formatPoints(
        points: List<Point>
    ): String {

        return points.joinToString(
            separator = " "
        ) {
            "(${it.x.toInt()},${it.y.toInt()})"
        }
    }

    /**
     * Test detector against example2.jpeg.
     */
    fun testExample2() {

        val bitmap =
            context.assets.open(
                "example2.jpeg"
            ).use {
                BitmapFactory.decodeStream(it)
            }

        if (bitmap == null) {

            Log.e(
                TAG,
                "Could not load assets/example2.jpeg"
            )

            return
        }

        try {

            Log.d(
                TAG,
                "TEST IMAGE: " +
                        "${bitmap.width}x${bitmap.height}"
            )

            val boxes =
                detect(bitmap)

            Log.d(
                TAG,
                "TEST RESULT: " +
                        "${boxes.size} boxes"
            )

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    fun close() {

        ortSession.close()
    }

}
