package com.cstv.app.data.cloudsync

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.CategoryPreferenceRow
import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.dao.FavoriteWireRow
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.MediaRatingRow
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.PlaybackWireRow
import com.cstv.app.data.local.dao.RecentlyWatchedWireRow
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.SeriesWatchStateRow
import com.cstv.app.data.local.dao.TrackPreferenceDao
import com.cstv.app.data.local.dao.TrackPreferenceRow
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * T19-R2/T19-R4/T20-5: exercises `RoomSnapshotSerializer.snapshot()` against mocked DAOs -- the
 * real production wiring, not just pure functions. Proves the per-item cap is enforced *in Room*
 * (the DAO prune query is actually invoked) and that the wire format is the lighter, versioned
 * `kind:providerId` shape (T20 §4.6), not a serialized entity.
 */
class RoomSnapshotSerializerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private val profileId = 7
    private val accountKey = "account-key"

    @Mock private lateinit var favorites: FavoritesDao
    @Mock private lateinit var vod: VodDao
    @Mock private lateinit var ratings: MediaRatingDao
    @Mock private lateinit var tracks: TrackPreferenceDao
    @Mock private lateinit var series: SeriesWatchStateDao
    @Mock private lateinit var categories: CategoryPreferenceDao
    @Mock private lateinit var live: LiveTvDao
    @Mock private lateinit var database: AppDatabase
    @Mock private lateinit var mediaRefDao: MediaRefDao
    @Mock private lateinit var categoryRefDao: CategoryRefDao
    @Mock private lateinit var accountKeyProvider: CurrentAccountKeyProvider

    private lateinit var serializer: RoomSnapshotSerializer

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(accountKeyProvider.current()).thenReturn(accountKey)
        serializer = RoomSnapshotSerializer(
            favorites, vod, ratings, tracks, series, categories, live, Gson(), database,
            mediaRefDao, categoryRefDao, accountKeyProvider,
        )
    }

    @Test
    fun `favorites snapshot prunes Room to the real cap, keys by kind colon providerId, versioned v2`() = runTest {
        whenever(favorites.wireRows(profileId, accountKey)).thenReturn(
            listOf(FavoriteWireRow(providerId = 1, kind = "movie", addedAt = 10L)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.FAVORITES)

        verify(favorites).pruneToMostRecent(profileId, SnapshotLimits.FAVORITES)
        assertEquals(2, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("movie:1").asJsonObject
        assertEquals(10L, item.get("addedAt").asLong)
        // T20: no catalogue metadata (name/cover/categoryId) and no profileId ever leave Room.
        assertEquals(setOf("addedAt"), item.keySet())
    }

    @Test
    fun `playback snapshot prunes Room to the real cap, keys disambiguate movie from episode`() = runTest {
        whenever(vod.wireRows(profileId, accountKey)).thenReturn(
            listOf(PlaybackWireRow(providerId = 1, kind = "episode", positionMs = 1000, durationMs = 2000, lastAccessedAt = 10L)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.PLAYBACK)

        verify(vod).prunePlaybackToMostRecent(profileId, SnapshotLimits.PLAYBACK)
        assertEquals(2, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("episode:1").asJsonObject
        assertEquals(setOf("positionMs", "durationMs", "lastAccessedAt"), item.keySet())
    }

    @Test
    fun `recently-watched-live snapshot prunes Room to the real cap, keyed live colon providerId`() = runTest {
        whenever(live.wireRows(profileId, accountKey)).thenReturn(listOf(RecentlyWatchedWireRow(providerId = 1, watchedAt = 10L)))

        val snapshot = serializer.snapshot(profileId, SyncNamespace.RECENTLY_WATCHED_LIVE)

        verify(live).pruneRecentlyWatchedToMostRecent(profileId, SnapshotLimits.RECENTLY_WATCHED_LIVE)
        assertEquals(setOf("live:1"), snapshot.objects.keys)
    }

    @Test
    fun `an uncapped namespace never calls a prune query, and stays at v1`() = runTest {
        whenever(ratings.getAllForProfile(profileId, accountKey)).thenReturn(emptyList())

        val snapshot = serializer.snapshot(profileId, SyncNamespace.RATINGS)

        assertEquals(1, snapshot.schemaVersion)
        org.mockito.kotlin.verifyNoInteractions(favorites, vod, live)
    }

    // T20-R2: `ratings`, `track-preferences`, `series-watch-state` and `category-preferences` are
    // declared unchanged at v1 -- their JSON body must stay byte-for-byte the pre-T20 shape
    // (`gson.toJsonTree(entity).withoutFields("profileId")`), not just encode the reference in the
    // map key. These four exact-JSON assertions pin that contract down.

    @Test
    fun `ratings snapshot keeps the exact v1 object shape`() = runTest {
        whenever(ratings.getAllForProfile(profileId, accountKey)).thenReturn(
            listOf(MediaRatingRow(providerId = 815, kind = "movie", value = 4)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.RATINGS)

        assertEquals(1, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("movie:815")
        assertEquals("""{"mediaType":"movie","mediaId":815,"value":4}""", Gson().toJson(item))
    }

    @Test
    fun `track-preferences snapshot keeps the exact v1 object shape`() = runTest {
        whenever(tracks.getAllForProfile(profileId, accountKey)).thenReturn(
            listOf(TrackPreferenceRow(providerId = 42, kind = "series", audioLang = "fre", subtitleLang = "none")),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.TRACK_PREFERENCES)

        assertEquals(1, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("series:42")
        assertEquals("""{"mediaType":"series","mediaId":42,"audioLang":"fre","subtitleLang":"none"}""", Gson().toJson(item))
    }

    @Test
    fun `series-watch-state snapshot keeps the exact v1 object shape`() = runTest {
        whenever(series.getAllForProfile(profileId, accountKey)).thenReturn(
            listOf(SeriesWatchStateRow(providerId = 99, lastKnownSeason = 2, lastKnownEpisode = 5, lastNotifiedSeason = 2, lastNotifiedEpisode = 4, updatedAt = 123L)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.SERIES_WATCH_STATE)

        assertEquals(1, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("99")
        assertEquals(
            """{"seriesId":99,"lastKnownSeason":2,"lastKnownEpisode":5,"lastNotifiedSeason":2,"lastNotifiedEpisode":4,"updatedAt":123}""",
            Gson().toJson(item),
        )
    }

    @Test
    fun `category-preferences snapshot keeps the exact v1 object shape`() = runTest {
        whenever(categories.getAllForProfile(profileId, accountKey)).thenReturn(
            listOf(CategoryPreferenceRow(categoryId = "12", kind = "vod", hidden = true, sortOrder = 3)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.CATEGORY_PREFERENCES)

        assertEquals(1, snapshot.schemaVersion)
        val item = snapshot.objects.getValue("vod:12")
        assertEquals("""{"categoryId":"12","type":"vod","hidden":true,"sortOrder":3}""", Gson().toJson(item))
    }

    // T20-R5: a cloud reference of unknown or invalid type must never be applied (T20 error rule).
    // These exercise `parseKindProviderId`/`parseKindCategoryId` directly rather than through
    // `apply()` -- see the `internal` visibility note on those functions for why -- and cover the
    // review's exact failure case (a `movie:` key that used to be silently resolved as `live` in
    // recently-watched-live because the parsed kind was discarded) plus a cross-kind rejection on a
    // second namespace and an unrecognized kind. Every `apply()` call site does `?: return@forEach`
    // immediately on a `null` result, before any identity is resolved or any row is written, so a
    // `null` here is exactly Room never being touched.

    @Test
    fun `recently-watched-live rejects a non-live kind instead of silently resolving it as live`() {
        assertEquals("live" to 1042, serializer.parseKindProviderId("live:1042", RoomSnapshotSerializer.RECENTLY_WATCHED_LIVE_KINDS))
        // This is the bug T20-R5 fixed: a `movie:` key must be rejected outright, never coerced
        // into a `live` identity by a caller that discards the parsed kind.
        assertEquals(null, serializer.parseKindProviderId("movie:42", RoomSnapshotSerializer.RECENTLY_WATCHED_LIVE_KINDS))
    }

    @Test
    fun `favorites reject a kind outside the favorites domain, and an unrecognized kind`() {
        assertEquals("movie" to 815, serializer.parseKindProviderId("movie:815", RoomSnapshotSerializer.FAVORITE_KINDS))
        // "episode" is never a valid favorites kind (favorites are live/movie/series only).
        assertEquals(null, serializer.parseKindProviderId("episode:99", RoomSnapshotSerializer.FAVORITE_KINDS))
        // Entirely unrecognized/corrupted kind.
        assertEquals(null, serializer.parseKindProviderId("bogus:1", RoomSnapshotSerializer.FAVORITE_KINDS))
    }

    @Test
    fun `playback rejects a live kind, which is never a valid resume target`() {
        assertEquals("episode" to 99213, serializer.parseKindProviderId("episode:99213", RoomSnapshotSerializer.PLAYBACK_KINDS))
        assertEquals(null, serializer.parseKindProviderId("live:9", RoomSnapshotSerializer.PLAYBACK_KINDS))
    }

    @Test
    fun `category-preferences reject a kind outside live vod series`() {
        assertEquals("vod" to "12", serializer.parseKindCategoryId("vod:12", RoomSnapshotSerializer.CATEGORY_KINDS))
        assertEquals(null, serializer.parseKindCategoryId("episode:12", RoomSnapshotSerializer.CATEGORY_KINDS))
    }

    @Test
    fun `a key with no separator or an unparsable provider id is rejected regardless of kind`() {
        assertEquals(null, serializer.parseKindProviderId("live", RoomSnapshotSerializer.FAVORITE_KINDS))
        assertEquals(null, serializer.parseKindProviderId("live:notanumber", RoomSnapshotSerializer.FAVORITE_KINDS))
        assertEquals(null, serializer.parseKindCategoryId("vod", RoomSnapshotSerializer.CATEGORY_KINDS))
    }
}
