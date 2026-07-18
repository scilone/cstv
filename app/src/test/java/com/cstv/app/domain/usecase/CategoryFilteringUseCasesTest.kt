package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.SearchResult
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
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

    // Le filtrage des catégories masquées se fait désormais AVANT le classement,
    // à l'intérieur du repository (voir VodRepositoryImpl.getRelatedMovies) : le
    // use case se contente de résoudre les catégories masquées et de les
    // transmettre, il ne post-filtre plus le résultat déjà classé/limité (un
    // post-filtrage après un sur-fetch fixe pouvait retourner moins de `limit`
    // résultats alors que des candidats visibles existaient au-delà du sur-fetch).
    @Test
    fun test_getRelatedMoviesUseCase_passesHiddenCategoriesToRepository() = runTest {
        val movies = listOf(VodStream(1, "Movie A", null, null, null, "cat_visible"))
        whenever(vodRepository.getRelatedMovies(eq(1), eq("Action"), eq(10), eq(setOf("cat_hidden")))).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat_hidden" to CategoryPreference(categoryId = "cat_hidden", hidden = true, sortOrder = 0))
        )

        val result = getRelatedMoviesUseCase(1, "Action")

        assertEquals(1, result.size)
        assertEquals(1, result[0].streamId)
        verify(vodRepository).getRelatedMovies(1, "Action", 10, setOf("cat_hidden"))
    }

    @Test
    fun test_getRelatedMoviesUseCase_categoryPreferenceThrows_passesEmptySet() = runTest {
        val movies = listOf(VodStream(1, "Movie A", null, null, null, "cat_visible"))
        whenever(vodRepository.getRelatedMovies(eq(1), eq("Action"), eq(10), eq(emptySet()))).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenThrow(RuntimeException("DB error"))

        val result = getRelatedMoviesUseCase(1, "Action")

        assertEquals(1, result.size)
        verify(vodRepository).getRelatedMovies(1, "Action", 10, emptySet())
    }

    @Test
    fun test_getRelatedSeriesUseCase_passesHiddenCategoriesToRepository() = runTest {
        val series = listOf(SeriesStream(1, "Series A", null, null, null, "cat_visible"))
        whenever(seriesRepository.getRelatedSeries(eq(1), eq("Comedy"), eq(10), eq(setOf("cat_hidden")))).thenReturn(series)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(
            mapOf("cat_hidden" to CategoryPreference(categoryId = "cat_hidden", hidden = true, sortOrder = 0))
        )

        val result = getRelatedSeriesUseCase(1, "Comedy")

        assertEquals(1, result.size)
        assertEquals(1, result[0].seriesId)
        verify(seriesRepository).getRelatedSeries(1, "Comedy", 10, setOf("cat_hidden"))
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
