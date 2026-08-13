package com.cstv.app.data.cloudsync

import com.cstv.app.data.cloudsync.wire.CategoryPreferenceWire
import com.cstv.app.data.cloudsync.wire.FavoriteWire
import com.cstv.app.data.cloudsync.wire.PlaybackWire
import com.cstv.app.data.cloudsync.wire.RatingWire
import com.cstv.app.data.cloudsync.wire.RecentlyWatchedWire
import com.cstv.app.data.cloudsync.wire.SeriesWatchStateWire
import com.cstv.app.data.cloudsync.wire.TrackPreferenceWire
import com.cstv.app.data.local.dao.*
import com.cstv.app.data.local.entity.*
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

/** T20: Room <-> document translation for one namespace. Extracted as an interface so
 *  `CloudSyncManagerImpl` can be tested with a fake instead of a Mockito mock on a
 *  final class (project has no mockito-inline, see AGENTS.md Mockito rules). */
interface SnapshotSerializer {
    suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot
    suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot)
}

/**
 * T20 (§4.6): produces and applies documents from/to **projections**, not entities -- the wire
 * format is no longer a by-product of the Room schema. Every object key is `"kind:providerId"`
 * except `series-watch-state` (always `kind = "series"`, key stays a bare id, unchanged v1
 * format) and `category-preferences` (`"kind:categoryId"`, unchanged v1 format).
 */
@Singleton
class RoomSnapshotSerializer @Inject constructor(
    private val favorites: FavoritesDao, private val vod: VodDao, private val ratings: MediaRatingDao,
    private val tracks: TrackPreferenceDao, private val series: SeriesWatchStateDao,
    private val categories: CategoryPreferenceDao, private val live: LiveTvDao, private val gson: Gson,
    private val database: AppDatabase, private val mediaRefDao: MediaRefDao, private val categoryRefDao: CategoryRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
) : SnapshotSerializer {
    override suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot {
        // T19-R2: prune Room itself before reading -- see RoomSnapshotSerializer's prior revision
        // for why (rows past the cap must not survive locally, or an older one can re-enter the
        // top N once a more recent one is deleted). Caps stay in force after T20 (rule 14).
        val accountKey = accountKeyProvider.current()
        val objects: Map<String, JsonElement> = when (namespace) {
            SyncNamespace.FAVORITES -> {
                favorites.pruneToMostRecent(profileId, SnapshotLimits.FAVORITES)
                favorites.wireRows(profileId, accountKey).associate { "${it.kind}:${it.providerId}" to gson.toJsonTree(FavoriteWire(it.addedAt)) }
            }
            SyncNamespace.PLAYBACK -> {
                vod.prunePlaybackToMostRecent(profileId, SnapshotLimits.PLAYBACK)
                vod.wireRows(profileId, accountKey).associate {
                    "${it.kind}:${it.providerId}" to gson.toJsonTree(PlaybackWire(it.positionMs, it.durationMs, it.lastAccessedAt))
                }
            }
            SyncNamespace.RATINGS -> ratings.getAllForProfile(profileId, accountKey)
                .associate { "${it.kind}:${it.providerId}" to gson.toJsonTree(RatingWire(it.value)) }
            SyncNamespace.TRACK_PREFERENCES -> tracks.getAllForProfile(profileId, accountKey)
                .associate { "${it.kind}:${it.providerId}" to gson.toJsonTree(TrackPreferenceWire(it.audioLang, it.subtitleLang)) }
            SyncNamespace.SERIES_WATCH_STATE -> series.getAllForProfile(profileId, accountKey)
                .associate { it.providerId.toString() to gson.toJsonTree(SeriesWatchStateWire(it.lastKnownSeason, it.lastKnownEpisode, it.lastNotifiedSeason, it.lastNotifiedEpisode, it.updatedAt)) }
            SyncNamespace.CATEGORY_PREFERENCES -> categories.getAllForProfile(profileId, accountKey)
                .associate { "${it.kind}:${it.categoryId}" to gson.toJsonTree(CategoryPreferenceWire(it.hidden, it.sortOrder)) }
            SyncNamespace.RECENTLY_WATCHED_LIVE -> {
                live.pruneRecentlyWatchedToMostRecent(profileId, SnapshotLimits.RECENTLY_WATCHED_LIVE)
                live.wireRows(profileId, accountKey).associate { "live:${it.providerId}" to gson.toJsonTree(RecentlyWatchedWire(it.watchedAt)) }
            }
        }
        return NamespaceSnapshot(namespace.schemaVersion, namespace.wireName, objects)
    }

    override suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot) {
        val namespace = SyncNamespace.fromWireName(snapshot.namespace) ?: return
        val accountKey = accountKeyProvider.current()
        // An identity is created even for a media absent from the local catalogue: the state is
        // restored and stays invisible until the target appears (T20 "cloud restored before
        // catalogue" behaviour) -- see `MediaRefDao.resolve` / `CategoryRefDao.resolve`.
        suspend fun applySnapshot() = when (namespace) {
            SyncNamespace.FAVORITES -> {
                favorites.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key) ?: return@forEach
                    val wire = gson.fromJson(value, FavoriteWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    favorites.addFavorite(FavoriteEntity(profileId = profileId, mediaUid = mediaUid, addedAt = wire.addedAt))
                }
            }
            SyncNamespace.PLAYBACK -> {
                vod.deleteAllPlaybackForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key) ?: return@forEach
                    val wire = gson.fromJson(value, PlaybackWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    vod.savePlaybackPosition(PlaybackPositionEntity(profileId, mediaUid, wire.positionMs, wire.durationMs, wire.lastAccessedAt))
                }
            }
            SyncNamespace.RATINGS -> {
                ratings.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key) ?: return@forEach
                    val wire = gson.fromJson(value, RatingWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    ratings.upsert(MediaRatingEntity(profileId, mediaUid, wire.value))
                }
            }
            SyncNamespace.TRACK_PREFERENCES -> {
                tracks.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key) ?: return@forEach
                    val wire = gson.fromJson(value, TrackPreferenceWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    tracks.upsert(TrackPreferenceEntity(profileId = profileId, mediaUid = mediaUid, audioLang = wire.audioLang, subtitleLang = wire.subtitleLang))
                }
            }
            SyncNamespace.SERIES_WATCH_STATE -> {
                series.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val providerId = key.toIntOrNull() ?: return@forEach
                    val wire = gson.fromJson(value, SeriesWatchStateWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, "series", providerId)
                    series.upsert(
                        SeriesWatchStateEntity(
                            profileId = profileId, mediaUid = mediaUid, lastKnownSeason = wire.lastKnownSeason,
                            lastKnownEpisode = wire.lastKnownEpisode, lastNotifiedSeason = wire.lastNotifiedSeason,
                            lastNotifiedEpisode = wire.lastNotifiedEpisode, updatedAt = wire.updatedAt
                        )
                    )
                }
            }
            SyncNamespace.CATEGORY_PREFERENCES -> {
                categories.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, categoryId) = parseKindCategoryId(key) ?: return@forEach
                    val wire = gson.fromJson(value, CategoryPreferenceWire::class.java)
                    val catUid = categoryRefDao.resolve(accountKey, kind, categoryId)
                    categories.upsert(CategoryPreferenceEntity(profileId = profileId, catUid = catUid, hidden = wire.hidden, sortOrder = wire.sortOrder))
                }
            }
            SyncNamespace.RECENTLY_WATCHED_LIVE -> {
                live.deleteRecentlyWatchedForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (_, providerId) = parseKindProviderId(key) ?: return@forEach
                    val wire = gson.fromJson(value, RecentlyWatchedWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, "live", providerId)
                    live.insertRecentlyWatched(RecentlyWatchedLiveEntity(profileId = profileId, mediaUid = mediaUid, watchedAt = wire.watchedAt))
                }
            }
        }
        database.withTransaction { applySnapshot() }
    }

    private fun parseKindProviderId(key: String): Pair<String, Int>? {
        val separator = key.indexOf(':')
        if (separator <= 0) return null
        val providerId = key.substring(separator + 1).toIntOrNull() ?: return null
        return key.substring(0, separator) to providerId
    }

    private fun parseKindCategoryId(key: String): Pair<String, String>? {
        val separator = key.indexOf(':')
        if (separator <= 0 || separator == key.lastIndex) return null
        return key.substring(0, separator) to key.substring(separator + 1)
    }
}
