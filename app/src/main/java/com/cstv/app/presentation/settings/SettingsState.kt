package com.cstv.app.presentation.settings

import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.domain.model.ExternalMetadataCoverage
import com.cstv.app.domain.model.SubtitleStyle
import com.cstv.app.domain.sync.CloudSyncStatus

data class SettingsState(
    val syncFrequency: SyncFrequency = SyncFrequency.DAILY,
    val isSyncingNow: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val debugModeEnabled: Boolean = false,
    val liveQualityModeDefault: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val isUploadingLogs: Boolean = false,
    val uploadedLogsUrl: String? = null,
    val uploadLogsError: String? = null,
    val cstvEmail: String? = null,
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.Idle,
    /** B27 : identifiant Xtream affiché dans la carte « Comptes » des Paramètres. */
    val iptvUsername: String? = null,
    /** F46 : `null` uniquement avant la première émission Room — un catalogue vide est représenté par `total == 0`. */
    val externalMetadataCoverage: ExternalMetadataCoverage? = null,
)
