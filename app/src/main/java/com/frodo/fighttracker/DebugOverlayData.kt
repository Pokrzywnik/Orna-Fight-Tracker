package com.frodo.fighttracker

object DebugOverlayData {

    @Volatile
    var detectedLeft: Int = 0

    @Volatile
    var bitmapWidth: Int = 1

    @Volatile
    var userCropLeft: Int = 0

    @Volatile
    var userCropRight: Int = 0

    @Volatile
    var userCropTop: Int = 0

    @Volatile
    var userCropBottom: Int = 0
}