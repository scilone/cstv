package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.PlaybackPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ViewingHistoryRepositoryImplTest {
    private val vodDao: VodDao = mock()
    private val liveTvDao: LiveTvDao = mock()
    private val activeProfileId = MutableStateFlow(7)
    private val profileManager: ProfileManager = mock {
        on { activeProfileId }.thenReturn(this@ViewingHistoryRepositoryImplTest.activeProfileId)
        on { currentProfileId() }.thenAnswer { this@ViewingHistoryRepositoryImplTest.activeProfileId.value }
    }
    private val repository = ViewingHistoryRepositoryImpl(vodDao, liveTvDao, profileManager)

    @Test
    fun `resume deletion is scoped to the current profile and exact episode`() = runTest {
        repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, seriesId = 100))

        verify(profileManager, times(1)).currentProfileId()
        verify(vodDao).deletePlaybackPosition(42, 7)
    }

    @Test
    fun `live deletion is scoped to the current profile`() = runTest {
        repository.removeRecentlyWatched(24)

        verify(profileManager, times(1)).currentProfileId()
        verify(liveTvDao).deleteRecentlyWatched(24, 7)
    }

    @Test
    fun `deletion is idempotent when the target row is already absent`() = runTest {
        repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, seriesId = null))

        verify(vodDao).deletePlaybackPosition(42, 7)
    }

    @Test
    fun `deletion propagates a local storage failure without widening its target`() = runTest {
        whenever(vodDao.deletePlaybackPosition(42, 7)).thenThrow(IllegalStateException("Room failure"))

        runCatching { repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, seriesId = 100)) }
            .onSuccess { throw AssertionError("Expected local storage failure") }

        verify(vodDao).deletePlaybackPosition(42, 7)
    }

    @Test
    fun `recent live observation follows the active profile and maps its entries`() = runTest {
        whenever(liveTvDao.observeRecentlyWatched(7, 10)).thenReturn(flowOf(listOf(
            RecentlyWatchedLiveEntity(24, 7, "News", "icon", "5", 3, 9)
        )))

        val streams = repository.observeRecentlyWatched().first()

        verify(liveTvDao).observeRecentlyWatched(7, 10)
        assertEquals(1, streams.size)
        assertEquals(24, streams.single().streamId)
        assertEquals("News", streams.single().name)
    }
}
