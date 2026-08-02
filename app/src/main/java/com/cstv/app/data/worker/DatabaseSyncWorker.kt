package com.cstv.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.usecase.DetectNewEpisodesUseCase
import com.cstv.app.domain.usecase.SyncCacheResult
import com.cstv.app.domain.usecase.SyncCacheUseCase
import com.cstv.app.domain.sync.SyncTrigger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseSyncWorkerEntryPoint {
        fun syncCacheUseCase(): SyncCacheUseCase
        fun detectNewEpisodesUseCase(): DetectNewEpisodesUseCase
    }

    // Priorité "arrière-plan" (voir RequestPriority) : ce sync ne doit jamais
    // faire attendre une requête écran déclenchée par la navigation utilisateur.
    override suspend fun doWork(): Result = withContext(Dispatchers.IO + RequestPriority.background) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DatabaseSyncWorkerEntryPoint::class.java
        )

        val syncResult = entryPoint.syncCacheUseCase().invoke(SyncTrigger.SCHEDULED)

        runPostSyncDetection(syncResult) { entryPoint.detectNewEpisodesUseCase().invoke() }

        when (syncResult) {
            SyncCacheResult.SUCCESS -> Result.success()
            SyncCacheResult.SKIPPED_NO_CREDENTIALS -> Result.success()
            SyncCacheResult.FAILED_RETRYABLE -> Result.retry()
            SyncCacheResult.FAILED_PERMANENT -> Result.failure()
        }
    }

    companion object {
        /**
         * F12 : la détection de nouveaux épisodes est un post-traitement, hors
         * de SyncCacheUseCase. Un échec ici ne doit jamais transformer un sync
         * réussi en retry, ni faire échouer la synchronisation du catalogue.
         * Extrait de [doWork] pour être testable en JVM sans passer par
         * `EntryPointAccessors`/`WorkerParameters` (F12-R2).
         */
        internal suspend fun runPostSyncDetection(syncResult: SyncCacheResult, detect: suspend () -> Unit) {
            if (syncResult != SyncCacheResult.SUCCESS) return
            try {
                detect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                IptvLog.e("NEWEP", "Détection de nouveaux épisodes interrompue", e)
            }
        }
    }
}
