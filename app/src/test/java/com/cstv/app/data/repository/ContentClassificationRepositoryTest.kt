package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.dto.CatalogItemDto
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchResponseDto
import com.cstv.app.domain.model.AgeRating
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentClassificationRepositoryTest {

    private val catalogApiService: CstvCatalogApiService = mock()
    private var now = 0L
    private val timeProvider = object : TimeProvider { override fun nowMillis() = now }
    private val repository = ContentClassificationRepository(catalogApiService, timeProvider)

    @Test
    fun `known classification is mapped to AgeRating`() = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Dune", 2021))).thenReturn(
            CatalogMatchResponseDto(item = CatalogItemDto(ageRatingFr = 12))
        )

        assertEquals(AgeRating.TWELVE, repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021))
    }

    @Test
    fun `unknown classification from backend is never converted to ALL`() = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Unknown", null))).thenReturn(
            CatalogMatchResponseDto(item = CatalogItemDto(ageRatingFr = null))
        )

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Unknown", null))
    }

    @Test
    fun `no matched item is treated as unknown, not ALL`() = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("series", "Missing", null))).thenReturn(
            CatalogMatchResponseDto(item = null)
        )

        assertNull(repository.classificationFor(MediaClassificationKind.SERIES, "Missing", null))
    }

    @Test
    fun `a service failure is defensively treated as unknown`() = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Boom", null))).thenThrow(RuntimeException("network down"))

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Boom", null))
    }

    @Test
    fun `a service failure is not cached and the next attempt retries`() {
        runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Retry", null)))
            .thenThrow(RuntimeException("network down"))
            .thenReturn(CatalogMatchResponseDto(item = CatalogItemDto(ageRatingFr = 12)))

        assertNull(repository.classificationFor(MediaClassificationKind.MOVIE, "Retry", null))
        assertEquals(AgeRating.TWELVE, repository.classificationFor(MediaClassificationKind.MOVIE, "Retry", null))

            verify(catalogApiService, org.mockito.kotlin.times(2))
                .match(CatalogMatchRequestDto("movie", "Retry", null))
        }
    }

    @Test
    fun `a fresh cached result skips the network on the second call`(): Unit = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Dune", 2021))).thenReturn(
            CatalogMatchResponseDto(item = CatalogItemDto(ageRatingFr = 12))
        )

        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)
        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)

        verify(catalogApiService, org.mockito.kotlin.times(1)).match(CatalogMatchRequestDto("movie", "Dune", 2021))
    }

    @Test
    fun `an expired cache entry triggers a fresh network call`(): Unit = runBlocking {
        whenever(catalogApiService.match(CatalogMatchRequestDto("movie", "Dune", 2021))).thenReturn(
            CatalogMatchResponseDto(item = CatalogItemDto(ageRatingFr = 12))
        )

        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)
        now += 31L * 60 * 1000
        repository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)

        verify(catalogApiService, org.mockito.kotlin.times(2)).match(CatalogMatchRequestDto("movie", "Dune", 2021))
    }
}
