package com.cstv.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.util.DiagnosticManager
import com.cstv.app.data.worker.DatabaseSyncWorker
import com.cstv.app.data.worker.SyncScheduling
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.model.SubtitleBackground
import com.cstv.app.domain.model.SubtitleTextColor
import com.cstv.app.domain.model.SubtitleTextSize
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
    private val diagnosticManager: DiagnosticManager,
    private val cstvAuthRepository: CstvAuthRepository,
    private val cloudSyncManager: CloudSyncManager,
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
        viewModelScope.launch {
            cloudSyncManager.status.collect { status -> _state.update { it.copy(cloudSyncStatus = status) } }
        }
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                syncFrequency = settingsManager.getSyncFrequency(),
                subtitleStyle = settingsManager.getSubtitleStyle(),
                debugModeEnabled = settingsManager.getDebugModeEnabled(),
                cstvEmail = cstvAuthRepository.storedEmail()
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

        // Heure fixe (6h du matin par défaut) plutôt que relative au moment de
        // l'activation : les exécutions suivantes du PeriodicWorkRequest héritent
        // de ce point de départ.
        val initialDelayMillis = SyncScheduling.initialDelayMillis(java.util.Calendar.getInstance())

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

    fun updateDebugModeEnabled(enabled: Boolean) {
        settingsManager.setDebugModeEnabled(enabled)
        _state.update { it.copy(debugModeEnabled = enabled) }
        if (enabled) {
            diagnosticManager.startLogging()
        } else {
            diagnosticManager.stopLogging()
        }
    }

    fun uploadDiagnosticLogs() {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingLogs = true, uploadedLogsUrl = null, uploadLogsError = null) }
            try {
                val url = diagnosticManager.uploadLogs()
                _state.update { it.copy(isUploadingLogs = false, uploadedLogsUrl = url) }
            } catch (e: Exception) {
                _state.update { it.copy(isUploadingLogs = false, uploadLogsError = e.message ?: "Une erreur est survenue lors du téléversement") }
            }
        }
    }

    fun clearUploadStatus() {
        _state.update { it.copy(uploadedLogsUrl = null, uploadLogsError = null, isUploadingLogs = false) }
    }

    /** Keeps Xtream credentials and all local profile data intact (F33 §5.7). */
    fun signOutCstv() = cstvAuthRepository.signOut()
}
