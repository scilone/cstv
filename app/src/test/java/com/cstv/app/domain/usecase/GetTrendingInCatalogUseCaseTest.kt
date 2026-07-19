package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.TrendingCatalogItem
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
        whenever(trendingRepository.getCachedMatchedTrendsGlobal()).thenReturn(null) // Cache expired/null

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
        whenever(trendingRepository.getCachedMatchedTrendsGlobal()).thenReturn(null)

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

    @Test
    fun test_useCase_returnsCachedMatchedTrendsIfAvailable() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()

        val cachedList = listOf(
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(1, "Cached Interstellar", isMovie = true, year = "2014", posterUrl = "url_int"),
                matchedMovies = listOf(VodStream(30, "Interstellar", "icon", "9.5", "12345", "cat_movies")),
                matchedMovie = VodStream(30, "Interstellar", "icon", "9.5", "12345", "cat_movies")
            )
        )
        whenever(trendingRepository.getCachedMatchedTrendsGlobal()).thenReturn(cachedList)
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetTrendingInCatalogUseCase(
            trendingRepository,
            vodRepository,
            seriesRepository,
            categoryPreferenceRepository
        )

        val result = useCase()

        // Should return cache immediately without querying database or TMDB API
        assertEquals(1, result.size)
        assertEquals("Cached Interstellar", result[0].trendingTitle.title)
    }

    @Test
    fun test_useCase_selectsBestAvailableUnhiddenVersion_ifMultipleMatched() = runTest {
        val trendingRepository = mock<TrendingRepository>()
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()

        // Mock TMDB Trends (Movie: Inception)
        val trends = listOf(
            TrendingTitle(1, "Inception", isMovie = true, year = "2010", posterUrl = "url_inc")
        )
        whenever(trendingRepository.getTrending()).thenReturn(trends)
        whenever(trendingRepository.getCachedMatchedTrendsGlobal()).thenReturn(null)

        // Mock IPTV local database (Inception in both PT (hidden) and FR (unhidden) categories)
        val movies = listOf(
            VodStream(streamId = 101, name = "|PT| Inception", streamIcon = "icon", rating = "9.0", added = "12345", categoryId = "cat_pt"),
            VodStream(streamId = 102, name = "|FR| Inception", streamIcon = "icon", rating = "9.0", added = "12345", categoryId = "cat_fr")
        )
        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(movies)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(emptyList())

        // Mark PT category as hidden, FR as visible
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat_pt" to CategoryPreference("cat_pt", hidden = true, sortOrder = null))
        )

        val useCase = GetTrendingInCatalogUseCase(
            trendingRepository,
            vodRepository,
            seriesRepository,
            categoryPreferenceRepository
        )

        val result = useCase()

        // The UseCase should successfully select the unhidden FR version (streamId 102) instead of discarding the movie!
        assertEquals(1, result.size)
        val item = result[0]
        assertEquals("Inception", item.trendingTitle.title)
        assertNotNull(item.matchedMovie)
        assertEquals(102, item.matchedMovie!!.streamId)
        assertEquals("cat_fr", item.matchedMovie!!.categoryId)
    }
}
