package com.cstv.app.data.cloudsync

import com.cstv.app.data.local.dao.*
import com.cstv.app.data.local.entity.*
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

/** Room <-> document translation for one namespace. Extracted as an interface so
 *  `CloudSyncManagerImpl` can be tested with a fake instead of a Mockito mock on a
 *  final class (project has no mockito-inline, see AGENTS.md Mockito rules). */
interface SnapshotSerializer {
    suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot
    suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot)
}

/** Explicit namespace boundary: profile ids are routing data and never serialized. */
@Singleton
class RoomSnapshotSerializer @Inject constructor(
    private val favorites: FavoritesDao, private val vod: VodDao, private val ratings: MediaRatingDao,
    private val tracks: TrackPreferenceDao, private val series: SeriesWatchStateDao,
    private val categories: CategoryPreferenceDao, private val live: LiveTvDao, private val gson: Gson,
    private val database: AppDatabase
) : SnapshotSerializer {
    override suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot {
        val values: List<Pair<String, Any>> = when (namespace) {
            SyncNamespace.FAVORITES -> favorites.getAllForProfile(profileId).map { "${it.type}:${it.id}" to it }
            SyncNamespace.PLAYBACK -> vod.getAllPlaybackPositions(profileId).map { it.streamId.toString() to it }
            SyncNamespace.RATINGS -> ratings.getAllForProfile(profileId).map { "${it.mediaType}:${it.mediaId}" to it }
            SyncNamespace.TRACK_PREFERENCES -> tracks.getAllForProfile(profileId).map { "${it.mediaType}:${it.mediaId}" to it }
            SyncNamespace.SERIES_WATCH_STATE -> series.getAllForProfile(profileId).map { it.seriesId.toString() to it }
            SyncNamespace.CATEGORY_PREFERENCES -> categories.getAllForProfile(profileId).map { "${it.type}:${it.categoryId}" to it }
            SyncNamespace.RECENTLY_WATCHED_LIVE -> live.getRecentlyWatched(profileId, Int.MAX_VALUE).map { it.streamId.toString() to it }
        }
        return NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, namespace.wireName, values.associate { (key, value) -> key to gson.toJsonTree(value).withoutProfileId() })
    }

    override suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot) {
        val namespace = SyncNamespace.fromWireName(snapshot.namespace) ?: return
        suspend fun applySnapshot() = when (namespace) {
            SyncNamespace.FAVORITES -> { favorites.deleteAllForProfile(profileId); snapshot.objects.values.forEach { favorites.addFavorite(gson.fromJson(it, FavoriteEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.PLAYBACK -> { vod.deleteAllPlaybackForProfile(profileId); snapshot.objects.values.forEach { vod.savePlaybackPosition(gson.fromJson(it, PlaybackPositionEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.RATINGS -> { ratings.deleteAllForProfile(profileId); snapshot.objects.values.forEach { ratings.upsert(gson.fromJson(it, MediaRatingEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.TRACK_PREFERENCES -> { tracks.deleteAllForProfile(profileId); snapshot.objects.values.forEach { tracks.upsert(gson.fromJson(it, TrackPreferenceEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.SERIES_WATCH_STATE -> { series.deleteAllForProfile(profileId); snapshot.objects.values.forEach { series.upsert(gson.fromJson(it, SeriesWatchStateEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.CATEGORY_PREFERENCES -> { categories.deleteAllForProfile(profileId); snapshot.objects.values.forEach { categories.upsert(gson.fromJson(it, CategoryPreferenceEntity::class.java).copy(profileId = profileId)) } }
            SyncNamespace.RECENTLY_WATCHED_LIVE -> { live.deleteRecentlyWatchedForProfile(profileId); snapshot.objects.values.forEach { live.insertRecentlyWatched(gson.fromJson(it, RecentlyWatchedLiveEntity::class.java).copy(profileId = profileId)) } }
        }
        database.withTransaction { applySnapshot() }
    }

    private fun JsonElement.withoutProfileId(): JsonElement = asJsonObject.deepCopy().apply { remove("profileId") }
}
