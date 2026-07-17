package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.CategoryPreference
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFilteringUseCasesTest {

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var favoritesRepository: FavoritesRepository

    @Mock
    private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private lateinit var getRelatedMoviesUseCase: GetRelatedMoviesUseCase
    private lateinit var getRelatedSeriesUseCase: GetRelatedSeriesUseCase
    private lateinit var searchUnifiedUseCase: SearchUnifiedUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        getRelatedMoviesUseCase = GetRelatedMoviesUseCase(vodRepository, categoryPreferenceRepository)
        getRelatedSeriesUseCase = GetRelatedSeriesUseCase(seriesRepository, categoryPreferenceRepository)
        searchUnifiedUseCase = SearchUnifiedUseCase(favoritesRepository, categoryPreferenceRepository)
    }

    @Test
    fun test_getRelatedMoviesUseCase_filtersHiddenCategories() = runTest {
        val movies = listOf(
            VodStream(1, "Movie A", null, null, null, "cat_visible"),
            VodStream(2, "Movie B", null, null, null, "cat_hidden")
        )
        whenever(vodRepository.getRelatedMovies(any(), anyOrNull(), any())).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat_hidden" to CategoryPreference(categoryId = "cat_hidden", hidden = true, sortOrder = 0))
        )

        val result = getRelatedMoviesUseCase(1, "Action")
        assertEquals(1, result.size)
        assertEquals(1, result[0].streamId)
    }

    @Test
    fun test_getRelatedSeriesUseCase_filtersHiddenCategories() = runTest {
        val series = listOf(
            SeriesStream(1, "Series A", null, null, null, "cat_visible"),
            SeriesStream(2, "Series B", null, null, null, "cat_hidden")
        )
        whenever(seriesRepository.getRelatedSeries(any(), anyOrNull(), any())).thenReturn(series)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(
            mapOf("cat_hidden" to CategoryPreference(categoryId = "cat_hidden", hidden = true, sortOrder = 0))
        )

        val result = getRelatedSeriesUseCase(1, "Comedy")
        assertEquals(1, result.size)
        assertEquals(1, result[0].seriesId)
    }

    @Test
    fun test_searchUnifiedUseCase_filtersHiddenCategoriesAcrossAllTypes() = runTest {
        val initialResult = SearchResult(
            liveResults = listOf(
                LiveStream(1, "TV A", null, null, 1, "live_visible"),
                LiveStream(2, "TV B", null, null, 2, "live_hidden")
            ),
            vodResults = listOf(
                VodStream(3, "Movie A", null, null, null, "vod_visible"),
                VodStream(4, "Movie B", null, null, null, "vod_hidden")
            ),
            seriesResults = listOf(
                SeriesStream(5, "Series A", null, null, null, "series_visible"),
                SeriesStream(6, "Series B", null, null, null, "series_hidden")
            )
        )
        whenever(favoritesRepository.searchUnified(any())).thenReturn(initialResult)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf("live_hidden" to CategoryPreference(categoryId = "live_hidden", hidden = true, sortOrder = 0))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("vod_hidden" to CategoryPreference(categoryId = "vod_hidden", hidden = true, sortOrder = 0))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(
            mapOf("series_hidden" to CategoryPreference(categoryId = "series_hidden", hidden = true, sortOrder = 0))
        )

        val result = searchUnifiedUseCase("test")
        assertEquals(1, result.liveResults.size)
        assertEquals(1, result.liveResults[0].streamId)

        assertEquals(1, result.vodResults.size)
        assertEquals(3, result.vodResults[0].streamId)

        assertEquals(1, result.seriesResults.size)
        assertEquals(5, result.seriesResults[0].seriesId)
    }
}
