package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetTrendingInCatalogUseCaseTest {

    @Test
    fun test_useCase_matchesTrendingItemsSuccessfully() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()

        // Mock TMDB Trends
        val trends = listOf(
            TrendingTitle(1, "Inception", isMovie = true, year = "2010", posterUrl = "url_inc"),
            TrendingTitle(2, "Breaking Bad", isMovie = false, year = "2008", posterUrl = "url_bb"),
            TrendingTitle(3, "Interstellar", isMovie = true, year = "2014", posterUrl = "url_int") // Not in IPTV catalog
        )
        whenever(trendingRepository.getTrending()).thenReturn(trends)

        // Mock IPTV local database
        val movies = listOf(
            VodStream(streamId = 10, name = "[FR] Inception 1080p", streamIcon = "icon", rating = "9.0", added = "12345", categoryId = "cat_movies"),
            VodStream(streamId = 11, name = "Gladiator", streamIcon = "icon", rating = "8.0", added = "12345", categoryId = "cat_movies")
        )
        val series = listOf(
            SeriesStream(seriesId = 20, name = "Breaking Bad Complete", cover = "cover", rating = "9.0", added = "12345", categoryId = "cat_series")
        )
        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(movies)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(series)

        // Mock preferences (no hidden categories)
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetTrendingInCatalogUseCase(
            trendingRepository,
            vodRepository,
            seriesRepository,
            categoryPreferenceRepository
        )

        val result = useCase()

        // Should match "Inception" -> streamId 10, and "Breaking Bad" -> seriesId 20. "Interstellar" is skipped.
        assertEquals(2, result.size)

        val item1 = result[0]
        assertEquals("Inception", item1.trendingTitle.title)
        assertNotNull(item1.matchedMovie)
        assertEquals(10, item1.matchedMovie!!.streamId)

        val item2 = result[1]
        assertEquals("Breaking Bad", item2.trendingTitle.title)
        assertNotNull(item2.matchedSeries)
        assertEquals(20, item2.matchedSeries!!.seriesId)
    }

    @Test
    fun test_useCase_filtersOutHiddenCategories() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()

        val trends = listOf(
            TrendingTitle(1, "Inception", isMovie = true, year = "2010", posterUrl = "url_inc")
        )
        whenever(trendingRepository.getTrending()).thenReturn(trends)

        val movies = listOf(
            VodStream(streamId = 10, name = "Inception", streamIcon = "icon", rating = "9.0", added = "12345", categoryId = "hidden_category")
        )
        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(movies)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(emptyList())

        // "hidden_category" is marked as hidden
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("hidden_category" to CategoryPreference("hidden_category", hidden = true, sortOrder = null))
        )

        val useCase = GetTrendingInCatalogUseCase(
            trendingRepository,
            vodRepository,
            seriesRepository,
            categoryPreferenceRepository
        )

        val result = useCase()

        // Since the matched movie belongs to a hidden category, the result must be empty
        assertTrue(result.isEmpty())
    }
}
