package com.frodo.fighttracker

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class FightTrackerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CodexRepository.loadFromAssets(this)
    }
    @RequiresApi(Build.VERSION_CODES.P)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}