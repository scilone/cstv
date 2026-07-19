package com.cstv.app.data.repository

import android.content.Context
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
        assertEquals("2010", movie.year)
        assertEquals("https://image.tmdb.org/t/p/w500/inception.jpg", movie.posterUrl)

        val tv = result[1]
        assertEquals(202, tv.tmdbId)
        assertEquals("Breaking Bad", tv.title)
        assertFalse(tv.isMovie)
        assertEquals("2008", tv.year)
        assertEquals("https://image.tmdb.org/t/p/w500/breakingbad.jpg", tv.posterUrl)
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
}
