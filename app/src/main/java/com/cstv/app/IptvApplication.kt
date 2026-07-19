package com.cstv.app

import android.app.Application
import androidx.work.*
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.worker.DatabaseSyncWorker
import com.cstv.app.data.worker.SyncScheduling
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class IptvApplication : Application() {

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        scheduleDefaultBackgroundSync()
    }

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
