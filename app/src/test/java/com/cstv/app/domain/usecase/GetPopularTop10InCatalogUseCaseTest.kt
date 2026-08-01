package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.PopularCatalogItem
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.PopularRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetPopularTop10InCatalogUseCaseTest {

    private suspend fun useCase(
        popularRepository: PopularRepository,
        vodRepository: VodRepository = mock(),
        seriesRepository: SeriesRepository = mock(),
        preferences: CategoryPreferenceRepository = mock()
    ): GetPopularTop10InCatalogUseCase {
        val catalogFreshness = mock<com.cstv.app.data.sync.CatalogFreshness>()
        whenever(catalogFreshness.vodSyncedAt()).thenReturn(0L)
        whenever(catalogFreshness.seriesSyncedAt()).thenReturn(0L)
        return GetPopularTop10InCatalogUseCase(popularRepository, vodRepository, seriesRepository, preferences, catalogFreshness)
    }

    // --- loadFreshMovies / loadFreshSeries : chargement "à froid" (T8 règle 5) ---

    @Test
    fun loadFreshSeriesUsesTmdbOrder_movieFailureDoesNotAffectSeries() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        whenever(popularRepository.getCachedMatchedMovies(0L)).thenReturn(null)
        whenever(popularRepository.getCachedMatchedSeries(0L)).thenReturn(null)
        whenever(popularRepository.getPopularMovies()).thenReturn(emptyList())
        whenever(popularRepository.getPopularSeries()).thenReturn(
            listOf(
                TrendingTitle(1, "Second", false, 2024, null),
                TrendingTitle(2, "First", false, 2024, null)
            )
        )
        val second = SeriesStream(2, "Second", null, null, null, "visible", releaseYear = 2024)
        val first = SeriesStream(1, "First", null, null, null, "visible", releaseYear = 2024)
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(listOf(second, first))
        whenever(seriesRepository.getStreamById(2)).thenReturn(second)
        whenever(seriesRepository.getStreamById(1)).thenReturn(first)
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        val target = useCase(popularRepository, vodRepository, seriesRepository, preferences)

        assertNull(target.loadFreshMovies())
        assertEquals(listOf("Second", "First"), target.loadFreshSeries()?.map { it.name })
        verify(popularRepository).saveMatchedSeries(any())
    }

    @Test
    fun loadFreshMoviesAndSeriesPreferDatedCandidateOverUnenrichedHomonym() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        whenever(popularRepository.getCachedMatchedMovies(0L)).thenReturn(null)
        whenever(popularRepository.getCachedMatchedSeries(0L)).thenReturn(null)
        whenever(popularRepository.getPopularMovies()).thenReturn(
            listOf(TrendingTitle(1, "Dune", true, 2021, null))
        )
        whenever(popularRepository.getPopularSeries()).thenReturn(
            listOf(TrendingTitle(2, "Dune", false, 2021, null))
        )

        // Candidat non enrichi placé en premier dans le catalogue : sans le
        // rang d'année (B14), l'ordre du catalogue le ferait gagner à tort.
        val unenrichedMovie = VodStream(1, "Dune", null, null, null, "visible", releaseYear = null)
        val datedMovie = VodStream(2, "Dune", null, null, null, "visible", releaseYear = 2021)
        val unenrichedSeries = SeriesStream(1, "Dune", null, null, null, "visible", releaseYear = null)
        val datedSeries = SeriesStream(2, "Dune", null, null, null, "visible", releaseYear = 2021)

        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(listOf(unenrichedMovie, datedMovie))
        whenever(seriesRepository.getCachedSeriesStreamsByYears(any())).thenReturn(listOf(unenrichedSeries, datedSeries))
        whenever(vodRepository.getStreamById(2)).thenReturn(datedMovie)
        whenever(seriesRepository.getStreamById(2)).thenReturn(datedSeries)
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        val target = useCase(popularRepository, vodRepository, seriesRepository, preferences)

        assertEquals(listOf(2), target.loadFreshMovies()?.map { it.streamId })
        assertEquals(listOf(2), target.loadFreshSeries()?.map { it.seriesId })
    }

    @Test
    fun loadFreshMoviesFallsBackToLocalWhenCachedMatchIsDeletedOrHidden() = runTest {
        val popularRepository = mock<PopularRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        whenever(popularRepository.getCachedMatchedMovies(0L)).thenReturn(
            listOf(PopularCatalogItem(listOf(404)))
        )
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        val target = useCase(popularRepository, preferences = preferences)

        assertNull(target.loadFreshMovies())
    }

    @Test
    fun loadFreshMoviesFallsBackToPersistentCacheWhenTmdbIsUnreachableAtLaunch() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        // Rafraîchissement forcé au lancement : le cache est ignoré…
        whenever(popularRepository.getCachedMatchedMovies(0L)).thenReturn(null)
        // …mais TMDB est injoignable (liste vide).
        whenever(popularRepository.getPopularMovies()).thenReturn(emptyList())
        whenever(popularRepository.getCachedMatchedMovies(0L, ignoreSessionRefresh = true)).thenReturn(
            listOf(PopularCatalogItem(listOf(7)))
        )
        val movie = VodStream(7, "Dune", null, null, null, "visible", releaseYear = 2021)
        whenever(vodRepository.getStreamById(7)).thenReturn(movie)
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        val target = useCase(popularRepository, vodRepository, preferences = preferences)

        assertEquals(listOf(7), target.loadFreshMovies()?.map { it.streamId })
    }

    // --- cachedMovies / cachedSeries : lecture pour affichage immédiat (T8 règle 1) ---

    @Test
    fun cachedMoviesIsReturnedRegardlessOfAgeWithoutTouchingTheNetwork() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        whenever(popularRepository.getCachedMatchedMoviesIgnoringAge(0L)).thenReturn(
            listOf(PopularCatalogItem(listOf(7)))
        )
        val movie = VodStream(7, "Dune", null, null, null, "visible", releaseYear = 2021)
        whenever(vodRepository.getStreamById(7)).thenReturn(movie)
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())
        val target = useCase(popularRepository, vodRepository, preferences = preferences)

        assertEquals(listOf(7), target.cachedMovies()?.map { it.streamId })
        verify(popularRepository, never()).getPopularMovies()
    }

    @Test
    fun cachedSeriesIsNullWhenNoCacheExists() = runTest {
        val popularRepository = mock<PopularRepository>()
        whenever(popularRepository.getCachedMatchedSeriesIgnoringAge(0L)).thenReturn(null)
        val target = useCase(popularRepository)

        assertNull(target.cachedSeries())
    }

    // --- refreshMoviesSilently / refreshSeriesSilently : persistance sans affichage (T8 règles 2, 3, 4, 8) ---

    @Test
    fun refreshMoviesSilentlySavesFreshMatchesWithoutReturningAnything() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        whenever(popularRepository.getPopularMovies()).thenReturn(
            listOf(TrendingTitle(1, "Dune", true, 2021, null))
        )
        val dune = VodStream(2, "Dune", null, null, null, "visible", releaseYear = 2021)
        whenever(vodRepository.getCachedVodStreamsByYears(any())).thenReturn(listOf(dune))
        val target = useCase(popularRepository, vodRepository)

        target.refreshMoviesSilently()

        verify(popularRepository).saveMatchedMovies(any())
    }

    @Test
    fun refreshSeriesSilentlyKeepsExistingCacheWhenTmdbReturnsNothing() = runTest {
        val popularRepository = mock<PopularRepository>()
        whenever(popularRepository.getPopularSeries()).thenReturn(emptyList())
        val target = useCase(popularRepository)

        target.refreshSeriesSilently()

        verify(popularRepository, never()).saveMatchedSeries(any())
    }

    // --- isMoviesCacheExpired / isSeriesCacheExpired (T8-R1) ---

    @Test
    fun isMoviesCacheExpiredDelegatesToRepositoryWithTheCurrentCatalogSyncTime() = runTest {
        val popularRepository = mock<PopularRepository>()
        whenever(popularRepository.isMoviesCacheExpired(0L)).thenReturn(false)
        val target = useCase(popularRepository)

        assertEquals(false, target.isMoviesCacheExpired())
    }

    @Test
    fun isSeriesCacheExpiredDelegatesToRepositoryWithTheCurrentCatalogSyncTime() = runTest {
        val popularRepository = mock<PopularRepository>()
        whenever(popularRepository.isSeriesCacheExpired(0L)).thenReturn(true)
        val target = useCase(popularRepository)

        assertEquals(true, target.isSeriesCacheExpired())
    }
}
