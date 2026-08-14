package com.frodo.fighttracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min



class PaddleOcrRecognizer(
    private val context: Context
) {

    companion object {
        private const val TAG = "PaddleOCR-REC"

        private const val MODEL_FILE =
            "ocr/rec/model2/inference.onnx"


        /*
         * PP-OCRv6 recognition input height.
         *
         * Width is dynamic and depends on the
         * aspect ratio of the cropped text.
         */
        private const val TARGET_HEIGHT = 48

        /*
         * PaddleOCR recognition normally allows
         * variable width.
         *
         * We keep a reasonable upper limit so that
         * a malformed detector box cannot allocate
         * an enormous tensor.
         */
        private const val MAX_WIDTH = 3200

        private val WIDTH_BUCKETS =
            listOf(
                64, 96, 128, 160, 192, 256,
                320, 384, 512, 640, 768, 896,
                1024, 1280, 1536, 1792, 2048,
                2560, 3200
            )

        private const val MAX_BATCH_SIZE = 16
        /*
         * Small horizontal padding around detected
         * text boxes.
         */
        private const val CROP_PADDING_X = 4

        /*
         * Vertical padding around detected text.
         */
        private const val CROP_PADDING_Y = 2

        /*
         * Minimum confidence for a decoded character.
         *
         * Keep this low initially. We want to see
         * what the model actually produces.
         */
        private const val MIN_CHAR_CONFIDENCE = 0.0f
    }

    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val box: PaddleOcrDetector.TextBox? = null
    )

    private data class PreparedCrop(
        val index: Int,
        val bitmap: Bitmap,
        val box: PaddleOcrDetector.TextBox
    )

    private val ortEnvironment: OrtEnvironment =
        OrtEnvironment.getEnvironment()

    private val ortSession: OrtSession

    private val dictionary: List<String>

    init {

        Log.d(TAG, "========================================")
        Log.d(TAG, "Initializing PP-OCRv6 RECOGNIZER")
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

        dictionary =
            loadDictionary()

        Log.d(
            TAG,
            "PP-OCRv6 RECOGNIZER loaded"
        )

        Log.d(
            TAG,
            "Inputs: ${ortSession.inputInfo.keys}"
        )

        Log.d(
            TAG,
            "Outputs: ${ortSession.outputInfo.keys}"
        )

        Log.d(
            TAG,
            "Dictionary size: ${dictionary.size}"
        )

        Log.d(
            TAG,
            "Dictionary first 20: " +
                    dictionary.take(20)
        )

        Log.d(
            TAG,
            "Dictionary contains SPACE: " +
                    dictionary.contains(" ")
        )

        Log.d(
            TAG,
            "Dictionary space indexes: " +
                    dictionary.mapIndexedNotNull { index, value ->
                        if (value == " ") index else null
                    }
        )

        for ((name, info) in ortSession.inputInfo) {

            Log.d(
                TAG,
                "INPUT $name = $info"
            )
        }

        for ((name, info) in ortSession.outputInfo) {

            Log.d(
                TAG,
                "OUTPUT $name = $info"
            )
        }

        Log.d(TAG, "========================================")
    }

    /**
     * Loads PaddleOCR character dictionary.
     *
     * CTC blank is index 0.
     */
    private fun loadDictionary(): List<String> {

        val result = mutableListOf<String>()

        /*
         * CTC blank MUST be index 0.
         *
         * PaddleOCR CTCLabelDecode does:
         *
         * 0 = blank
         * 1 = character_dict[0]
         * 2 = character_dict[1]
         * ...
         */
        result.add("<BLANK>")

        val yamlText =
            context.assets.open(
                "ocr/rec/model2/inference.yml"
            )
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        val lines =
            yamlText.lines()

        var insideCharacterDict = false

        for (rawLine in lines) {

            val line =
                rawLine.trim()

            /*
             * Find:
             *
             * character_dict:
             */
            if (line == "character_dict:") {

                insideCharacterDict = true
                continue
            }

            if (!insideCharacterDict) {
                continue
            }

            /*
             * Every dictionary entry is:
             *
             * - 'A'
             * - B
             * - '\'
             *
             * Stop when another YAML section starts.
             */
            if (!line.startsWith("-")) {

                if (
                    line.isNotEmpty() &&
                    !line.startsWith("#")
                ) {
                    break
                }

                continue
            }

            var value =
                line.substring(1).trim()

            if (value.isEmpty()) {
                continue
            }

            /*
             * Remove YAML single/double quotes.
             */
            if (
                value.length >= 2 &&
                (
                        (
                                value.first() == '\'' &&
                                        value.last() == '\''
                                ) ||
                                (
                                        value.first() == '"' &&
                                                value.last() == '"'
                                        )
                        )
            ) {
                value =
                    value.substring(
                        1,
                        value.length - 1
                    )
            }

            /*
             * YAML represents a single backslash
             * as "\" in this file.
             *
             * Keep the actual character.
             */
            if (value == "\\") {
                value = "\\"
            }

            result.add(value)
        }

        Log.d(
            TAG,
            "Loaded character_dict from inference.yml"
        )

        Log.d(
            TAG,
            "Dictionary size = ${result.size}"
        )

        Log.d(
            TAG,
            "First 20 = ${result.take(20)}"
        )

        Log.d(
            TAG,
            "Last 20 = ${result.takeLast(20)}"
        )

        return result
    }

    /**
     * Recognize one bitmap.
     */
    fun recognize(bitmap: Bitmap): String {

        return recognizeDetailed(bitmap).text
    }

    /**
     * Recognize one bitmap and return text +
     * confidence.
     */
    fun recognizeDetailed(
        bitmap: Bitmap
    ): RecognitionResult {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val inputBitmap =
            resizeForRecognition(bitmap)

        Log.d(
            TAG,
            "OCR input bitmap: " +
                    "${inputBitmap.width}x${inputBitmap.height}"
        )

        val inputData =
            bitmapToFloatArray(
                inputBitmap
            )

        val shape =
            longArrayOf(
                1,
                3,
                TARGET_HEIGHT.toLong(),
                inputBitmap.width.toLong()
            )

        val inputName =
            ortSession.inputInfo.keys.first()

        val tensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(inputData),
                shape
            )

        try {

            tensor.use {

                val inputs =
                    mapOf(
                        inputName to tensor
                    )

                ortSession.run(
                    inputs
                ).use { result ->

                    Log.d(
                        TAG,
                        "Output count: ${result.size()}"
                    )

                    if (result.size() == 0) {

                        Log.e(
                            TAG,
                            "Recognizer returned no output"
                        )

                        return RecognitionResult(
                            text = "",
                            confidence = 0.0f
                        )
                    }

                    val output =
                        result[0].value

                    return decodeOutput(
                        output
                    )
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Recognition inference failed",
                e
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )

        } finally {

            if (
                inputBitmap !== bitmap &&
                !inputBitmap.isRecycled
            ) {
                inputBitmap.recycle()
            }
        }
    }

    /**
     * Recognize a detector TextBox directly.
     *
     * The detector returns coordinates in the
     * original bitmap coordinate system.
     */
    fun recognizeBox(
        source: Bitmap,
        box: PaddleOcrDetector.TextBox
    ): RecognitionResult {

        val crop =
            cropTextBox(
                source,
                box
            )

        if (crop == null) {

            Log.w(
                TAG,
                "Could not create crop for box"
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f,
                box = box
            )
        }

        try {

            val result =
                recognizeDetailed(
                    crop
                )

            return result.copy(
                box = box
            )

        } finally {

            if (!crop.isRecycled) {
                crop.recycle()
            }
        }
    }

    /**
     * Smallest bucket that fits the given width.
     * Never exceeds MAX_WIDTH.
     */
    private fun bucketFor(width: Int): Int {

        for (bucket in WIDTH_BUCKETS) {

            if (width <= bucket) {
                return bucket
            }
        }

        return MAX_WIDTH
    }

    /**
     * Adaptive batch cap: keep every tensor under
     * ~2.4M floats (~9.4 MB) regardless of bucket.
     * Wide buckets automatically get smaller batches.
     */
    private fun maxBatchFor(bucketWidth: Int): Int {

        val budget = 3 * 48 * 1024 * 16

        val perImage = 3 * 48 * bucketWidth

        return max(1, min(MAX_BATCH_SIZE, budget / perImage))
    }

    /**
     * One ONNX call for crops sharing one padded width.
     */
    private fun runBatchInference(
        batch: List<PreparedCrop>,
        bucketWidth: Int
    ): Map<Int, RecognitionResult> {

        if (batch.isEmpty()) {
            return emptyMap()
        }

        val batchSize = batch.size
        val channelSize = TARGET_HEIGHT * bucketWidth
        val singleImageSize = 3 * channelSize

        val inputData =
            FloatArray(batchSize * singleImageSize)

        for ((batchIndex, prepared) in batch.withIndex()) {

            bitmapToFloatArrayPadded(
                bitmap = prepared.bitmap,
                targetWidth = bucketWidth,
                output = inputData,
                batchOffset = batchIndex * singleImageSize
            )
        }

        val shape =
            longArrayOf(
                batchSize.toLong(),
                3,
                TARGET_HEIGHT.toLong(),
                bucketWidth.toLong()
            )

        val inputName =
            ortSession.inputInfo.keys.first()

        val tensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(inputData),
                shape
            )

        val decodedResults =
            try {

                tensor.use {

                    val inputs =
                        mapOf(inputName to tensor)

                    ortSession.run(inputs).use { result ->

                        if (result.size() == 0) {

                            Log.e(
                                TAG,
                                "Recognizer returned no output"
                            )

                            emptyList()

                        } else {

                            decodeBatchOutput(
                                result[0].value
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Batch recognition inference failed",
                    e
                )

                List(batchSize) {
                    RecognitionResult(
                        text = "",
                        confidence = 0.0f
                    )
                }
            }

        val byIndex =
            mutableMapOf<Int, RecognitionResult>()

        for ((batchIndex, prepared) in batch.withIndex()) {

            byIndex[prepared.index] =
                decodedResults.getOrNull(batchIndex)
                    ?: RecognitionResult(
                        text = "",
                        confidence = 0.0f
                    )
        }

        return byIndex
    }

    /**
     * Run recognizer against all detector boxes.
     */
    fun recognizeBoxes(
        source: Bitmap,
        boxes: List<PaddleOcrDetector.TextBox>
    ): List<RecognitionResult> {

        val totalStart = System.nanoTime()

        Log.d(TAG, "========================================")
        Log.d(TAG, "BATCH OCR START")
        Log.d(TAG, "Input boxes: ${boxes.size}")
        Log.d(TAG, "========================================")

        if (boxes.isEmpty()) {
            return emptyList()
        }

        /*
         * ----------------------------------------
         * 1. CREATE CROPS
         * ----------------------------------------
         */
        val prepStart = System.nanoTime()

        val prepared = mutableListOf<PreparedCrop>()

        for ((index, box) in boxes.withIndex()) {

            val crop = cropTextBox(source, box)

            if (crop == null) {
                Log.w(TAG, "BOX $index: crop failed")
                continue
            }

            try {

                val resized = resizeForRecognition(crop)

                prepared.add(
                    PreparedCrop(
                        index = index,
                        bitmap = resized,
                        box = box
                    )
                )

            } finally {

                if (!crop.isRecycled) {
                    crop.recycle()
                }
            }
        }

        val prepMs =
            (System.nanoTime() - prepStart) / 1_000_000.0

        Log.d(TAG, "OCR PREP: %.2f ms".format(prepMs))

        if (prepared.isEmpty()) {
            return boxes.map {
                RecognitionResult(
                    text = "",
                    confidence = 0.0f,
                    box = it
                )
            }
        }

        /*
         * ----------------------------------------
         * 2. GROUP CROPS BY WIDTH BUCKET
         * ----------------------------------------
         *
         * All images in one ONNX tensor must share
         * the same width, but padding everything to
         * the single widest box wastes most of the
         * tensor. Group by bucket and run one
         * inference per bucket.
         */
        val bucketStart = System.nanoTime()

        val byBucket =
            prepared.groupBy {
                bucketFor(it.bitmap.width)
            }

        val bucketMs =
            (System.nanoTime() - bucketStart) / 1_000_000.0

        Log.d(
            TAG,
            "OCR BUCKETS: " +
                    byBucket.entries
                        .sortedBy { it.key }
                        .joinToString(", ") {
                            "${it.key}(${it.value.size})"
                        }
        )

        Log.d(TAG, "BUCKET PREP: %.2f ms".format(bucketMs))

        /*
         * ----------------------------------------
         * 3. INFERENCE PER BUCKET / CHUNK
         * ----------------------------------------
         */
        val inferenceStart = System.nanoTime()

        val decodedByIndex =
            mutableMapOf<Int, RecognitionResult>()

        for ((bucketWidth, group) in byBucket) {

            for (chunk in group.chunked(maxBatchFor(bucketWidth))) {

                Log.d(
                    TAG,
                    "RUN BATCH: bucket=$bucketWidth size=${chunk.size}"
                )

                decodedByIndex.putAll(
                    runBatchInference(
                        batch = chunk,
                        bucketWidth = bucketWidth
                    )
                )
            }
        }

        val inferenceMs =
            (System.nanoTime() - inferenceStart) / 1_000_000.0

        Log.d(TAG, "OCR INFERENCE: %.2f ms".format(inferenceMs))

        /*
         * ----------------------------------------
         * 4. MAP RESULTS BACK TO ORIGINAL BOXES
         * ----------------------------------------
         */
        val results =
            MutableList(boxes.size) {
                RecognitionResult(
                    text = "",
                    confidence = 0.0f,
                    box = boxes[it]
                )
            }

        for (preparedItem in prepared) {

            val decoded =
                decodedByIndex[preparedItem.index]

            if (decoded != null) {

                results[preparedItem.index] =
                    decoded.copy(box = preparedItem.box)
            }
        }

        /*
         * ----------------------------------------
         * 5. CLEANUP
         * ----------------------------------------
         */
        for (item in prepared) {

            if (!item.bitmap.isRecycled) {
                item.bitmap.recycle()
            }
        }

        val totalMs =
            (System.nanoTime() - totalStart) / 1_000_000.0

        Log.d(TAG, "========================================")
        Log.d(TAG, "BATCH OCR FINISHED")
        Log.d(TAG, "RESULTS: ${results.size}")
        Log.d(TAG, "OCR TOTAL: %.2f ms".format(totalMs))
        Log.d(TAG, "========================================")

        return results
    }

    /**
     * Crop detector rectangle from original bitmap.
     *
     * TextBox currently contains four points.
     *
     * We use an axis-aligned bounding rectangle.
     *
     * This is sufficient for the current screenshots
     * because detected text is horizontal.
     */
    private fun cropTextBox(
        source: Bitmap,
        box: PaddleOcrDetector.TextBox
    ): Bitmap? {

        if (box.points.isEmpty()) {
            return null
        }

        var minX =
            Float.MAX_VALUE

        var maxX =
            -Float.MIN_VALUE

        var minY =
            Float.MAX_VALUE

        var maxY =
            -Float.MIN_VALUE

        for (point in box.points) {

            minX =
                min(
                    minX,
                    point.x
                )

            maxX =
                max(
                    maxX,
                    point.x
                )

            minY =
                min(
                    minY,
                    point.y
                )

            maxY =
                max(
                    maxY,
                    point.y
                )
        }

        /*
         * Add small padding.
         */
        minX -= CROP_PADDING_X
        maxX += CROP_PADDING_X

        minY -= CROP_PADDING_Y
        maxY += CROP_PADDING_Y

        val left =
            max(
                0,
                minX.toInt()
            )

        val top =
            max(
                0,
                minY.toInt()
            )

        val right =
            min(
                source.width,
                kotlin.math.ceil(
                    maxX.toDouble()
                ).toInt()
            )

        val bottom =
            min(
                source.height,
                kotlin.math.ceil(
                    maxY.toDouble()
                ).toInt()
            )

        val width =
            right - left

        val height =
            bottom - top

        if (
            width <= 0 ||
            height <= 0
        ) {
            return null
        }

        Log.d(
            TAG,
            "Crop: " +
                    "$left,$top " +
                    "${right}x$bottom " +
                    "size=${width}x$height"
        )

        return try {

            Bitmap.createBitmap(
                source,
                left,
                top,
                width,
                height
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to crop text box",
                e
            )

            null
        }
    }

    /**
     * Resize recognition image to height 48 while
     * preserving aspect ratio.
     */
    private fun resizeForRecognition(
        source: Bitmap
    ): Bitmap {

        if (
            source.width <= 0 ||
            source.height <= 0
        ) {
            return source
        }

        val scale =
            TARGET_HEIGHT.toFloat() /
                    source.height.toFloat()

        var resizedWidth =
            max(
                1,
                (source.width * scale)
                    .toInt()
            )

        /*
         * Do not make a gigantic recognition tensor.
         */
        resizedWidth =
            min(
                MAX_WIDTH,
                resizedWidth
            )

        Log.d(
            TAG,
            "Resizing " +
                    "${source.width}x${source.height}" +
                    " -> " +
                    "${resizedWidth}x$TARGET_HEIGHT"
        )

        return Bitmap.createScaledBitmap(
            source,
            resizedWidth,
            TARGET_HEIGHT,
            true
        )
    }

    private fun bitmapToFloatArrayPadded(
        bitmap: Bitmap,
        targetWidth: Int,
        output: FloatArray,
        batchOffset: Int
    ) {

        val width =
            bitmap.width

        val height =
            bitmap.height

        val pixels =
            IntArray(
                width * height
            )

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val channelSize =
            TARGET_HEIGHT * targetWidth

        /*
         * PaddleOCR normally expects the padded
         * area to be white.
         *
         * Normalized white = +1.
         */

        java.util.Arrays.fill(
            output,
            batchOffset,
            batchOffset + channelSize * 3,
            1.0f
        )

        for (y in 0 until height) {

            for (x in 0 until width) {

                val pixel =
                    pixels[
                        y * width + x
                    ]

                val r =
                    Color.red(pixel)

                val g =
                    Color.green(pixel)

                val b =
                    Color.blue(pixel)

                val index =
                    y * targetWidth + x

                /*
                 * B
                 */
                output[
                    batchOffset + index
                ] =
                    b / 127.5f - 1.0f

                /*
                 * G
                 */
                output[
                    batchOffset +
                            channelSize +
                            index
                ] =
                    g / 127.5f - 1.0f

                /*
                 * R
                 */
                output[
                    batchOffset +
                            2 * channelSize +
                            index
                ] =
                    r / 127.5f - 1.0f
            }
        }
    }

    private fun decodeBatchOutput(
        output: Any?
    ): List<RecognitionResult> {

        if (output == null) {

            Log.e(
                TAG,
                "Batch OCR output is null"
            )

            return emptyList()
        }

        if (output !is Array<*>) {

            Log.e(
                TAG,
                "Unexpected batch OCR output type: " +
                        output.javaClass
            )

            return emptyList()
        }

        Log.d(
            TAG,
            "Batch output dimensions: " +
                    describeArray(output)
        )

        val batchSize =
            output.size

        val results =
            ArrayList<RecognitionResult>(
                batchSize
            )

        for (
        batchIndex
        in 0 until batchSize
        ) {

            val batchItem =
                output[batchIndex]

            if (
                batchItem !is Array<*>
            ) {

                results.add(
                    RecognitionResult(
                        text = "",
                        confidence = 0.0f
                    )
                )

                continue
            }

            val result =
                decodeSingleBatchItem(
                    batchItem
                )

            results.add(result)
        }

        return results
    }

    private fun decodeSingleBatchItem(
        batchItem: Array<*>
    ): RecognitionResult {

        val sequenceLength =
            batchItem.size

        if (sequenceLength == 0) {

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val firstTimeStep =
            batchItem[0]

        if (
            firstTimeStep !is FloatArray
        ) {

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val classCount =
            firstTimeStep.size

        val text =
            StringBuilder()

        var previousIndex =
            -1

        var confidenceSum =
            0.0

        var confidenceCount =
            0

        for (
        timeStepIndex
        in 0 until sequenceLength
        ) {

            val timeStep =
                batchItem[timeStepIndex]

            if (
                timeStep !is FloatArray
            ) {
                continue
            }

            var bestIndex =
                0

            var bestValue =
                Float.NEGATIVE_INFINITY

            for (
            classIndex
            in 0 until classCount
            ) {

                val value =
                    timeStep[classIndex]

                if (
                    value > bestValue
                ) {

                    bestValue =
                        value

                    bestIndex =
                        classIndex
                }
            }

            if (
                bestIndex != 0 &&
                bestIndex != previousIndex &&
                bestIndex < dictionary.size
            ) {

                val character =
                    dictionary[bestIndex]

                if (
                    character != "<BLANK>"
                ) {

                    text.append(character)

                    if (
                        bestValue >=
                        MIN_CHAR_CONFIDENCE
                    ) {

                        confidenceSum +=
                            bestValue.toDouble()

                        confidenceCount++
                    }
                }
            }

            previousIndex =
                bestIndex
        }

        val confidence =
            if (confidenceCount > 0) {

                (
                        confidenceSum /
                                confidenceCount
                        ).toFloat()

            } else {

                0.0f
            }

        return RecognitionResult(
            text = text.toString(),
            confidence = confidence
        )
    }
    /**
     * PaddleOCR recognition preprocessing.
     *
     * RGB bitmap
     *     ↓
     * BGR
     *     ↓
     * /255
     *     ↓
     * (x - 0.5) / 0.5
     *     ↓
     * CHW
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

        val channelSize =
            pixelCount

        for (y in 0 until height) {

            for (x in 0 until width) {

                val pixel =
                    pixels[
                        y * width + x
                    ]

                val r =
                    Color.red(pixel)

                val g =
                    Color.green(pixel)

                val b =
                    Color.blue(pixel)

                val index =
                    y * width + x

                /*
                 * B
                 */
                result[index] =
                    b / 127.5f - 1.0f

                /*
                 * G
                 */
                result[
                    channelSize + index
                ] =
                    g / 127.5f - 1.0f

                /*
                 * R
                 */
                result[
                    2 * channelSize + index
                ] =
                    r / 127.5f - 1.0f
            }
        }

        return result
    }

    /**
     * Decode PP-OCR CTC output.
     *
     * Expected structure:
     *
     * [batch][time][class]
     *
     * Usually:
     *
     * Array<Array<FloatArray>>
     *
     * We intentionally do NOT hardcode the number
     * of output classes.
     */
    private fun decodeOutput(
        output: Any?
    ): RecognitionResult {

        if (output == null) {

            Log.e(
                TAG,
                "OCR output is null"
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        Log.d(
            TAG,
            "Output runtime type: " +
                    output.javaClass.name
        )

        if (output !is Array<*>) {

            Log.e(
                TAG,
                "Unexpected OCR output type: " +
                        output.javaClass
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        Log.d(
            TAG,
            "Output dimensions: " +
                    describeArray(output)
        )

        if (output.isEmpty()) {

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val batch =
            output[0]

        if (batch !is Array<*>) {

            Log.e(
                TAG,
                "Unexpected OCR batch type: " +
                        "${batch?.javaClass}"
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val sequenceLength =
            batch.size

        if (sequenceLength == 0) {

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        /*
         * The actual number of classes is obtained
         * directly from the first timestep.
         */
        val firstTimeStep =
            batch[0]

        if (firstTimeStep !is FloatArray) {

            Log.e(
                TAG,
                "Unexpected timestep type: " +
                        "${firstTimeStep?.javaClass}"
            )

            return RecognitionResult(
                text = "",
                confidence = 0.0f
            )
        }

        val classCount =
            firstTimeStep.size

        Log.d(
            TAG,
            "OCR sequence length = " +
                    sequenceLength
        )

        Log.d(
            TAG,
            "OCR class count = " +
                    classCount
        )

        /*
         * With the dictionary loaded as:
         *
         * 0 = <BLANK>
         * 1 = first dictionary character
         * ...
         *
         * the valid character range is:
         *
         * 0 until dictionary.size
         *
         * Some Paddle exports contain one additional
         * special class. We simply ignore anything
         * outside the dictionary.
         */
        val blankIndex =
            0

        val text =
            StringBuilder()

        var previousIndex =
            -1

        var confidenceSum =
            0.0

        var confidenceCount =
            0

        for (
        timeStepIndex
        in 0 until sequenceLength
        ) {

            val timeStep =
                batch[
                    timeStepIndex
                ]

            if (
                timeStep !is FloatArray
            ) {

                Log.w(
                    TAG,
                    "Invalid timestep " +
                            "$timeStepIndex"
                )

                continue
            }

            var bestIndex =
                0

            var bestValue =
                Float.NEGATIVE_INFINITY

            for (
            classIndex
            in timeStep.indices
            ) {

                val value =
                    timeStep[classIndex]

                if (
                    value > bestValue
                ) {

                    bestValue =
                        value

                    bestIndex =
                        classIndex
                }
            }

            /*
             * Some exported ONNX models return logits,
             * while others return probabilities.
             *
             * For selecting the best character the
             * argmax is identical.
             *
             * We keep the raw value for diagnostics.
             */

            if (
                bestIndex != blankIndex &&
                bestIndex != previousIndex &&
                bestIndex < dictionary.size
            ) {

                val character =
                    dictionary[bestIndex]

                if (
                    character != "<BLANK>"
                ) {

                    text.append(character)

                    if (
                        bestValue >=
                        MIN_CHAR_CONFIDENCE
                    ) {

                        confidenceSum +=
                            bestValue

                        confidenceCount++
                    }
                }
            }

            previousIndex =
                bestIndex
        }

        val resultText =
            text.toString()

        val confidence =
            if (confidenceCount > 0) {
                (
                        confidenceSum /
                                confidenceCount
                        ).toFloat()
            } else {
                0.0f
            }

        Log.d(
            TAG,
            "OCR RESULT = [$resultText]"
        )

        Log.d(
            TAG,
            "OCR CONFIDENCE = " +
                    "%.4f".format(
                        confidence
                    )
        )

        return RecognitionResult(
            text = resultText,
            confidence = confidence
        )
    }

    /**
     * Describe nested Java arrays.
     */
    private fun describeArray(
        array: Array<*>
    ): String {

        val dimensions =
            mutableListOf<Int>()

        var current: Any? =
            array

        while (
            current is Array<*>
        ) {

            dimensions.add(
                current.size
            )

            current =
                if (
                    current.isNotEmpty()
                ) {
                    current[0]
                } else {
                    null
                }
        }

        if (
            current is FloatArray
        ) {
            dimensions.add(
                current.size
            )
        }

        return dimensions.joinToString(
            " x "
        )
    }

    /**
     * Test recognizer using a synthetic image.
     *
     * This is only a basic sanity test.
     */
    fun testSynthetic() {

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "SYNTHETIC OCR TEST"
        )

        Log.d(
            TAG,
            "========================================"
        )

        val bitmap =
            createOcrTestBitmap()

        try {

            val result =
                recognizeDetailed(
                    bitmap
                )

            Log.d(
                TAG,
                "SYNTHETIC RESULT: " +
                        "[${result.text}]"
            )

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Full test:
     *
     * example2.jpeg
     *      ↓
     * detector
     *      ↓
     * text boxes
     *      ↓
     * recognizer
     */
    fun testExample2FullPipeline(
        detector: PaddleOcrDetector
    ) {

        Log.d(TAG, "========================================")
        Log.d(TAG, "FULL PP-OCR PIPELINE TEST")
        Log.d(TAG, "========================================")

        val bitmap =
            context.assets.open(
                "example2.jpeg"
            ).use {
                BitmapFactory.decodeStream(it)
            }

        if (bitmap == null) {

            Log.e(
                TAG,
                "Could not load " +
                        "assets/example2.jpeg"
            )

            return
        }

        try {

            Log.d(
                TAG,
                "TEST IMAGE: " +
                        "${bitmap.width}x${bitmap.height}"
            )

            /*
             * 1. Detection
             */
            val boxes =
                detector.detect(
                    bitmap
                )

            Log.d(
                TAG,
                "DETECTOR FOUND: " +
                        "${boxes.size} boxes"
            )

            /*
             * 2. Recognition
             */
            val results =
                recognizeBoxes(
                    bitmap,
                    boxes
                )

            /*
             * 3. Final combined output
             */
            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "FINAL OCR RESULTS"
            )

            Log.d(
                TAG,
                "========================================"
            )

            for (
            (index, result)
            in results.withIndex()
            ) {

                Log.d(
                    TAG,
                    "[$index] " +
                            "[${result.text}] " +
                            "conf=" +
                            "%.4f".format(
                                result.confidence
                            )
                )
            }

            Log.d(
                TAG,
                "========================================"
            )

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun createOcrTestBitmap(): Bitmap {

        val width =
            640

        val height =
            TARGET_HEIGHT

        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(bitmap)

        canvas.drawColor(
            Color.WHITE
        )

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.BLACK

                textSize =
                    32f

                typeface =
                    android.graphics.Typeface.DEFAULT
            }

        canvas.drawText(
            "Fallen Sky",
            20f,
            36f,
            paint
        )

        return bitmap
    }

    private fun formatPoints(
        points: List<PaddleOcrDetector.Point>
    ): String {

        return points.joinToString(
            separator = " "
        ) {

            "(${it.x.toInt()},${it.y.toInt()})"
        }
    }

    fun close() {

        ortSession.close()
    }
}