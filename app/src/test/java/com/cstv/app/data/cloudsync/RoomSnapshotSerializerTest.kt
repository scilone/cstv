package com.cstv.app.data.cloudsync

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.TrackPreferenceDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * T19-R2/T19-R4: exercises `RoomSnapshotSerializer.snapshot()` against mocked DAOs -- the real
 * production wiring, not just the pure `capMostRecent`/`strippedFields`/`withoutFields` functions
 * covered by [SnapshotLimitsTest]. Proves the per-item cap is enforced *in Room* (the DAO prune
 * query is actually invoked, with the right profile and limit) rather than only in the in-memory
 * list handed back to the caller.
 */
class RoomSnapshotSerializerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private val profileId = 7

    @Mock private lateinit var favorites: FavoritesDao
    @Mock private lateinit var vod: VodDao
    @Mock private lateinit var ratings: MediaRatingDao
    @Mock private lateinit var tracks: TrackPreferenceDao
    @Mock private lateinit var series: SeriesWatchStateDao
    @Mock private lateinit var categories: CategoryPreferenceDao
    @Mock private lateinit var live: LiveTvDao
    @Mock private lateinit var database: AppDatabase

    private lateinit var serializer: RoomSnapshotSerializer

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        serializer = RoomSnapshotSerializer(favorites, vod, ratings, tracks, series, categories, live, Gson(), database)
    }

    @Test
    fun `favorites snapshot prunes Room to the real cap before reading, and strips profileId`() = runTest {
        whenever(favorites.getAllForProfile(profileId)).thenReturn(
            listOf(FavoriteEntity(id = 1, type = "movie", name = "A", cover = null, categoryId = "c", addedAt = 10L, profileId = profileId)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.FAVORITES)

        verify(favorites).pruneToMostRecent(profileId, SnapshotLimits.FAVORITES)
        assertFalse(snapshot.objects.getValue("movie:1").asJsonObject.has("profileId"))
    }

    @Test
    fun `playback snapshot prunes Room to the real cap, and strips profileId and plot`() = runTest {
        whenever(vod.getAllPlaybackPositions(profileId)).thenReturn(
            listOf(
                PlaybackPositionEntity(
                    streamId = 1, profileId = profileId, positionMs = 1000, durationMs = 2000,
                    lastAccessedAt = 10L, plot = "a long synopsis",
                ),
            ),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.PLAYBACK)

        verify(vod).prunePlaybackToMostRecent(profileId, SnapshotLimits.PLAYBACK)
        val item = snapshot.objects.getValue("1").asJsonObject
        assertFalse(item.has("profileId"))
        assertFalse(item.has("plot"))
    }

    @Test
    fun `recently-watched-live snapshot prunes Room to the real cap before the capped read`() = runTest {
        whenever(live.getRecentlyWatched(profileId, SnapshotLimits.RECENTLY_WATCHED_LIVE)).thenReturn(
            listOf(RecentlyWatchedLiveEntity(streamId = 1, profileId = profileId, name = "Chan", streamIcon = null, categoryId = null, num = 1, watchedAt = 10L)),
        )

        val snapshot = serializer.snapshot(profileId, SyncNamespace.RECENTLY_WATCHED_LIVE)

        verify(live).pruneRecentlyWatchedToMostRecent(profileId, SnapshotLimits.RECENTLY_WATCHED_LIVE)
        assertEquals(setOf("1"), snapshot.objects.keys)
    }

    @Test
    fun `an uncapped namespace never calls a prune query`() = runTest {
        whenever(ratings.getAllForProfile(profileId)).thenReturn(emptyList())

        serializer.snapshot(profileId, SyncNamespace.RATINGS)

        org.mockito.kotlin.verifyNoInteractions(favorites, vod, live)
    }
}
