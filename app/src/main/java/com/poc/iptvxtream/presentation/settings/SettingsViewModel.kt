package com.poc.iptvxtream.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.data.worker.DatabaseSyncWorker
import com.poc.iptvxtream.domain.model.SubtitleBackground
import com.poc.iptvxtream.domain.model.SubtitleTextColor
import com.poc.iptvxtream.domain.model.SubtitleTextSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    companion object {
        private const val PERIODIC_WORK_NAME = "database_sync_work"
        private const val ONE_TIME_WORK_NAME = "database_sync_work_now"
    }

    init {
        loadSettings()
        observeForceSyncStatus()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                tvSorting = settingsManager.getTvCategorySorting(),
                vodSorting = settingsManager.getVodCategorySorting(),
                seriesSorting = settingsManager.getSeriesCategorySorting(),
                syncFrequency = settingsManager.getSyncFrequency(),
                subtitleStyle = settingsManager.getSubtitleStyle()
            )
        }
    }

    fun updateSubtitleSize(size: SubtitleTextSize) {
        val style = _state.value.subtitleStyle.copy(size = size)
        settingsManager.setSubtitleStyle(style)
        _state.update { it.copy(subtitleStyle = style) }
    }

    fun updateSubtitleColor(color: SubtitleTextColor) {
        val style = _state.value.subtitleStyle.copy(textColor = color)
        settingsManager.setSubtitleStyle(style)
        _state.update { it.copy(subtitleStyle = style) }
    }

    fun updateSubtitleBackground(background: SubtitleBackground) {
        val style = _state.value.subtitleStyle.copy(background = background)
        settingsManager.setSubtitleStyle(style)
        _state.update { it.copy(subtitleStyle = style) }
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
        val workManager = workManagerOrNull() ?: return

        if (frequency == SyncFrequency.DISABLED) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
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
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun forceSyncNow() {
        val workManager = workManagerOrNull() ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DatabaseSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun observeForceSyncStatus() {
        val workManager = workManagerOrNull() ?: return
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK_NAME).collect { workInfos ->
                val isRunning = workInfos.any { !it.state.isFinished }
                _state.update { it.copy(isSyncingNow = isRunning) }
            }
        }
    }

    private fun workManagerOrNull(): WorkManager? {
        return try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            // WorkManager is not initialized in standard unit tests, return gracefully
            null
        }
    }
}
