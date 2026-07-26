package com.cstv.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.data.remote.dto.TmdbTrendingItemDto
import com.cstv.app.data.remote.dto.TmdbTrendingResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class PopularRepositoryImplTest {
    @Test
    fun getPopularMovies_mapsOnlyValidMovieTitlesAndIds() = runTest {
        val service = mock<TmdbApiService>()
        whenever(service.getPopularMovies("key", page = 1)).thenReturn(
            TmdbTrendingResponseDto(listOf(
                TmdbTrendingItemDto("14", "Film", null, null, "/poster.jpg", "2024-02-01", null),
                TmdbTrendingItemDto("bad", "Ignored", null, null, null, null, null),
                TmdbTrendingItemDto(15, null, "TV name", null, null, null, "2024-01-01")
            ))
        )
        whenever(service.getPopularMovies("key", page = 2)).thenReturn(TmdbTrendingResponseDto(emptyList()))
        whenever(service.getPopularMovies("key", page = 3)).thenReturn(TmdbTrendingResponseDto(emptyList()))

        val result = PopularRepositoryImpl(mock<Context>(), service, "key", Gson()).getPopularMovies()

        assertEquals(1, result.size)
        assertEquals(14, result.single().tmdbId)
        assertEquals("Film", result.single().title)
        assertEquals(2024, result.single().year)
    }

    @Test
    fun getPopularMovies_fetchesThreePagesAndKeepsOnlyFirstFiftyCandidates() = runTest {
        val service = mock<TmdbApiService>()
        (1..3).forEach { page ->
            val pageItems = (1..20).map { index ->
                val id = (page - 1) * 20 + index
                TmdbTrendingItemDto(id, "Film $id", null, null, null, "2024-01-01", null)
            }
            whenever(service.getPopularMovies("key", page = page))
                .thenReturn(TmdbTrendingResponseDto(pageItems))
        }

        val result = PopularRepositoryImpl(mock<Context>(), service, "key", Gson()).getPopularMovies()

        assertEquals(50, result.size)
        assertEquals((1..50).toList(), result.map { it.tmdbId })
        (1..3).forEach { page -> verify(service).getPopularMovies("key", page = page) }
        verifyNoMoreInteractions(service)
    }

    @Test
    fun getPopularSeries_returnsEmptyWithoutApiKey() = runTest {
        val service = mock<TmdbApiService>()
        val result = PopularRepositoryImpl(mock<Context>(), service, "", Gson()).getPopularSeries()

        assertTrue(result.isEmpty())
        verify(service, never()).getPopularSeries(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )
    }

    @Test
    fun getCachedMatchedMovies_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("tmdb_popular_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("movies_data_v2", null)).thenReturn("""[{"localIds":[14,15]}]""")
        val repository = PopularRepositoryImpl(context, mock(), "valid_key", Gson())

        // Premier accès du lancement : rafraîchissement forcé, le cache est ignoré.
        assertEquals(null, repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L))
        verify(prefs, never()).getString("movies_data_v2", null)

        // Accès suivants : le cache reprend son rôle normal.
        val second = repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L)
        assertEquals(listOf(14, 15), second?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedMovies_readsCacheOnFirstAccess_whenSessionRefreshIgnored() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("tmdb_popular_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("movies_data_v2", null)).thenReturn("""[{"localIds":[14]}]""")
        val repository = PopularRepositoryImpl(context, mock(), "valid_key", Gson())

        // Repli hors ligne : le cache doit rester lisible dès le premier accès.
        val result = repository.getCachedMatchedMovies(
            lastVodCatalogSyncTime = 0L,
            ignoreSessionRefresh = true
        )

        assertEquals(listOf(14), result?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedSeries_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("tmdb_popular_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("series_data_v2", null)).thenReturn("""[{"localIds":[2]}]""")
        val repository = PopularRepositoryImpl(context, mock(), "valid_key", Gson())

        assertEquals(null, repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L))

        val second = repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L)
        assertEquals(listOf(2), second?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedMovies_andSeries_haveIndependentSessionGates() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("tmdb_popular_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("series_data_v2", null)).thenReturn("""[{"localIds":[2]}]""")
        val repository = PopularRepositoryImpl(context, mock(), "valid_key", Gson())

        // Consommer la porte Films ne doit pas consommer celle des Séries.
        repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L)

        assertEquals(null, repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L))
    }
}
