package com.cstv.app

import android.app.Application
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.update.UpdateApkStore
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.util.DiagnosticManager
import com.cstv.app.data.util.FrameJankWatchdog
import com.cstv.app.data.util.MainThreadWatchdog
import com.cstv.app.data.worker.DatabaseSyncWorker
import com.cstv.app.data.worker.CatalogNormalizationWorker
import com.cstv.app.data.worker.SyncScheduling
import com.cstv.app.di.IptvLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    /**
     * `Lazy` et non injection directe : construire la base sur le thread
     * principal, dans `onCreate`, déplacerait le coût au lieu de le supprimer.
     */
    @Inject
    lateinit var database: dagger.Lazy<com.cstv.app.data.local.db.AppDatabase>

    @Inject
    lateinit var updateApkStore: UpdateApkStore

    @Inject
    lateinit var databaseMaintenanceRunner: com.cstv.app.data.local.db.DatabaseMaintenanceRunner

    @Inject
    lateinit var externalMetadataBackfillSeeder: com.cstv.app.data.worker.ExternalMetadataBackfillSeeder

    override fun onCreate() {
        super.onCreate()
        diagnosticManager.initialize()
        if (settingsManager.getDebugModeEnabled()) {
            diagnosticManager.startLogging()
        }
        // Diagnostic lenteurs/freezes (retour utilisateur du 2026-08-18) : coût
        // négligeable, démarré inconditionnellement pour capturer un incident
        // même si le mode debug n'a pas été activé avant qu'il survienne.
        MainThreadWatchdog.start()
        FrameJankWatchdog.start()
        scheduleDefaultBackgroundSync()
        warmUpDatabase()
        scheduleCatalogNormalization()
        resumeExternalMetadataHydration()
        runExternalMetadataBackfill()
        purgeObsoleteAppUpdateApks()
        runDatabaseMaintenance()
    }

    /**
     * T20-6 : rejoue le `VACUUM` demandé par la migration 27→28, hors thread
     * principal. Sans effet tant qu'aucune demande n'est en attente dans
     * `db_maintenance` ; une tentative échouée ou refusée (espace insuffisant)
     * laisse la base intacte et sera rejouée au prochain démarrage.
     */
    private fun runDatabaseMaintenance() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { databaseMaintenanceRunner.run() }
                .onFailure { IptvLog.e("MAINTENANCE", "Compactage différé impossible", it) }
        }
    }

    /**
     * Purge `cacheDir/app_updates` (F35, §4.7/RG13) : `.part` interrompus, APK
     * illisibles/étrangers, et APK de version ≤ celle installée. Toujours hors
     * thread principal, ne bloque jamais l'affichage du splash.
     */
    private fun purgeObsoleteAppUpdateApks() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { updateApkStore.purge(BuildConfig.VERSION_CODE) }
                .onFailure { IptvLog.e("APPUPDATE", "Purge du cache de mise à jour impossible", it) }
        }
    }

    /**
     * Ouvre la base hors du chemin critique.
     *
     * Le premier accès à Room paie l'ouverture du fichier SQLite, la
     * vérification d'empreinte du schéma (vingt tables), l'installation des
     * déclencheurs d'invalidation et, le cas échéant, la reprise d'un journal
     * laissé par une session interrompue. Payé par le premier écran de
     * catalogue ouvert, ce coût est directement visible ; payé ici, il se
     * déroule pendant que l'Accueil s'affiche depuis ses caches.
     *
     * La durée est tracée : si elle est négligeable, la lenteur observée sur le
     * premier catalogue vient d'ailleurs, et la trace le dira au lieu de
     * laisser croire que le sujet est réglé.
     */
    private fun warmUpDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            val startedAt = System.nanoTime()
            runCatching { database.get().openHelper.writableDatabase }
                .onSuccess {
                    IptvLog.d(
                        "PERF",
                        "Base ouverte au démarrage en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                    )
                }
                .onFailure {
                    IptvLog.d("PERF", "Ouverture de la base au démarrage impossible : ${it.message}")
                }
        }
    }

    /** T21: local-only post-migration work; it is a no-op after the empty-key backlog is drained. */
    private fun scheduleCatalogNormalization() {
        try {
            CatalogNormalizationWorker.enqueue(this)
        } catch (exception: Exception) {
            // WorkManager is intentionally absent from regular JVM tests.
            IptvLog.e("T21", "Planification du rattrapage des titres impossible", exception)
        }
    }

    /**
     * F45 §8.11 : reprise après process death. La file `external_hydration_queue` est durable, mais
     * rien ne la redrainerait automatiquement si l'appli redémarre sans qu'aucune fiche ne soit
     * ouverte entre-temps (ex. relance directe sur l'Accueil) — `KEEP` rend cet appel un no-op si une
     * hydratation est déjà en file/active.
     */
    private fun resumeExternalMetadataHydration() {
        try {
            com.cstv.app.data.worker.ExternalMetadataHydrationWorker.enqueue(this)
            scheduleExternalMetadataHydrationHeartbeat()
        } catch (exception: Exception) {
            IptvLog.e("F45", "Reprise de l'hydratation externe impossible", exception)
        }
    }

    /**
     * Retour utilisateur 2026-08-21 : sans ce battement, la convergence du backfill (§8.11) ne
     * progressait que par rafales liées aux ouvertures d'appli — `resumeExternalMetadataHydration()`
     * n'enqueue qu'un seul passage au démarrage, et `ExternalMetadataHydrationWorker` ne se
     * reprogramme lui-même que jusqu'à l'échéance du prochain item en file (potentiellement des
     * heures de silence si tout est en backoff). 15 minutes = intervalle minimal `WorkManager` pour
     * du travail périodique ; `KEEP` ne redémarre pas le cycle déjà planifié à chaque lancement.
     */
    private fun scheduleExternalMetadataHydrationHeartbeat() {
        val workManager = try {
            WorkManager.getInstance(this)
        } catch (e: Exception) {
            return
        }
        val request = PeriodicWorkRequestBuilder<com.cstv.app.data.worker.ExternalMetadataHydrationHeartbeatWorker>(
            15, TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "external_metadata_hydration_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * F45 §8.11 : couvre première installation et installation existante par le même mécanisme.
     * Hors thread principal, jamais sur le chemin critique du démarrage ; ré-exécutable sans coût
     * une fois le catalogue convergé (voir `ExternalMetadataBackfillSeeder`).
     */
    private fun runExternalMetadataBackfill() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { externalMetadataBackfillSeeder.seed() }
                .onFailure { IptvLog.e("F45", "Backfill des métadonnées externes impossible", it) }
        }
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
