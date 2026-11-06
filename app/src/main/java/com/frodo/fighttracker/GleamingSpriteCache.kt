package com.frodo.fighttracker

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.io.File

/** One shared cache for Gleaming sprites, independent of other app images. */
object GleamingSpriteCache {
    @Volatile
    private var loader: ImageLoader? = null

    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: createImageLoader(context.applicationContext).also { loader = it }
    }

    private fun createImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(8 * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "gleaming_sprites"))
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            // These public sprite URLs are stable. Keep downloaded images for
            // reuse, including offline, even if the server discourages caching.
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
}
