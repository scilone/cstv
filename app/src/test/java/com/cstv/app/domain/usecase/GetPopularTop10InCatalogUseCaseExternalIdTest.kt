package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.ExternalCatalogLink
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.ExternalCatalogLinkRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.PopularRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** T24 : même couverture que [GetTrendingInCatalogUseCaseCanonicalTest], côté Popular (`match()` partagé). */
class GetPopularTop10InCatalogUseCaseExternalIdTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private suspend fun useCase(
        popularRepository: PopularRepository,
        vodRepository: VodRepository,
        externalCatalogLinkRepository: ExternalCatalogLinkRepository
    ): GetPopularTop10InCatalogUseCase {
        val catalogFreshness = mock<com.cstv.app.data.sync.CatalogFreshness>()
        whenever(catalogFreshness.vodSyncedAt()).thenReturn(0L)
        val preferences = mock<CategoryPreferenceRepository>()
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        return GetPopularTop10InCatalogUseCase(
            popularRepository, vodRepository, mock<SeriesRepository>(), preferences, catalogFreshness, externalCatalogLinkRepository
        )
    }

    @Test
    fun `a media already associated is resolved without invoking the matcher`() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val popular = TrendingTitle(externalId = "movie:1", title = "Titre TMDB sans rapport", isMovie = true, year = 2010, posterUrl = null)
        whenever(popularRepository.getPopularMovies()).thenReturn(listOf(popular))
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        val movie = VodStream(streamId = 42, name = "Le Vrai Film", streamIcon = null, rating = null, added = null, categoryId = "cat")
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(externalCatalogLinkRepository.findByExternalIds(listOf("movie:1")))
            .thenReturn(listOf(ExternalCatalogLink("movie", 42, "movie:1")))

        val result = useCase(popularRepository, vodRepository, externalCatalogLinkRepository).loadFreshMovies()

        assertEquals(listOf(42), result?.map { it.streamId })
        verify(externalCatalogLinkRepository, never()).persistAll(any())
    }

    @Test
    fun `an unknown externalId falls back to the matcher, then persists the new association`() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val popular = TrendingTitle(externalId = "movie:2", title = "Inception", isMovie = true, year = 2010, posterUrl = null)
        whenever(popularRepository.getPopularMovies()).thenReturn(listOf(popular))
        val movie = VodStream(streamId = 10, name = "Inception", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2010)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(listOf(movie))
        whenever(vodRepository.getStreamById(10)).thenReturn(movie)
        whenever(externalCatalogLinkRepository.findByExternalIds(any())).thenReturn(emptyList())

        val result = useCase(popularRepository, vodRepository, externalCatalogLinkRepository).loadFreshMovies()

        assertEquals(listOf(10), result?.map { it.streamId })
        verify(externalCatalogLinkRepository).persistAll(
            argThat { links -> links == listOf(ExternalCatalogLink("movie", 10, "movie:2")) }
        )
    }

    @Test
    fun `the externalId batch lookup is a single call for the whole popular list`() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val popular = listOf(
            TrendingTitle(externalId = "movie:1", title = "A", isMovie = true, year = 2020, posterUrl = null),
            TrendingTitle(externalId = "movie:2", title = "B", isMovie = true, year = 2021, posterUrl = null)
        )
        whenever(popularRepository.getPopularMovies()).thenReturn(popular)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        whenever(externalCatalogLinkRepository.findByExternalIds(any())).thenReturn(emptyList())

        useCase(popularRepository, vodRepository, externalCatalogLinkRepository).loadFreshMovies()

        verify(externalCatalogLinkRepository, times(1)).findByExternalIds(
            argThat { ids -> ids.toSet() == setOf("movie:1", "movie:2") }
        )
    }
}
