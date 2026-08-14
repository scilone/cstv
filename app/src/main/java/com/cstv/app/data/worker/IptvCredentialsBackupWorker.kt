package com.cstv.app.data.worker

import android.content.Context
import androidx.work.*
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

interface IptvCredentialsBackupScheduler {
    fun enqueue()
}

class WorkManagerIptvCredentialsBackupScheduler @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : IptvCredentialsBackupScheduler {
    override fun enqueue() {
        IptvCredentialsBackupWorker.enqueue(context)
    }
}

internal fun iptvBackupWorkerResult(outcome: com.cstv.app.domain.model.IptvBackupOutcome): ListenableWorker.Result =
    if (outcome == com.cstv.app.domain.model.IptvBackupOutcome.Deferred) ListenableWorker.Result.retry() else ListenableWorker.Result.success()

class IptvCredentialsBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint @InstallIn(SingletonComponent::class) interface BackupWorkerEntryPoint { fun repository(): IptvCredentialsBackupRepository }
    override suspend fun doWork(): Result = iptvBackupWorkerResult(EntryPointAccessors.fromApplication(applicationContext, BackupWorkerEntryPoint::class.java).repository().drainPending())
    companion object { private const val NAME = "iptv_credentials_backup"; fun enqueue(context: Context) { val request = OneTimeWorkRequestBuilder<IptvCredentialsBackupWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES).build(); WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request) } }
}
