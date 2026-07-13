package com.poc.iptvxtream.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.*
import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.data.worker.DatabaseSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                tvSorting = settingsManager.getTvCategorySorting(),
                vodSorting = settingsManager.getVodCategorySorting(),
                seriesSorting = settingsManager.getSeriesCategorySorting(),
                syncFrequency = settingsManager.getSyncFrequency()
            )
        }
    }

    fun updateTvSorting(sorting: CategorySorting) {
        settingsManager.setTvCategorySorting(sorting)
        _state.update { it.copy(tvSorting = sorting) }
    }

    fun updateVodSorting(sorting: CategorySorting) {
        settingsManager.setVodCategorySorting(sorting)
        _state.update { it.copy(vodSorting = sorting) }
    }

    fun updateSeriesSorting(sorting: CategorySorting) {
        settingsManager.setSeriesCategorySorting(sorting)
        _state.update { it.copy(seriesSorting = sorting) }
    }

    fun updateSyncFrequency(frequency: SyncFrequency) {
        settingsManager.setSyncFrequency(frequency)
        _state.update { it.copy(syncFrequency = frequency) }
        scheduleBackgroundSync(frequency)
    }

    private fun scheduleBackgroundSync(frequency: SyncFrequency) {
        val workManager = try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            // WorkManager is not initialized in standard unit tests, return gracefully
            return
        }
        val workName = "database_sync_work"

        if (frequency == SyncFrequency.DISABLED) {
            workManager.cancelUniqueWork(workName)
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

        val syncRequest = PeriodicWorkRequestBuilder<DatabaseSyncWorker>(
            repeatInterval.first,
            repeatInterval.second
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }
}
