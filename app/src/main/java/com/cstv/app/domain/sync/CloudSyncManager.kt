package com.cstv.app.domain.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * [schemaVersion] : T20 versions the format **per namespace**, not globally (§4.6). Only
 * `favorites`, `playback` and `recently-watched-live` move to 2 -- their documents are produced
 * from `media_refs`-joined projections and key playback by `kind:providerId` instead of a bare
 * `streamId`, which used to collide between a movie and an episode sharing the same numeric id.
 * The other four namespaces keep their existing v1 shape unchanged.
 */
enum class SyncNamespace(val wireName: String, val schemaVersion: Int) {
    FAVORITES("favorites", 2), PLAYBACK("playback", 2), RATINGS("ratings", 1), TRACK_PREFERENCES("track-preferences", 1),
    SERIES_WATCH_STATE("series-watch-state", 1), CATEGORY_PREFERENCES("category-preferences", 1), RECENTLY_WATCHED_LIVE("recently-watched-live", 2), PROFILE_PREFERENCES("profile-preferences", 1);
    companion object { fun fromWireName(value: String): SyncNamespace? = entries.firstOrNull { it.wireName == value } }
}

sealed interface CloudSyncStatus { data object Idle : CloudSyncStatus; data object Pending : CloudSyncStatus; data class Failed(val code: String) : CloudSyncStatus; data object Incompatible : CloudSyncStatus }

interface CloudSyncManager {
    val status: StateFlow<CloudSyncStatus>
    suspend fun markDirty(profileId: Int, namespace: SyncNamespace)
    suspend fun synchronizeProfile(profileId: Int)
}
