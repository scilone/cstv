package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GetTrendingInCatalogUseCaseTest {

    @Mock private lateinit var trendingRepository: TrendingRepository
    @Mock private lateinit var vodRepository: VodRepository
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private lateinit var useCase: GetTrendingInCatalogUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetTrendingInCatalogUseCase(
            trendingRepository, vodRepository, seriesRepository, categoryPreferenceRepository
        )
    }

    private fun movie(id: Int, name: String, cat: String = "cat1") =
        VodStream(id, name, null, null, null, cat)

    private fun series(id: Int, name: String, cat: String = "cat1") =
        SeriesStream(id, name, null, null, null, cat)

    private fun trendingMovie(id: Int, title: String) =
        TrendingTitle(id, title, isMovie = true, year = "2020", posterUrl = "http://p/$id.jpg")

    private fun trendingTv(id: Int, title: String) =
        TrendingTitle(id, title, isMovie = false, year = "2020", posterUrl = null)

    private suspend fun noHiddenCategories() {
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
    }

    @Test
    fun emptyTrending_returnsEmpty() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(emptyList())

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun trendingRepositoryThrows_returnsEmpty() = runTest {
        whenever(trendingRepository.getTrending()).thenThrow(RuntimeException("no key"))

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun matchesDirtyMovieTitle_acrossNormalization() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trendingMovie(1, "Dragon Ball Z")))
        whenever(vodRepository.getVodStreams(any(), any()))
            .thenReturn(listOf(movie(10, "|FR| Dragon Ball Z 1080p MULTI")))
        noHiddenCategories()

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals(10, result[0].vodStream?.streamId)
        assertNull(result[0].seriesStream)
        assertEquals("http://p/1.jpg", result[0].trending.posterUrl)
    }

    @Test
    fun movieTrending_neverMatchesSeries_andViceVersa() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trendingMovie(1, "The Matrix")))
        // Titre identique mais côté séries : ne doit PAS matcher (respect isMovie).
        whenever(vodRepository.getVodStreams(any(), any())).thenReturn(emptyList())
        whenever(seriesRepository.getSeriesStreams(any(), any())).thenReturn(listOf(series(20, "The Matrix")))
        noHiddenCategories()

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun tvTrending_matchesSeries() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trendingTv(1, "Breaking Bad")))
        whenever(seriesRepository.getSeriesStreams(any(), any()))
            .thenReturn(listOf(series(30, "Breaking Bad [VOSTFR] 1080p")))
        noHiddenCategories()

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals(30, result[0].seriesStream?.seriesId)
        assertNull(result[0].vodStream)
    }

    @Test
    fun falsePositive_warDoesNotMatchWarrior() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trendingMovie(1, "War")))
        whenever(vodRepository.getVodStreams(any(), any())).thenReturn(listOf(movie(10, "Warrior")))
        noHiddenCategories()

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun excludesHiddenCategories() = runTest {
        whenever(trendingRepository.getTrending()).thenReturn(listOf(trendingMovie(1, "Inception")))
        whenever(vodRepository.getVodStreams(any(), any()))
            .thenReturn(listOf(movie(10, "Inception", cat = "hidden")))
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("hidden" to CategoryPreference("hidden", hidden = true, sortOrder = 0))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(emptyMap())

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun capsAtTenResults_inTmdbOrder() = runTest {
        // 12 tendances toutes présentes au catalogue → au plus 10, ordre TMDB.
        val trending = (1..12).map { trendingMovie(it, "Movie $it") }
        val catalog = (1..12).map { movie(100 + it, "Movie $it") }
        whenever(trendingRepository.getTrending()).thenReturn(trending)
        whenever(vodRepository.getVodStreams(any(), any())).thenReturn(catalog)
        noHiddenCategories()

        val result = useCase()

        assertEquals(10, result.size)
        assertEquals(1, result.first().trending.tmdbId)
        assertEquals(10, result.last().trending.tmdbId)
    }

    @Test
    fun dedupesSameCatalogStream() = runTest {
        // Deux tendances distinctes matchant le MÊME film → une seule entrée.
        whenever(trendingRepository.getTrending()).thenReturn(
            listOf(trendingMovie(1, "Avatar"), trendingMovie(2, "Avatar"))
        )
        whenever(vodRepository.getVodStreams(any(), any())).thenReturn(listOf(movie(50, "Avatar")))
        noHiddenCategories()

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals(50, result[0].vodStream?.streamId)
        assertEquals(1, result[0].trending.tmdbId)
    }
}
