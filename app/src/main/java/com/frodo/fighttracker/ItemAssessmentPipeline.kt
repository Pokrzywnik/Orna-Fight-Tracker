package com.frodo.fighttracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

class ItemAssessmentPipeline(
    private val context: Context
) {

    companion object {

        private const val TAG =
            "ItemAssessmentPipeline"
    }

    private val detector =
        PaddleOcrDetector(context)

    private val recognizer =
        PaddleOcrRecognizer(context)

    private val matcher =
        ItemMatcher(context)

    private val assessor =
        ItemAssessor()

    object ItemAssessmentManager {

        @Volatile
        private var pipeline: ItemAssessmentPipeline? = null

        private val lock = Any()

        fun initialize(context: Context) {

            if (pipeline != null) {
                return
            }

            synchronized(lock) {

                if (pipeline != null) {
                    return
                }

                Log.d(
                    "ItemAssessmentManager",
                    "Initializing ItemAssessmentPipeline..."
                )

                pipeline =
                    ItemAssessmentPipeline(
                        context.applicationContext
                    )

                Log.d(
                    "ItemAssessmentManager",
                    "ItemAssessmentPipeline initialized"
                )
            }
        }

        fun getPipeline(context: Context): ItemAssessmentPipeline {

            initialize(context)

            return pipeline!!
        }
    }

    data class PipelineResult(
        val item: ItemMatcher.ItemMatch?,
        val assessment: ItemAssessor.Assessment?,
        val recognitionResults:
        List<PaddleOcrRecognizer.RecognitionResult>
    )

    /**
     * Reload item database used by ItemMatcher.
     *
     * This allows changing items.json while the app is running,
     * without recreating the whole OCR/assessment pipeline.
     */
    fun reloadItemDatabase() {

        Log.d(
            TAG,
            "Reloading ItemMatcher database..."
        )

        matcher.reload()

        Log.d(
            TAG,
            "ItemMatcher database reloaded"
        )
    }

    fun processAllExamples(): List<PipelineResult> {

        Log.d(TAG, "========================================")
        Log.d(TAG, "BATCH ITEM ASSESSMENT")
        Log.d(TAG, "========================================")

        val exampleFiles =
            context.assets
                .list("Examples")
                ?.filter { fileName ->

                    val lower =
                        fileName.lowercase()

                    lower.endsWith(".png") ||
                            lower.endsWith(".jpg") ||
                            lower.endsWith(".jpeg")
                }
                ?.sortedWith(
                    compareBy { fileName ->
                        extractExampleNumber(fileName)
                    }
                )
                ?: emptyList()

        Log.d(
            TAG,
            "FOUND EXAMPLES: ${exampleFiles.size}"
        )

        for (file in exampleFiles) {

            Log.d(
                TAG,
                "FILE: Examples/$file"
            )
        }

        Log.d(TAG, "========================================")

        val results =
            mutableListOf<PipelineResult>()

        for (
        (index, fileName)
        in exampleFiles.withIndex()
        ) {

            Log.d(TAG, "")
            Log.d(TAG, "")
            Log.d(TAG, "########################################")
            Log.d(
                TAG,
                "PROCESSING ${index + 1}/${exampleFiles.size}"
            )
            Log.d(
                TAG,
                "FILE: Examples/$fileName"
            )
            Log.d(TAG, "########################################")

            val result =
                processExample(
                    fileName
                )

            if (result != null) {
                results.add(result)
            }
        }



        Log.d(TAG, "")
        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "BATCH SUMMARY")
        Log.d(TAG, "========================================")

        Log.d(
            TAG,
            "FILES FOUND: ${exampleFiles.size}"
        )

        Log.d(
            TAG,
            "FILES PROCESSED: ${results.size}"
        )

        val successful =
            results.count {
                it.item != null &&
                        it.assessment != null
            }

        val failed =
            results.size - successful

        Log.d(
            TAG,
            "SUCCESS: $successful"
        )

        Log.d(
            TAG,
            "FAILED: $failed"
        )

        Log.d(TAG, "----------------------------------------")

        for (
        (index, result)
        in results.withIndex()
        ) {

            val item =
                result.item

            val assessment =
                result.assessment

            if (
                item == null ||
                assessment == null
            ) {

                Log.d(
                    TAG,
                    "[$index] FAILED"
                )

                continue
            }

            val qualityText =
                if (assessment.exactQuality != null) {

                    "%.2f".format(
                        assessment.exactQuality * 100.0
                    ) + "%"

                } else if (
                    assessment.qualityRangeMin > 0.0 &&
                    assessment.qualityRangeMax > 0.0
                ) {

                    "%.2f".format(
                        assessment.qualityRangeMin * 100.0
                    ) +
                            "-" +
                            "%.2f".format(
                                assessment.qualityRangeMax * 100.0
                            ) +
                            "%"

                } else {

                    "UNKNOWN"
                }

            Log.d(
                TAG,
                "[$index] " +
                        "${assessment.itemName} | " +
                        "level=${assessment.upgradeLevel} | " +
                        "quality=$qualityText"
            )
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "BATCH FINISHED")
        Log.d(TAG, "========================================")

        return results
    }

    private fun processExample(
        fileName: String
    ): PipelineResult? {

        val assetPath =
            "Examples/$fileName"

        val bitmap =
            try {

                context.assets
                    .open(assetPath)
                    .use {
                        BitmapFactory.decodeStream(it)
                    }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Could not load $assetPath",
                    e
                )

                return null
            }

        if (bitmap == null) {

            Log.e(
                TAG,
                "BitmapFactory returned null: $assetPath"
            )

            return null
        }

        try {

            Log.d(
                TAG,
                "IMAGE: ${bitmap.width}x${bitmap.height}"
            )

            /*
             * ========================================
             * 1. DETECTION
             * ========================================
             */

            val boxes =
                detector.detect(
                    bitmap
                )

            Log.d(
                TAG,
                "DETECTED BOXES: ${boxes.size}"
            )

            /*
             * ========================================
             * 2. OCR
             * ========================================
             */

            val recognitionResults =
                recognizer.recognizeBoxes(
                    bitmap,
                    boxes
                )

            Log.d(
                TAG,
                "OCR RESULTS: ${recognitionResults.size}"
            )

            for (
            (index, result)
            in recognitionResults.withIndex()
            ) {

                Log.d(
                    TAG,
                    "OCR[$index] = " +
                            "[${result.text}] " +
                            "conf=" +
                            "%.4f".format(
                                result.confidence
                            )
                )
            }

            /*
             * ========================================
             * 3. FIND ITEM
             * ========================================
             */

            val itemResult =
                findItem(
                    recognitionResults
                )

            if (itemResult == null) {

                Log.e(
                    TAG,
                    "NO ITEM FOUND IN OCR"
                )

                return PipelineResult(
                    item = null,
                    assessment = null,
                    recognitionResults =
                        recognitionResults
                )
            }

            val item =
                itemResult.item

            Log.d(TAG, "----------------------------------------")
            Log.d(TAG, "MATCHED ITEM")
            Log.d(TAG, "NAME: ${item.name}")
            Log.d(TAG, "ID: ${item.id}")
            Log.d(TAG, "QUALITY: ${item.quality}")
            Log.d(TAG, "ENCHANT: ${item.enchant}")
            Log.d(TAG, "BOSS: ${item.isBoss}")
            Log.d(TAG, "BASE STATS: ${item.baseStats}")

            /*
             * ========================================
             * 4. DETECT LEVEL
             * ========================================
             */

            val upgradeLevel =
                detectUpgradeLevel(
                    recognitionResults
                ) ?: run {

                    Log.w(
                        TAG,
                        "COULD NOT DETECT ITEM LEVEL - assuming LEVEL 1"
                    )

                    "1"
                }

            Log.d(
                TAG,
                "UPGRADE LEVEL: $upgradeLevel"
            )

            Log.d(
                TAG,
                "UPGRADE LEVEL: $upgradeLevel"
            )

            /*
             * ========================================
             * 5. COLLECT OCR TEXT
             * ========================================
             */

            val ocrTexts =
                recognitionResults
                    .map {
                        it.text
                    }
                    .filter {
                        it.isNotBlank()
                    }

            /*
             * ========================================
             * 6. ASSESS
             * ========================================
             */

            val assessment =
                assessor.assess(
                    item = item,
                    upgradeLevel = upgradeLevel,
                    ocrTexts = ocrTexts
                )

            /*
             * ========================================
             * 7. RESULT
             * ========================================
             */

            Log.d(TAG, "----------------------------------------")
            Log.d(TAG, "FINAL RESULT")
            Log.d(TAG, "----------------------------------------")

            Log.d(
                TAG,
                "FILE: $fileName"
            )

            Log.d(
                TAG,
                "ITEM: ${assessment.itemName}"
            )

            Log.d(
                TAG,
                "ID: ${assessment.itemId}"
            )

            Log.d(
                TAG,
                "QUALITY PREFIX: ${assessment.quality}"
            )

            Log.d(
                TAG,
                "ENCHANT: ${assessment.enchant}"
            )

            Log.d(
                TAG,
                "LEVEL: ${assessment.upgradeLevel}"
            )

            if (assessment.exactQuality != null) {

                Log.d(
                    TAG,
                    "QUALITY: " +
                            "%.2f".format(
                                assessment.exactQuality * 100.0
                            ) +
                            "%"
                )

            } else if (
                assessment.qualityRangeMin > 0.0 &&
                assessment.qualityRangeMax > 0.0
            ) {

                Log.d(
                    TAG,
                    "QUALITY RANGE: " +
                            "%.2f".format(
                                assessment.qualityRangeMin * 100.0
                            ) +
                            "% - " +
                            "%.2f".format(
                                assessment.qualityRangeMax * 100.0
                            ) +
                            "%"
                )

            } else {

                Log.d(
                    TAG,
                    "QUALITY: UNKNOWN"
                )
            }

            for (stat in assessment.statQualities) {

                val statQualityText =
                    if (stat.exactQualityCandidates.size == 1) {

                        "${stat.exactQualityCandidates.first()}%"

                    } else {

                        "%.2f".format(
                            stat.minQuality * 100.0
                        ) +
                                "-" +
                                "%.2f".format(
                                    stat.maxQuality * 100.0
                                ) +
                                "%"
                    }

                Log.d(
                    TAG,
                    "STAT ${stat.statName}: " +
                            "actual=${stat.actualValue}, " +
                            "base=${stat.baseValue}, " +
                            "quality=$statQualityText, " +
                            "outlier=${stat.outlier}"
                )
            }

            Log.d(
                TAG,
                "========================================"
            )

            return PipelineResult(
                item = item,
                assessment = assessment,
                recognitionResults =
                    recognitionResults
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "FAILED PROCESSING: $assetPath",
                e
            )

            return null

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun extractExampleNumber(
        fileName: String
    ): Int {

        val match =
            Regex(
                """(?:\((\d+)\)|(\d+))"""
            )
                .find(
                    fileName
                )

        return (
                match?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: match?.groupValues?.getOrNull(2)
                        ?.toIntOrNull()
                    ?: 0
                )
    }

    fun processExample2(): PipelineResult? {

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "ITEM ASSESSMENT PIPELINE"
        )

        Log.d(
            TAG,
            "========================================"
        )

        val bitmap =
            context.assets
                .open("example2.jpeg")
                .use {
                    BitmapFactory.decodeStream(it)
                }

        if (bitmap == null) {

            Log.e(
                TAG,
                "Could not load example2.jpeg"
            )

            return null
        }

        try {

            Log.d(
                TAG,
                "IMAGE: " +
                        "${bitmap.width}x${bitmap.height}"
            )

            /*
             * ----------------------------------------
             * 1. DETECTION
             * ----------------------------------------
             */

            val boxes =
                detector.detect(
                    bitmap
                )

            Log.d(
                TAG,
                "DETECTED BOXES: ${boxes.size}"
            )

            /*
             * ----------------------------------------
             * 2. OCR
             * ----------------------------------------
             */

            val recognitionResults =
                recognizer.recognizeBoxes(
                    bitmap,
                    boxes
                )

            Log.d(
                TAG,
                "OCR RESULTS: ${recognitionResults.size}"
            )

            for (
            (index, result)
            in recognitionResults.withIndex()
            ) {

                Log.d(
                    TAG,
                    "OCR[$index] = " +
                            "[${result.text}] " +
                            "conf=" +
                            "%.4f".format(
                                result.confidence
                            )
                )
            }

            /*
             * ----------------------------------------
             * 3. FIND ITEM
             * ----------------------------------------
             */

            val itemResult =
                findItem(
                    recognitionResults
                )

            if (itemResult == null) {

                Log.e(
                    TAG,
                    "NO ITEM FOUND IN OCR"
                )

                return PipelineResult(
                    item = null,
                    assessment = null,
                    recognitionResults =
                        recognitionResults
                )
            }

            val item =
                itemResult.item

            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "MATCHED ITEM"
            )

            Log.d(
                TAG,
                "NAME: ${item.name}"
            )

            Log.d(
                TAG,
                "ID: ${item.id}"
            )

            Log.d(
                TAG,
                "QUALITY: ${item.quality}"
            )

            Log.d(
                TAG,
                "ENCHANT: ${item.enchant}"
            )

            Log.d(
                TAG,
                "BOSS: ${item.isBoss}"
            )

            Log.d(
                TAG,
                "BASE STATS: ${item.baseStats}"
            )

            /*
             * ----------------------------------------
             * 4. DETECT UPGRADE LEVEL
             * ----------------------------------------
             */

            val upgradeLevel =
                detectUpgradeLevel(
                    recognitionResults
                ) ?: run {

                    Log.w(
                        TAG,
                        "COULD NOT DETECT ITEM LEVEL - assuming LEVEL 1"
                    )

                    "1"
                }

            Log.d(
                TAG,
                "UPGRADE LEVEL: $upgradeLevel"
            )

            Log.d(
                TAG,
                "UPGRADE LEVEL: $upgradeLevel"
            )

            /*
             * ----------------------------------------
             * 5. COLLECT STAT OCR
             * ----------------------------------------
             *
             * We pass ALL OCR lines to ItemAssessor.
             *
             * Its parser will extract:
             *
             * HP:...
             * Mana:...
             * Att:...
             * Mag:...
             * Dex:...
             * Ward:...
             * Crit:...
             */

            val ocrTexts =
                recognitionResults
                    .map {
                        it.text
                    }
                    .filter {
                        it.isNotBlank()
                    }

            /*
             * ----------------------------------------
             * 6. ASSESS
             * ----------------------------------------
             */

            val assessment =
                assessor.assess(
                    item = item,
                    upgradeLevel = upgradeLevel,
                    ocrTexts = ocrTexts
                )

            /*
             * ----------------------------------------
             * 7. FINAL RESULT
             * ----------------------------------------
             */

            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "FINAL ITEM ASSESSMENT"
            )

            Log.d(
                TAG,
                "========================================"
            )

            Log.d(
                TAG,
                "ITEM: ${assessment.itemName}"
            )

            Log.d(
                TAG,
                "QUALITY PREFIX: ${assessment.quality}"
            )

            Log.d(
                TAG,
                "ENCHANT: ${assessment.enchant}"
            )

            Log.d(
                TAG,
                "LEVEL: ${assessment.upgradeLevel}"
            )

            if (assessment.exactQuality != null) {

                Log.d(
                    TAG,
                    "QUALITY: " +
                            "%.2f".format(
                                assessment.exactQuality * 100.0
                            ) +
                            "%"
                )

            } else {

                val hasValidRange =
                    assessment.qualityRangeMin > 0.0 &&
                            assessment.qualityRangeMax > 0.0 &&
                            assessment.qualityRangeMin <=
                            assessment.qualityRangeMax

                if (hasValidRange) {

                    Log.d(
                        TAG,
                        "QUALITY RANGE: " +
                                "%.2f".format(
                                    assessment.qualityRangeMin * 100.0
                                ) +
                                "-" +
                                "%.2f".format(
                                    assessment.qualityRangeMax * 100.0
                                ) +
                                "%"
                    )

                } else {

                    Log.d(
                        TAG,
                        "QUALITY: UNKNOWN"
                    )
                }
            }

            for (
            stat in assessment.statQualities
            ) {

                Log.d(
                    TAG,
                    "STAT ${stat.statName}: " +
                            "actual=${stat.actualValue}, " +
                            "base=${stat.baseValue}, " +
                            "quality=" +
                            "%.2f".format(
                                stat
                                    .estimatedQuality *
                                        100.0
                            ) +
                            "%, " +
                            "range=" +
                            "%.2f".format(
                                stat.minQuality *
                                        100.0
                            ) +
                            "-" +
                            "%.2f".format(
                                stat.maxQuality *
                                        100.0
                            ) +
                            "%, " +
                            "outlier=${stat.outlier}"
                )
            }

            Log.d(
                TAG,
                "========================================"
            )

            return PipelineResult(
                item = item,
                assessment = assessment,
                recognitionResults =
                    recognitionResults
            )

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private data class ItemCandidate(
        val item:
        ItemMatcher.ItemMatch,
        val result:
        PaddleOcrRecognizer.RecognitionResult
    )

    fun cancelCurrentInference() {

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "CANCELLING CURRENT ITEM ASSESSMENT INFERENCE"
        )

        /*
         * Terminate any native ONNX inference currently
         * running inside the detector.
         */
        detector.cancelCurrentRun()

        /*
         * Terminate any native ONNX inference currently
         * running inside the recognizer.
         */
        recognizer.cancelCurrentRuns()

        Log.d(
            TAG,
            "ONNX inference termination requested"
        )

        Log.d(
            TAG,
            "========================================"
        )
    }

    fun processBitmap(
        bitmap: Bitmap
    ): PipelineResult {

        Log.d(TAG, "========================================")
        Log.d(TAG, "LIVE ITEM ASSESSMENT")
        Log.d(TAG, "========================================")

        Log.d(
            TAG,
            "IMAGE: ${bitmap.width}x${bitmap.height}"
        )

        /*
         * 1. DETECTION
         */

        val pipelineStart =
            System.nanoTime()

        val detectionStart =
            System.nanoTime()

        val boxes =
            detector.detect(bitmap)

        val detectionMs =
            (System.nanoTime() - detectionStart) / 1_000_000.0

        Log.d(
            TAG,
            "DETECTION TIME: %.2f ms".format(
                detectionMs
            )
        )

        Log.d(
            TAG,
            "DETECTED BOXES: ${boxes.size}"
        )

        val ocrStart =
            System.nanoTime()

        val recognitionResults =
            recognizer.recognizeBoxes(
                bitmap,
                boxes
            )

        val ocrMs =
            (System.nanoTime() - ocrStart) / 1_000_000.0

        Log.d(
            TAG,
            "OCR PIPELINE TIME: %.2f ms".format(
                ocrMs
            )
        )

        Log.d(
            TAG,
            "DETECTED BOXES: ${boxes.size}"
        )

        /*
         * 2. OCR
         */


        Log.d(
            TAG,
            "OCR RESULTS: ${recognitionResults.size}"
        )

        for (
        (index, result)
        in recognitionResults.withIndex()
        ) {

            Log.d(
                TAG,
                "OCR[$index] = " +
                        "[${result.text}] " +
                        "conf=" +
                        "%.4f".format(
                            result.confidence
                        )
            )
        }

        /*
         * 3. FIND ITEM
         */

        val pipelineMs =
            (System.nanoTime() - pipelineStart) / 1_000_000.0

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "PIPELINE TIMING"
        )

        Log.d(
            TAG,
            "Detection: %.2f ms".format(
                detectionMs
            )
        )

        Log.d(
            TAG,
            "OCR: %.2f ms".format(
                ocrMs
            )
        )

        Log.d(
            TAG,
            "TOTAL: %.2f ms".format(
                pipelineMs
            )
        )

        Log.d(
            TAG,
            "========================================"
        )

        val itemResult =
            findItem(
                recognitionResults
            )

        if (itemResult == null) {

            Log.d(
                TAG,
                "NO ITEM FOUND"
            )

            return PipelineResult(
                item = null,
                assessment = null,
                recognitionResults =
                    recognitionResults
            )
        }

        val item =
            itemResult.item

        Log.d(
            TAG,
            "MATCHED ITEM: ${item.name}"
        )

        /*
         * 4. LEVEL
         */

        val upgradeLevel =
            detectUpgradeLevel(
                recognitionResults
            ) ?: run {

                Log.w(
                    TAG,
                    "LEVEL NOT FOUND - assuming 1"
                )

                "1"
            }

        /*
         * 5. OCR TEXT
         */

        val ocrTexts =
            recognitionResults
                .map {
                    it.text
                }
                .filter {
                    it.isNotBlank()
                }

        /*
         * 6. ASSESSMENT
         */

        val assessment =
            assessor.assess(
                item = item,
                upgradeLevel = upgradeLevel,
                ocrTexts = ocrTexts
            )

        /*
         * 7. LOG RESULT
         */

        if (assessment.exactQuality != null) {

            Log.d(
                TAG,
                "QUALITY: %.2f%%".format(
                    assessment.exactQuality * 100.0
                )
            )

        } else {

            Log.d(
                TAG,
                "QUALITY RANGE: %.2f%% - %.2f%%".format(
                    assessment.qualityRangeMin * 100.0,
                    assessment.qualityRangeMax * 100.0
                )
            )
        }

        return PipelineResult(
            item = item,
            assessment = assessment,
            recognitionResults =
                recognitionResults
        )
    }

    private fun findItem(
        results:
        List<PaddleOcrRecognizer.RecognitionResult>
    ): ItemCandidate? {

        for (result in results) {

            val text =
                result.text.trim()

            if (text.isBlank()) {
                continue
            }

            val match =
                matcher.match(
                    text
                )

            if (match != null) {

                Log.d(
                    TAG,
                    "ITEM MATCH FROM OCR: [$text]"
                )

                return ItemCandidate(
                    item = match,
                    result = result
                )
            }
        }

        return null
    }

    private fun detectUpgradeLevel(
        results:
        List<PaddleOcrRecognizer.RecognitionResult>
    ): String? {

        for (result in results) {

            val text =
                result.text
                    .trim()
                    .uppercase()

            val match =
                Regex(
                    """LEVEL\s*(\d+)"""
                )
                    .find(text)

            if (match != null) {

                val number =
                    match.groupValues[1]
                        .toIntOrNull()
                        ?: continue

                return when (number) {

                    1 -> "1"
                    2 -> "2"
                    3 -> "3"
                    4 -> "4"
                    5 -> "5"
                    6 -> "6"
                    7 -> "7"
                    8 -> "8"
                    9 -> "9"
                    10 -> "10"
                    11 -> "MF"
                    12 -> "DF"
                    13 -> "GF"

                    else -> null
                }
            }
        }

        return null
    }
}