package com.cstv.app.presentation.settings

import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.domain.model.SubtitleStyle

data class SettingsState(
    val syncFrequency: SyncFrequency = SyncFrequency.DAILY,
    val isSyncingNow: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val debugModeEnabled: Boolean = false,
    val isUploadingLogs: Boolean = false,
    val uploadedLogsUrl: String? = null,
    val uploadLogsError: String? = null
)
