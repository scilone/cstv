package com.poc.iptvxtream.data.download

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

/**
 * Détenteur unique (process-wide) des objets media3 de téléchargement hors-ligne.
 *
 * media3 instancie `DownloadService` via le framework Android (pas Hilt) : le
 * service et les providers Hilt doivent partager le **même** `DownloadManager`
 * et le **même** `Cache`. D'où ce holder statique thread-safe, initialisé
 * paresseusement avec l'`applicationContext`.
 *
 * Stockage app-privé (`getExternalFilesDir`/interne) → pas de permission
 * stockage supplémentaire, respect du scoped storage.
 */
object OfflineDownloadUtil {

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "iptv_downloads"
    private const val DOWNLOAD_CONTENT_DIRECTORY = "iptv_downloads"

    private var databaseProvider: DatabaseProvider? = null
    private var downloadCache: Cache? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var downloadManager: DownloadManager? = null
    private var notificationHelper: DownloadNotificationHelper? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    fun getHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return httpDataSourceFactory ?: DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .also { httpDataSourceFactory = it }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        return databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext)
            .also { databaseProvider = it }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    fun getDownloadCache(context: Context): Cache {
        return downloadCache ?: run {
            val app = context.applicationContext
            val base = app.getExternalFilesDir(null) ?: app.filesDir
            val dir = File(base, DOWNLOAD_CONTENT_DIRECTORY)
            // NoOpCacheEvictor : jamais de purge automatique (décision produit —
            // suppression manuelle uniquement).
            SimpleCache(dir, NoOpCacheEvictor(), getDatabaseProvider(app)).also { downloadCache = it }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: run {
            val app = context.applicationContext
            DownloadManager(
                app,
                getDatabaseProvider(app),
                getDownloadCache(app),
                getHttpDataSourceFactory(),
                Executors.newFixedThreadPool(2)
            ).apply {
                // Un seul téléchargement à la fois : la plupart des panels Xtream
                // limitent les connexions concurrentes par compte (UserInfo.maxConnections).
                maxParallelDownloads = 1
            }.also { downloadManager = it }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    fun getDownloadNotificationHelper(context: Context): DownloadNotificationHelper {
        return notificationHelper ?: DownloadNotificationHelper(
            context.applicationContext,
            DOWNLOAD_NOTIFICATION_CHANNEL_ID
        ).also { notificationHelper = it }
    }

    /**
     * Factory de source de données pour la **lecture** : lit d'abord le cache
     * (contenu téléchargé), retombe sur le réseau sinon, sans jamais écrire
     * dans le cache (l'écriture est réservée au `DownloadManager`). Permet de
     * lire un média téléchargé hors-ligne de façon transparente.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized
    fun getReadOnlyCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream: DataSource.Factory = getHttpDataSourceFactory()
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
