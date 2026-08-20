package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.ExternalCatalogLink
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.ExternalCatalogLinkRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/**
 * T24 : résolution/persistance du `externalId` en amont du matching
 * `ExternalCatalogMatcher`. Complète [GetTrendingInCatalogUseCaseTest] (qui
 * couvre déjà le matching lui-même, inchangé) — voir
 * `ai/technical/archive/T24-persistance-canonicalid-trending-popular.md`.
 */
class GetTrendingInCatalogUseCaseExternalIdTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private suspend fun useCase(
        trendingRepository: TrendingRepository,
        vodRepository: VodRepository,
        seriesRepository: SeriesRepository,
        externalCatalogLinkRepository: ExternalCatalogLinkRepository
    ): GetTrendingInCatalogUseCase {
        val catalogFreshness = mock<com.cstv.app.data.sync.CatalogFreshness>()
        whenever(catalogFreshness.vodSyncedAt()).thenReturn(0L)
        whenever(catalogFreshness.seriesSyncedAt()).thenReturn(0L)
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        return GetTrendingInCatalogUseCase(
            trendingRepository, vodRepository, seriesRepository, categoryPreferenceRepository, catalogFreshness, externalCatalogLinkRepository
        )
    }

    @Test
    fun `a media already associated is resolved without invoking the matcher`() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        // Titre TMDB volontairement très différent de "Le Vrai Film" : si le
        // matcher (par titre/année) était encore sollicité, il ne trouverait
        // jamais ce candidat -- seul le hit externalId peut produire ce résultat.
        val trend = TrendingTitle(externalId = "movie:438631", title = "Titre TMDB Sans Rapport", isMovie = true, year = 2010, posterUrl = null)
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trend))
        whenever(trendingRepository.getCachedMatchedTrendsGlobal(0L)).thenReturn(null)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(emptyList())

        val localMovie = VodStream(streamId = 99, name = "Le Vrai Film", streamIcon = null, rating = null, added = null, categoryId = "cat")
        whenever(vodRepository.getStreamById(99)).thenReturn(localMovie)
        whenever(externalCatalogLinkRepository.findByExternalIds(listOf("movie:438631")))
            .thenReturn(listOf(ExternalCatalogLink("movie", 99, "movie:438631")))

        val result = useCase(trendingRepository, vodRepository, seriesRepository, externalCatalogLinkRepository)()

        assertEquals(1, result.size)
        assertEquals(99, result.single().matchedMovie?.streamId)
        // Pas de nouveau match résolu, donc rien à persister à nouveau.
        verify(externalCatalogLinkRepository, never()).persistAll(any())
    }

    @Test
    fun `an unknown externalId falls back to the matcher, then persists the new association`() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val trend = TrendingTitle(externalId = "movie:999", title = "Inception", isMovie = true, year = 2010, posterUrl = null)
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trend))
        whenever(trendingRepository.getCachedMatchedTrendsGlobal(0L)).thenReturn(null)
        val movie = VodStream(streamId = 10, name = "Inception", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2010)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(listOf(movie))
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(emptyList())
        whenever(vodRepository.getStreamById(10)).thenReturn(movie)
        whenever(externalCatalogLinkRepository.findByExternalIds(any())).thenReturn(emptyList())

        val result = useCase(trendingRepository, vodRepository, seriesRepository, externalCatalogLinkRepository)()

        assertEquals(1, result.size)
        assertEquals(10, result.single().matchedMovie?.streamId)
        verify(externalCatalogLinkRepository).persistAll(
            argThat { links -> links == listOf(ExternalCatalogLink("movie", 10, "movie:999")) }
        )
    }

    @Test
    fun `several local versions of the same work share the same externalId once resolved`() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val trend = TrendingTitle(externalId = "movie:1", title = "T", isMovie = true, year = 2020, posterUrl = null)
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trend))
        whenever(trendingRepository.getCachedMatchedTrendsGlobal(0L)).thenReturn(null)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(emptyList())
        val versionA = VodStream(streamId = 1, name = "Film VF", streamIcon = null, rating = null, added = null, categoryId = "cat")
        val versionB = VodStream(streamId = 2, name = "Film MULTI 4K", streamIcon = null, rating = null, added = null, categoryId = "cat")
        whenever(vodRepository.getStreamById(1)).thenReturn(versionA)
        whenever(vodRepository.getStreamById(2)).thenReturn(versionB)
        whenever(externalCatalogLinkRepository.findByExternalIds(listOf("movie:1"))).thenReturn(
            listOf(ExternalCatalogLink("movie", 1, "movie:1"), ExternalCatalogLink("movie", 2, "movie:1"))
        )

        val result = useCase(trendingRepository, vodRepository, seriesRepository, externalCatalogLinkRepository)()

        assertEquals(1, result.size)
        assertEquals(setOf(1, 2), result.single().matchedMovies.map { it.streamId }.toSet())
    }

    @Test
    fun `the externalId batch lookup is a single call for the whole trending list, never one per item`() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        val trends = listOf(
            TrendingTitle(externalId = "movie:1", title = "A", isMovie = true, year = 2020, posterUrl = null),
            TrendingTitle(externalId = "movie:2", title = "B", isMovie = true, year = 2021, posterUrl = null),
            TrendingTitle(externalId = "series:1", title = "C", isMovie = false, year = 2019, posterUrl = null)
        )
        whenever(trendingRepository.getTrending()).thenReturn(trends)
        whenever(trendingRepository.getCachedMatchedTrendsGlobal(0L)).thenReturn(null)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(emptyList())
        whenever(externalCatalogLinkRepository.findByExternalIds(any())).thenReturn(emptyList())

        useCase(trendingRepository, vodRepository, seriesRepository, externalCatalogLinkRepository)()

        verify(externalCatalogLinkRepository, times(1)).findByExternalIds(
            argThat { ids -> ids.toSet() == setOf("movie:1", "movie:2", "series:1") }
        )
    }

    @Test
    fun `externalId is used only as an opaque lookup key, its content is never interpreted`() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val externalCatalogLinkRepository = mock<ExternalCatalogLinkRepository>()

        // Format volontairement non conforme à "kind:id" -- doit fonctionner
        // identiquement, aucune supposition sur son contenu n'est permise.
        val opaqueId = "not-a-real-format-éà"
        val trend = TrendingTitle(externalId = opaqueId, title = "T", isMovie = true, year = 2020, posterUrl = null)
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trend))
        whenever(trendingRepository.getCachedMatchedTrendsGlobal(0L)).thenReturn(null)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(emptyList())
        val movie = VodStream(streamId = 5, name = "T", streamIcon = null, rating = null, added = null, categoryId = "cat")
        whenever(vodRepository.getStreamById(5)).thenReturn(movie)
        whenever(externalCatalogLinkRepository.findByExternalIds(listOf(opaqueId)))
            .thenReturn(listOf(ExternalCatalogLink("movie", 5, opaqueId)))

        val result = useCase(trendingRepository, vodRepository, seriesRepository, externalCatalogLinkRepository)()

        assertEquals(5, result.single().matchedMovie?.streamId)
    }
}
