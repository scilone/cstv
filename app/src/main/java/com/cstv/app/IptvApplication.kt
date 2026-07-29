package com.cstv.app

import android.app.Application
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.util.DiagnosticManager
import com.cstv.app.data.worker.DatabaseSyncWorker
import com.cstv.app.data.worker.SyncScheduling
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class IptvApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var diagnosticManager: DiagnosticManager

    /**
     * Injecté ici uniquement pour forcer la création du singleton au démarrage :
     * c'est sa construction qui arme la veille de reconnexion (déclencheur
     * RECONNECT), laquelle doit survivre à toute navigation.
     */
    @Inject
    lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager

    override fun onCreate() {
        super.onCreate()
        diagnosticManager.initialize()
        if (settingsManager.getDebugModeEnabled()) {
            diagnosticManager.startLogging()
        }
        scheduleDefaultBackgroundSync()
    }

    /**
     * Cache d'images explicite.
     *
     * `respectCacheHeaders(false)` est le réglage décisif du mode hors-ligne :
     * les panels Xtream servent les jaquettes sans `Cache-Control` ou avec un
     * âge très court, ce qui pousse Coil à revalider en réseau — hors ligne,
     * l'image en cache serait alors ignorée au profit du placeholder.
     *
     * 256 Mo est un plafond prévisible, là où le défaut (2 % du disque) varie
     * d'un appareil à l'autre. Le cache reste dans `cacheDir` : le système peut
     * le purger sous pression disque, sans autre conséquence qu'un retour au
     * visuel de remplacement.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    private fun scheduleDefaultBackgroundSync() {
        val frequency = try {
            settingsManager.getSyncFrequency()
        } catch (e: Exception) {
            SyncFrequency.DAILY
        }

        if (frequency == SyncFrequency.DISABLED) {
            return
        }

        val workManager = try {
            WorkManager.getInstance(this)
        } catch (e: Exception) {
            return
        }

        val repeatInterval = when (frequency) {
            SyncFrequency.DAILY -> 24L to TimeUnit.HOURS
            SyncFrequency.WEEKLY -> 7L to TimeUnit.DAYS
            SyncFrequency.MONTHLY -> 30L to TimeUnit.DAYS
            SyncFrequency.DISABLED -> return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val initialDelayMillis = SyncScheduling.initialDelayMillis(Calendar.getInstance())

        val syncRequest = PeriodicWorkRequestBuilder<DatabaseSyncWorker>(
            repeatInterval.first,
            repeatInterval.second
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.MINUTES
            )
            .build()

        // ExistingPeriodicWorkPolicy.KEEP ensures we don't reset the current work timer
        // if it is already scheduled (retaining its execution schedule), but schedules it
        // on first app launch or if the database work records are missing.
        workManager.enqueueUniquePeriodicWork(
            "database_sync_work",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
