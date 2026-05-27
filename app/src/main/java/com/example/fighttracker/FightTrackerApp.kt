package com.example.fighttracker

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class FightTrackerApp : Application(), ImageLoaderFactory {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun newImageLoader(): ImageLoader {

        return ImageLoader.Builder(this)

            .components {

                add(ImageDecoderDecoder.Factory())

                add(GifDecoder.Factory())
            }

            .build()
    }
}