package com.cstv.app.data.repository

import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.repository.ExternalMetadataRepository
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentClassificationRepositoryTest {

    private val externalMetadataRepository: ExternalMetadataRepository = mock()
    private var now = 0L
    private val timeProvider = object : TimeProvider { override fun nowMillis() = now }
    private val repository = ContentClassificationRepository(externalMetadataRepository, timeProvider)

    private fun match(ageRating: Int?) = ExternalMetadataMatchOutcome.Matched(
        ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 90, "title+year", 1, ageRating),
    )

    @Test
    fun `a resolved match exposes its exact ageRating, no bucketing`() = runBlocking {
        whenever(externalMetadataRepository.match("movie", 42, "Dune", 2021, null, ExternalMatchHints(), allowRefresh = true)).thenReturn(match(13))

        assertEquals(13, repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42))
    }

    @Test
    fun `no providerId never calls the repository and returns unknown`(): Unit = runBlocking {
        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, providerId = null))
        verify(externalMetadataRepository, org.mockito.kotlin.never()).match(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `an unmatched work is treated as unknown, not ALL`() = runBlocking {
        whenever(externalMetadataRepository.match("series", 7, "Missing", null, null, ExternalMatchHints(), allowRefresh = true)).thenReturn(ExternalMetadataMatchOutcome.Unresolved)

        assertNull(repository.classificationFor(MediaClassificationKind.SERIES, "Missing", null, 7))
    }

    /** T29 §7.3 : une impossibilité technique temporaire (429 provider) se comporte comme une erreur réseau — jamais mise en cache comme "inconnue". */
    @Test
    fun `a retry outcome is treated as unknown and never cached`(): Unit = runBlocking {
        whenever(externalMetadataRepository.match("movie", 3, "Retry Me", null, null, ExternalMatchHints(), allowRefresh = true))
            .thenReturn(ExternalMetadataMatchOutcome.Retry(3_000L))
            .thenReturn(match(9))

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Retry Me", null, 3))
        assertEquals(9, repository.classificationFor(MediaClassificationKind.MOVIE, "Retry Me", null, 3))

        verify(externalMetadataRepository, times(2)).match("movie", 3, "Retry Me", null, null, ExternalMatchHints(), allowRefresh = true)
    }

    @Test
    fun `a service failure is defensively treated as unknown`() = runBlocking {
        whenever(externalMetadataRepository.match("movie", 1, "Boom", null, null, ExternalMatchHints(), allowRefresh = true)).thenThrow(RuntimeException("network down"))

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Boom", null, 1))
    }

    @Test
    fun `a service failure is not cached and the next attempt retries`(): Unit = runBlocking {
        whenever(externalMetadataRepository.match("movie", 2, "Retry", null, null, ExternalMatchHints(), allowRefresh = true))
            .thenThrow(RuntimeException("network down"))
            .thenReturn(match(12))

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Retry", null, 2))
        assertEquals(12, repository.classificationFor(MediaClassificationKind.MOVIE, "Retry", null, 2))

        verify(externalMetadataRepository, times(2)).match("movie", 2, "Retry", null, null, ExternalMatchHints(), allowRefresh = true)
    }

    @Test
    fun `a fresh cached result skips the repository call on the second lookup`(): Unit = runBlocking {
        whenever(externalMetadataRepository.match("movie", 42, "Dune", 2021, null, ExternalMatchHints(), allowRefresh = true)).thenReturn(match(12))

        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)
        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)

        verify(externalMetadataRepository, times(1)).match("movie", 42, "Dune", 2021, null, ExternalMatchHints(), allowRefresh = true)
    }

    @Test
    fun `an expired cache entry triggers a fresh lookup`(): Unit = runBlocking {
        whenever(externalMetadataRepository.match("movie", 42, "Dune", 2021, null, ExternalMatchHints(), allowRefresh = true)).thenReturn(match(12))

        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)
        now += 31L * 60 * 1000
        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)

        verify(externalMetadataRepository, times(2)).match("movie", 42, "Dune", 2021, null, ExternalMatchHints(), allowRefresh = true)
    }
}
