package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.ViewingHistoryRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ViewingHistoryUseCasesTest {
    private val repository: ViewingHistoryRepository = mock()

    @Test
    fun `removing a resume item invalidates recommendations after exact deletion`() = runTest {
        val recommendations: GetRecommendationsUseCase = mock()
        val position = PlaybackPosition(streamId = 42, positionMs = 1, durationMs = 2, lastAccessedAt = 3)
        RemoveFromContinueWatchingUseCase(repository, recommendations)(position)

        verify(repository).removeFromContinueWatching(position)
        verify(recommendations).invalidateCache()
    }

    @Test
    fun `failed resume deletion does not invalidate recommendations`() = runTest {
        val recommendations: GetRecommendationsUseCase = mock()
        val position = PlaybackPosition(streamId = 42, positionMs = 1, durationMs = 2, lastAccessedAt = 3)
        whenever(repository.removeFromContinueWatching(position)).thenThrow(IllegalStateException())

        runCatching { RemoveFromContinueWatchingUseCase(repository, recommendations)(position) }

        verifyNoInteractions(recommendations)
    }

    @Test
    fun `removing a live item never touches recommendations`() = runTest {
        RemoveRecentlyWatchedUseCase(repository)(42)

        verify(repository).removeRecentlyWatched(42)
    }

    @Test
    fun `observing recent live items delegates the reactive list unchanged`() = runTest {
        val expected = listOf(LiveStream(42, "News", null, null, 1, "5"))
        whenever(repository.observeRecentlyWatched()).thenReturn(flowOf(expected))

        val actual = ObserveRecentlyWatchedUseCase(repository)().first()

        assertEquals(expected, actual)
    }

    @Test
    fun `observing recent live items preserves later reactive updates`() = runTest {
        val updates = MutableStateFlow(emptyList<LiveStream>())
        whenever(repository.observeRecentlyWatched()).thenReturn(updates)

        val observed = ObserveRecentlyWatchedUseCase(repository)()
        updates.value = listOf(LiveStream(42, "News", null, null, 1, "5"))

        assertEquals(42, observed.first().single().streamId)
    }
}
