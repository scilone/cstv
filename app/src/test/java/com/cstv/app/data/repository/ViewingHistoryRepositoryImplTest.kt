package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.RecentlyWatchedListRow
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.PlaybackPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.Assert.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ViewingHistoryRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val vodDao: VodDao = mock()
    private val liveTvDao: LiveTvDao = mock()
    private val mediaRefDao: MediaRefDao = mock()
    private val accountKeyProvider: CurrentAccountKeyProvider = mock { on { current() }.thenReturn("account-key") }
    private val activeProfileId = MutableStateFlow(7)
    private val profileManager: ProfileManager = mock {
        on { activeProfileId }.thenReturn(this@ViewingHistoryRepositoryImplTest.activeProfileId)
        on { currentProfileId() }.thenAnswer { this@ViewingHistoryRepositoryImplTest.activeProfileId.value }
    }
    private val repository = ViewingHistoryRepositoryImpl(vodDao, liveTvDao, profileManager, mediaRefDao, accountKeyProvider)

    @Test
    fun `resume deletion is scoped to the current profile, account and exact episode`() = runTest {
        repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, type = "episode", seriesId = 100))

        verify(profileManager, times(1)).currentProfileId()
        verify(vodDao).deletePlaybackPosition(7, "account-key", "episode", 42)
        verify(mediaRefDao).purgeUnreferenced()
    }

    @Test
    fun `live deletion is scoped to the current profile and account`() = runTest {
        repository.removeRecentlyWatched(24)

        verify(profileManager, times(1)).currentProfileId()
        verify(liveTvDao).deleteRecentlyWatched(7, "account-key", 24)
        verify(mediaRefDao).purgeUnreferenced()
    }

    @Test
    fun `deletion with no known kind is a no-op`() = runTest {
        repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, type = null))

        verify(vodDao, never()).deletePlaybackPosition(any(), any(), any(), any())
    }

    @Test
    fun `deletion propagates a local storage failure without widening its target`() = runTest {
        whenever(vodDao.deletePlaybackPosition(7, "account-key", "movie", 42)).thenThrow(IllegalStateException("Room failure"))

        runCatching { repository.removeFromContinueWatching(PlaybackPosition(42, 1, 2, 3, type = "movie")) }
            .onSuccess { throw AssertionError("Expected local storage failure") }

        verify(vodDao).deletePlaybackPosition(7, "account-key", "movie", 42)
    }

    @Test
    fun `recent live observation follows the active profile and maps its entries`() = runTest {
        whenever(liveTvDao.observeRecentlyWatched(7, "account-key", 10)).thenReturn(flowOf(listOf(
            RecentlyWatchedListRow(24, 9, "News", "icon", "5", 3)
        )))

        val streams = repository.observeRecentlyWatched().first()

        verify(liveTvDao).observeRecentlyWatched(7, "account-key", 10)
        assertEquals(1, streams.size)
        assertEquals(24, streams.single().streamId)
        assertEquals("News", streams.single().name)
    }
}
