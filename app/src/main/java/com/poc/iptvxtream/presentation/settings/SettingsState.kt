package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.domain.model.SubtitleStyle

data class SettingsState(
    val syncFrequency: SyncFrequency = SyncFrequency.DISABLED,
    val isSyncingNow: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle()
)
