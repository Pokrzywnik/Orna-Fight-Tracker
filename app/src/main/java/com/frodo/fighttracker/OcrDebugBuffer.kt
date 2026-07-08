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

    @Volatile
    var xpRect: Rect? = null

    @Volatile
    var ornsRect: Rect? = null

    @Volatile
    var goldRect: Rect? = null

    @Volatile
    var detectedY = -1

    @Volatile
    var xpLeft = -1
    @Volatile
    var xpTop = -1

    @Volatile
    var ornsLeft = -1
    @Volatile
    var ornsTop = -1

    @Volatile
    var goldLeft = -1
    @Volatile
    var goldTop = -1
}