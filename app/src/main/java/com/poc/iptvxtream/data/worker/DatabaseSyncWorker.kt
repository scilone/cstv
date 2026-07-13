package com.poc.iptvxtream.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.data.local.storage.CredentialsManager
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
        fun liveTvRepository(): LiveTvRepository
        fun vodRepository(): VodRepository
        fun seriesRepository(): SeriesRepository
        fun credentialsManager(): CredentialsManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DatabaseSyncWorkerEntryPoint::class.java
            )
            val credentialsManager = entryPoint.credentialsManager()
            val liveTvRepository = entryPoint.liveTvRepository()
            val vodRepository = entryPoint.vodRepository()
            val seriesRepository = entryPoint.seriesRepository()

            val credentials = credentialsManager.getCredentials()
            if (credentials == null) {
                return@withContext Result.success()
            }

            // Sync Live TV categories & streams
            liveTvRepository.getLiveCategories(forceRefresh = true)
            liveTvRepository.getLiveStreams(categoryId = "all", forceRefresh = true)

            // Sync VOD categories & streams
            vodRepository.getVodCategories(forceRefresh = true)
            vodRepository.getVodStreams(categoryId = "all", forceRefresh = true)

            // Sync Series categories & streams
            seriesRepository.getSeriesCategories(forceRefresh = true)
            seriesRepository.getSeriesStreams(categoryId = "all", forceRefresh = true)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
