package com.cstv.app.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.domain.sync.CloudSyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Recovers pending CSTV namespace writes after the app process has stopped. */
class CloudSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CloudSyncWorkerEntryPoint {
        fun profileSyncStates(): ProfileSyncStateDao
        fun cloudSyncManager(): CloudSyncManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, CloudSyncWorkerEntryPoint::class.java)
        entryPoint.profileSyncStates().getPending().map { it.profileId }.distinct().forEach {
            entryPoint.cloudSyncManager().synchronizeProfile(it)
        }
        return if (entryPoint.profileSyncStates().getPending().isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "cstv_cloud_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
