package com.cstv.app.domain.sync

import kotlinx.coroutines.flow.StateFlow

enum class SyncNamespace(val wireName: String) {
    FAVORITES("favorites"), PLAYBACK("playback"), RATINGS("ratings"), TRACK_PREFERENCES("track-preferences"),
    SERIES_WATCH_STATE("series-watch-state"), CATEGORY_PREFERENCES("category-preferences"), RECENTLY_WATCHED_LIVE("recently-watched-live");
    companion object { fun fromWireName(value: String): SyncNamespace? = entries.firstOrNull { it.wireName == value } }
}

sealed interface CloudSyncStatus { data object Idle : CloudSyncStatus; data object Pending : CloudSyncStatus; data class Failed(val code: String) : CloudSyncStatus; data object Incompatible : CloudSyncStatus }

interface CloudSyncManager {
    val status: StateFlow<CloudSyncStatus>
    suspend fun markDirty(profileId: Int, namespace: SyncNamespace)
    suspend fun synchronizeProfile(profileId: Int)
}
