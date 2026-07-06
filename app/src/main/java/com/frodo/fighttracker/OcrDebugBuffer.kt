package com.frodo.fighttracker

import android.graphics.Rect

object OcrDebugBuffer {

    @Volatile
    var lastText: String? = null

    // User crop in screen coordinates
    @Volatile
    var userCrop: Rect? = null

    // Final OCR crop after auto left-edge detection
    @Volatile
    var finalCrop: Rect? = null

    // Detected left edge in screen coordinates
    @Volatile
    var detectedLeft = -1

    var bookmarkRect: Rect? = null
}