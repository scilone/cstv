package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.PopularRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetPopularTop10InCatalogUseCaseTest {
    @Test
    fun loadsMovieAndSeriesBranchesConcurrently() = runTest {
        val movieGate = CompletableDeferred<Unit>()
        val seriesStarted = CompletableDeferred<Unit>()
        val popularRepository = object : PopularRepository {
            override suspend fun getPopularMovies(): List<TrendingTitle> {
                movieGate.await()
                return emptyList()
            }

            override suspend fun getPopularSeries(): List<TrendingTitle> {
                seriesStarted.complete(Unit)
                return emptyList()
            }

            override suspend fun getCachedMatchedMovies(lastVodCatalogSyncTime: Long) = null
            override suspend fun getCachedMatchedSeries(lastSeriesCatalogSyncTime: Long) = null
            override suspend fun saveMatchedMovies(items: List<com.cstv.app.domain.model.PopularCatalogItem>) = Unit
            override suspend fun saveMatchedSeries(items: List<com.cstv.app.domain.model.PopularCatalogItem>) = Unit
        }
        val settings = mock<SettingsManager>()
        whenever(settings.getVodAllStreamsSyncedAt()).thenReturn(0L)
        whenever(settings.getSeriesAllStreamsSyncedAt()).thenReturn(0L)
        val useCase = GetPopularTop10InCatalogUseCase(
            popularRepository,
            mock<VodRepository>(),
            mock<SeriesRepository>(),
            mock<CategoryPreferenceRepository>(),
            settings
        )

        val result = async { useCase() }
        withTimeout(1_000L) { seriesStarted.await() }
        movieGate.complete(Unit)
        result.await()
    }

    @Test
    fun usesTmdbOrderAndLetsMovieFailureKeepSeriesResult() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        val settings = mock<SettingsManager>()
        whenever(settings.getVodAllStreamsSyncedAt()).thenReturn(0L)
        whenever(settings.getSeriesAllStreamsSyncedAt()).thenReturn(0L)
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
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(listOf(second, first))
        whenever(seriesRepository.getStreamById(2)).thenReturn(second)
        whenever(seriesRepository.getStreamById(1)).thenReturn(first)
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())

        val result = GetPopularTop10InCatalogUseCase(
            popularRepository, vodRepository, seriesRepository, preferences, settings
        )()

        assertNull(result.movies)
        assertEquals(listOf("Second", "First"), result.series?.map { it.name })
        verify(popularRepository).saveMatchedSeries(any())
    }

    @Test
    fun cachedDeletedOrHiddenMoviesFallBackToLocal() = runTest {
        val popularRepository = mock<PopularRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val preferences = mock<CategoryPreferenceRepository>()
        val settings = mock<SettingsManager>()
        whenever(settings.getVodAllStreamsSyncedAt()).thenReturn(0L)
        whenever(settings.getSeriesAllStreamsSyncedAt()).thenReturn(0L)
        whenever(popularRepository.getCachedMatchedMovies(0L)).thenReturn(
            listOf(com.cstv.app.domain.model.PopularCatalogItem(listOf(404)))
        )
        whenever(popularRepository.getCachedMatchedSeries(0L)).thenReturn(null)
        whenever(popularRepository.getPopularSeries()).thenReturn(emptyList())
        whenever(preferences.getPreferences(any())).thenReturn(emptyMap())

        val result = GetPopularTop10InCatalogUseCase(
            popularRepository, vodRepository, seriesRepository, preferences, settings
        )()

        assertNull(result.movies)
    }
}
