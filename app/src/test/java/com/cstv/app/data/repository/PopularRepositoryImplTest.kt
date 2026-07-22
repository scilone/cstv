package com.cstv.app.data.repository

import android.content.Context
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
}
