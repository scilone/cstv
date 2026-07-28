package com.cstv.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.data.remote.dto.TmdbTrendingItemDto
import com.cstv.app.data.remote.dto.TmdbTrendingResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TrendingRepositoryImplTest {

    @Test
    fun test_getTrending_returnsEmptyList_ifApiKeyIsBlank() = runTest {
        val context = mock<Context>()
        val apiService = mock<TmdbApiService>()
        val gson = Gson()
        val repository = TrendingRepositoryImpl(context, apiService, apiKey = "", gson)

        val result = repository.getTrending()

        assertTrue(result.isEmpty())
        verify(apiService, times(0)).getTrending(any(), any())
    }

    @Test
    fun test_getTrending_returnsMappedTitles_onApiSuccess() = runTest {
        val context = mock<Context>()
        val apiService = mock<TmdbApiService>()
        val gson = Gson()
        val mockResponse = TmdbTrendingResponseDto(
            results = listOf(
                TmdbTrendingItemDto(
                    id = 101,
                    title = "Inception",
                    name = null,
                    mediaType = "movie",
                    posterPath = "/inception.jpg",
                    releaseDate = "2010-07-16",
                    firstAirDate = null
                ),
                TmdbTrendingItemDto(
                    id = "202",
                    title = null,
                    name = "Breaking Bad",
                    mediaType = "tv",
                    posterPath = "/breakingbad.jpg",
                    releaseDate = null,
                    firstAirDate = "2008-01-20"
                )
            )
        )
        whenever(apiService.getTrending("valid_key")).thenReturn(mockResponse)

        val repository = TrendingRepositoryImpl(context, apiService, apiKey = "valid_key", gson)

        val result = repository.getTrending()

        assertEquals(2, result.size)
        
        val movie = result[0]
        assertEquals(101, movie.tmdbId)
        assertEquals("Inception", movie.title)
        assertTrue(movie.isMovie)
        assertEquals(2010, movie.year)
        assertEquals("https://image.tmdb.org/t/p/w500/inception.jpg", movie.posterUrl)

        val tv = result[1]
        assertEquals(202, tv.tmdbId)
        assertEquals("Breaking Bad", tv.title)
        assertFalse(tv.isMovie)
        assertEquals(2008, tv.year)
        assertEquals("https://image.tmdb.org/t/p/w500/breakingbad.jpg", tv.posterUrl)
    }

    @Test
    fun test_getTrending_mapsBackdropPathToLandscapeUrl() = runTest {
        val context = mock<Context>()
        val apiService = mock<TmdbApiService>()
        whenever(apiService.getTrending("valid_key")).thenReturn(
            TmdbTrendingResponseDto(
                results = listOf(
                    TmdbTrendingItemDto(
                        id = 101,
                        title = "Inception",
                        name = null,
                        mediaType = "movie",
                        posterPath = "/inception.jpg",
                        releaseDate = "2010-07-16",
                        firstAirDate = null,
                        backdropPath = "/inception_backdrop.jpg"
                    ),
                    TmdbTrendingItemDto(
                        id = 202,
                        title = "No Backdrop",
                        name = null,
                        mediaType = "movie",
                        posterPath = "/poster.jpg",
                        releaseDate = "2011-07-16",
                        firstAirDate = null,
                        backdropPath = ""
                    )
                )
            )
        )

        val result = TrendingRepositoryImpl(context, apiService, "valid_key", Gson()).getTrending()

        assertEquals("https://image.tmdb.org/t/p/w780/inception_backdrop.jpg", result[0].backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/inception_backdrop.jpg", result[0].landscapeImageUrl())
        assertEquals(null, result[1].backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", result[1].landscapeImageUrl())
    }

    @Test
    fun test_getTrending_returnsEmptyList_onApiFailure() = runTest {
        val context = mock<Context>()
        val apiService = mock<TmdbApiService>()
        val gson = Gson()
        whenever(apiService.getTrending("valid_key")).thenThrow(RuntimeException("API Error"))

        val repository = TrendingRepositoryImpl(context, apiService, apiKey = "valid_key", gson)

        val result = repository.getTrending()

        assertTrue(result.isEmpty())
    }

    @Test
    fun test_getTrending_mapsMalformedDatesToNull() = runTest {
        val context = mock<Context>()
        val apiService = mock<TmdbApiService>()
        whenever(apiService.getTrending("valid_key")).thenReturn(
            TmdbTrendingResponseDto(
                results = listOf(
                    TmdbTrendingItemDto(
                        id = 101,
                        title = "Untitled",
                        name = null,
                        mediaType = "movie",
                        posterPath = null,
                        releaseDate = "TBD",
                        firstAirDate = null
                    )
                )
            )
        )

        val result = TrendingRepositoryImpl(context, apiService, "valid_key", Gson()).getTrending()

        assertEquals(1, result.size)
        assertEquals(null, result.single().year)
    }

    @Test
    fun test_getCachedMatchedTrendsGlobal_doesNotReadV2Cache() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("tmdb_trends_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(0L)
        val repository = TrendingRepositoryImpl(context, mock(), "valid_key", Gson())

        val result = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = 0L,
            ignoreSessionRefresh = true
        )

        assertEquals(null, result)
        verify(prefs).getLong("trends_time_global_v3", 0L)
        verify(prefs, never()).getLong("trends_time_global_v2", 0L)
    }

    @Test
    fun test_getCachedMatchedTrendsGlobal_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val json = """[{"trendingTitle":{"tmdbId":1,"title":"Dune","isMovie":true,"year":2021}}]"""
        whenever(context.getSharedPreferences("tmdb_trends_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(context, mock(), "valid_key", Gson())

        // Premier accès du lancement : rafraîchissement forcé, le cache est ignoré.
        assertEquals(null, repository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime = 0L))
        verify(prefs, never()).getString("trends_data_global_v3", null)

        // Accès suivants : le cache reprend son rôle normal.
        val second = repository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime = 0L)
        assertEquals(1, second?.size)
        assertEquals("Dune", second?.single()?.trendingTitle?.title)
    }

    @Test
    fun test_getCachedMatchedTrendsGlobal_readsCacheOnFirstAccess_whenSessionRefreshIgnored() = runTest {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val json = """[{"trendingTitle":{"tmdbId":1,"title":"Dune","isMovie":true,"year":2021}}]"""
        whenever(context.getSharedPreferences("tmdb_trends_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(context, mock(), "valid_key", Gson())

        // Repli hors ligne : le cache doit rester lisible dès le premier accès.
        val result = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = 0L,
            ignoreSessionRefresh = true
        )

        assertEquals(1, result?.size)
    }
}
