package com.cstv.app.data.cloudsync

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.dao.FavoriteWireRow
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.PlaybackWireRow
import com.cstv.app.data.local.dao.RecentlyWatchedWireRow
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.TrackPreferenceDao
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
}
