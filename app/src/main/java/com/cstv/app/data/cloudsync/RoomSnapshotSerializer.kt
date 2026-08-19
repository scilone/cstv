package com.cstv.app.data.cloudsync

import com.cstv.app.data.cloudsync.wire.CategoryPreferenceWire
import com.cstv.app.data.cloudsync.wire.FavoriteWire
import com.cstv.app.data.cloudsync.wire.PlaybackWire
import com.cstv.app.data.cloudsync.wire.RatingWire
import com.cstv.app.data.cloudsync.wire.RecentlyWatchedWire
import com.cstv.app.data.cloudsync.wire.SeriesWatchStateWire
import com.cstv.app.data.cloudsync.wire.TrackPreferenceWire
import com.cstv.app.data.cloudsync.wire.ProfilePreferencesWire
import com.cstv.app.data.local.dao.*
import com.cstv.app.data.local.entity.*
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.SettingsManager
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
    private val accountKeyProvider: CurrentAccountKeyProvider, private val settingsManager: SettingsManager,
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
                .associate { "${it.kind}:${it.providerId}" to gson.toJsonTree(RatingWire(it.kind, it.providerId, it.value)) }
            SyncNamespace.TRACK_PREFERENCES -> tracks.getAllForProfile(profileId, accountKey)
                .associate { "${it.kind}:${it.providerId}" to gson.toJsonTree(TrackPreferenceWire(it.kind, it.providerId, it.audioLang, it.subtitleLang)) }
            SyncNamespace.SERIES_WATCH_STATE -> series.getAllForProfile(profileId, accountKey)
                .associate { it.providerId.toString() to gson.toJsonTree(SeriesWatchStateWire(it.providerId, it.lastKnownSeason, it.lastKnownEpisode, it.lastNotifiedSeason, it.lastNotifiedEpisode, it.updatedAt)) }
            SyncNamespace.CATEGORY_PREFERENCES -> categories.getAllForProfile(profileId, accountKey)
                .associate { "${it.kind}:${it.categoryId}" to gson.toJsonTree(CategoryPreferenceWire(it.categoryId, it.kind, it.hidden, it.sortOrder)) }
            SyncNamespace.PROFILE_PREFERENCES -> mapOf(
                "player" to gson.toJsonTree(ProfilePreferencesWire(settingsManager.getAutoPlayNextEpisode(profileId)))
            )
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
                    val (kind, providerId) = parseKindProviderId(key, FAVORITE_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, FavoriteWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    favorites.addFavorite(FavoriteEntity(profileId = profileId, mediaUid = mediaUid, addedAt = wire.addedAt))
                }
            }
            SyncNamespace.PLAYBACK -> {
                vod.deleteAllPlaybackForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key, PLAYBACK_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, PlaybackWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    vod.savePlaybackPosition(PlaybackPositionEntity(profileId, mediaUid, wire.positionMs, wire.durationMs, wire.lastAccessedAt))
                }
            }
            SyncNamespace.RATINGS -> {
                ratings.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key, RATING_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, RatingWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    ratings.upsert(MediaRatingEntity(profileId, mediaUid, wire.value))
                }
            }
            SyncNamespace.TRACK_PREFERENCES -> {
                tracks.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    val (kind, providerId) = parseKindProviderId(key, TRACK_PREFERENCE_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, TrackPreferenceWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    tracks.upsert(TrackPreferenceEntity(profileId = profileId, mediaUid = mediaUid, audioLang = wire.audioLang, subtitleLang = wire.subtitleLang))
                }
            }
            SyncNamespace.SERIES_WATCH_STATE -> {
                series.deleteAllForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    // No kind travels in this namespace's key (always "series") or body -- only the
                    // provider id needs validating, which `toIntOrNull()` already does.
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
                    val (kind, categoryId) = parseKindCategoryId(key, CATEGORY_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, CategoryPreferenceWire::class.java)
                    val catUid = categoryRefDao.resolve(accountKey, kind, categoryId)
                    categories.upsert(CategoryPreferenceEntity(profileId = profileId, catUid = catUid, hidden = wire.hidden, sortOrder = wire.sortOrder))
                }
            }
            SyncNamespace.PROFILE_PREFERENCES -> {
                snapshot.objects["player"]?.let { value ->
                    val enabled = runCatching {
                        value.asJsonObject.get("autoPlayNextEpisode")?.asBoolean
                    }.getOrNull() ?: true
                    settingsManager.setAutoPlayNextEpisode(profileId, enabled)
                }
            }
            SyncNamespace.RECENTLY_WATCHED_LIVE -> {
                live.deleteRecentlyWatchedForProfile(profileId)
                snapshot.objects.forEach { (key, value) ->
                    // T20-R5: the parsed kind must actually be used and strictly be "live" -- this
                    // namespace's identity is always resolved as live, so a `movie:42` key silently
                    // became a `live:42` identity when the parsed kind was discarded.
                    val (kind, providerId) = parseKindProviderId(key, RECENTLY_WATCHED_LIVE_KINDS) ?: return@forEach
                    val wire = gson.fromJson(value, RecentlyWatchedWire::class.java)
                    val mediaUid = mediaRefDao.resolve(accountKey, kind, providerId)
                    live.insertRecentlyWatched(RecentlyWatchedLiveEntity(profileId = profileId, mediaUid = mediaUid, watchedAt = wire.watchedAt))
                }
            }
        }
        database.withTransaction { applySnapshot() }
    }

    /**
     * T20-R5: a cloud reference of unknown or invalid type must never be applied (T20 error rule).
     * `parseKindProviderId`/`parseKindCategoryId` used to accept any non-empty string as `kind`,
     * so a corrupted or hostile snapshot could create identities of a kind that namespace never
     * produces (e.g. a `live` entry in `playback`), or -- worse, for `recently-watched-live` --
     * write under a fixed `"live"` kind while completely ignoring the kind actually carried by the
     * key. [allowedKinds] is the domain-accurate set for the namespace calling this.
     *
     * `internal`, not `private`: `RoomSnapshotSerializerTest` exercises this parsing/validation
     * directly (see the T20-R5 tests) rather than through `apply()`'s `database.withTransaction { }`
     * -- a real Room transaction dispatch has no reliable way to be driven against a bare Mockito
     * `AppDatabase` mock under `runTest`'s virtual-time dispatcher, and no other transactional DAO
     * method in this codebase is unit-tested that way either (see `ProfileRepositoryImplTest`'s
     * `database = null`). Testing the pure decision this directly targets is what actually matters:
     * every call site immediately does `?: return@forEach` on a `null` result, before any DAO write.
     */
    internal fun parseKindProviderId(key: String, allowedKinds: Set<String>): Pair<String, Int>? {
        val separator = key.indexOf(':')
        if (separator <= 0) return null
        val kind = key.substring(0, separator)
        if (kind !in allowedKinds) return null
        val providerId = key.substring(separator + 1).toIntOrNull() ?: return null
        return kind to providerId
    }

    internal fun parseKindCategoryId(key: String, allowedKinds: Set<String>): Pair<String, String>? {
        val separator = key.indexOf(':')
        if (separator <= 0 || separator == key.lastIndex) return null
        val kind = key.substring(0, separator)
        if (kind !in allowedKinds) return null
        return kind to key.substring(separator + 1)
    }

    internal companion object {
        val FAVORITE_KINDS = setOf("live", "movie", "series")
        val PLAYBACK_KINDS = setOf("movie", "episode")
        val RATING_KINDS = setOf("movie", "series")
        val TRACK_PREFERENCE_KINDS = setOf("movie", "series")
        val RECENTLY_WATCHED_LIVE_KINDS = setOf("live")
        val CATEGORY_KINDS = setOf("live", "vod", "series")
    }
}
