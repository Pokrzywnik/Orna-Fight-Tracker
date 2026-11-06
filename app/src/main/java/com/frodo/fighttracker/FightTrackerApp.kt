package com.frodo.fighttracker

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.util.Log
import java.util.concurrent.Executors

class FightTrackerApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Start immediately, without making Activity creation wait for models or JSON.
        val preload = Executors.newSingleThreadExecutor()
        preload.execute {
            try {
                ItemAssessmentPipeline.ItemAssessmentManager.initialize(this)
            } catch (e: Exception) {
                Log.e("FightTracker", "Item assessment preload failed", e)
            }
        }
        preload.shutdown()
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
