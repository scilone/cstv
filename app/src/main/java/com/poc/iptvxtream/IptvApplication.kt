package com.poc.iptvxtream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvApplication : Application(), ImageLoaderFactory {

    /**
     * ImageLoader applicatif utilisé par tous les AsyncImage (audit #12).
     * Milliers de posters 2:3 et logos de chaînes :
     * - cache disque dédié de 250 Mo (le défaut Coil, 2 % du stockage, est
     *   trop juste sur Android TV bas de gamme) ;
     * - cache mémoire à 25 % de la RAM applicative ;
     * - respectCacheHeaders(false) : les panels Xtream renvoient des headers
     *   de cache incohérents alors que les posters sont immuables par URL ;
     * - crossfade pour lisser l'apparition dans les grilles.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
