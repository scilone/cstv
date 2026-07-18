package com.cstv.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.domain.usecase.SyncCacheResult
import com.cstv.app.domain.usecase.SyncCacheUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    }

    // Priorité "arrière-plan" (voir RequestPriority) : ce sync ne doit jamais
    // faire attendre une requête écran déclenchée par la navigation utilisateur.
    override suspend fun doWork(): Result = withContext(Dispatchers.IO + RequestPriority.background) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DatabaseSyncWorkerEntryPoint::class.java
        )

        when (entryPoint.syncCacheUseCase().invoke()) {
            SyncCacheResult.SUCCESS -> Result.success()
            SyncCacheResult.SKIPPED_NO_CREDENTIALS -> Result.success()
            SyncCacheResult.FAILED -> Result.retry()
        }
    }
}
