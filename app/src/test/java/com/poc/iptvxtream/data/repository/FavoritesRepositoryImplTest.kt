package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchSuggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryImplTest {

    @Mock
    private lateinit var favoritesDao: FavoritesDao

    private lateinit var repository: FavoritesRepositoryImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = FavoritesRepositoryImpl(favoritesDao)
    }

    // --- 1. FAVORITES ADD & REMOVE TESTS ---
    @Test
    fun test_addFavorite_savesEntityToDatabase() = runTest {
        val favorite = FavoriteItem(123, "movie", "Inception", "cover.jpg", "5")

        repository.addFavorite(favorite)

        // Capture saved entity and verify mapping
        argumentCaptor<FavoriteEntity>().apply {
            verify(favoritesDao).addFavorite(capture())
            assertEquals(123, firstValue.id)
            assertEquals("movie", firstValue.type)
            assertEquals("Inception", firstValue.name)
            assertEquals("cover.jpg", firstValue.cover)
            assertEquals("5", firstValue.categoryId)
            assertTrue(firstValue.addedAt > 0L)
        }
    }

    @Test
    fun test_removeFavorite_removesEntityFromDatabase() = runTest {
        repository.removeFavorite(123, "movie")

        verify(favoritesDao).removeFavorite(123, "movie")
    }

    @Test
    fun test_isFavorite_returnsTrue_whenEntityExists() = runTest {
        whenever(favoritesDao.isFavorite(123, "movie")).thenReturn(true)

        val result = repository.isFavorite(123, "movie")

        assertTrue(result)
        verify(favoritesDao).isFavorite(123, "movie")
    }

    // --- 2. UNIFIED SEARCH FILTERING TESTS ---
    @Test
    fun test_searchUnified_queriesAndReturnsAggregatedResults() = runTest {
        val searchQuery = "tf"
        val expectedSqlQuery = "%tf%"

        // Mock database responses for Live, VOD, and Series tables
        val liveEntities = listOf(
            LiveStreamEntity(1, "TF1 HD", null, null, 1, "10", 0L)
        )
        val vodEntities = listOf(
            VodStreamEntity(2, "The Fast and the Furious", null, null, null, "5", 0L)
        )
        val seriesEntities = listOf(
            SeriesStreamEntity(3, "The Flash", null, null, null, "12", 0L)
        )

        whenever(favoritesDao.searchLiveStreams(expectedSqlQuery)).thenReturn(liveEntities)
        whenever(favoritesDao.searchVodStreams(expectedSqlQuery)).thenReturn(vodEntities)
        whenever(favoritesDao.searchSeriesStreams(expectedSqlQuery)).thenReturn(seriesEntities)

        val result = repository.searchUnified(searchQuery)

        // Verify aggregation
        assertNotNull(result)
        assertFalse(result.isEmpty)

        // Live results
        assertEquals(1, result.liveResults.size)
        assertEquals("TF1 HD", result.liveResults[0].name)

        // VOD results
        assertEquals(1, result.vodResults.size)
        assertEquals("The Fast and the Furious", result.vodResults[0].name)

        // Series results
        assertEquals(1, result.seriesResults.size)
        assertEquals("The Flash", result.seriesResults[0].name)

        verify(favoritesDao).searchLiveStreams(expectedSqlQuery)
        verify(favoritesDao).searchVodStreams(expectedSqlQuery)
        verify(favoritesDao).searchSeriesStreams(expectedSqlQuery)
    }

    @Test
    fun test_searchUnified_returnsEmptyResult_whenQueryIsBlank() = runTest {
        val result = repository.searchUnified("  ")

        assertTrue(result.isEmpty)
        verifyNoInteractions(favoritesDao)
    }

    @Test
    fun test_getSearchSuggestions_returnsCombinedAndFilteredSuggestions() = runTest {
        val query = "Lucas"
        val expectedSqlQuery = "%Lucas%"

        val liveEntity = LiveStreamEntity(1, "Lucas TV", null, null, 1, "10", 0L)
        val vodEntity = VodStreamEntity(
            streamId = 2,
            name = "Star Wars",
            streamIcon = null,
            rating = "8.5",
            added = null,
            categoryId = "15",
            cachedAt = 0L,
            actors = "Mark Hamill, Harrison Ford",
            director = "George Lucas",
            genre = "Sci-Fi"
        )
        val seriesEntity = SeriesStreamEntity(3, "Lucas Series", null, null, null, "12", 0L)

        whenever(favoritesDao.suggestLiveStreams(expectedSqlQuery, 5)).thenReturn(listOf(liveEntity))
        whenever(favoritesDao.suggestVodStreams(expectedSqlQuery, 5)).thenReturn(listOf(vodEntity))
        whenever(favoritesDao.suggestSeriesStreams(expectedSqlQuery, 5)).thenReturn(listOf(seriesEntity))

        val results = repository.getSearchSuggestions(query, limit = 5)

        assertNotNull(results)
        val terms = results.filterIsInstance<SearchSuggestion.Term>()
        assertEquals(1, terms.size)
        assertEquals("George Lucas", terms[0].term)

        val items = results.filterIsInstance<SearchSuggestion.Item>()
        assertEquals(3, items.size)
        assertTrue(items.any { it.name == "Lucas TV" })
        assertTrue(items.any { it.name == "Star Wars" })
        assertTrue(items.any { it.name == "Lucas Series" })

        verify(favoritesDao).suggestLiveStreams(expectedSqlQuery, 5)
        verify(favoritesDao).suggestVodStreams(expectedSqlQuery, 5)
        verify(favoritesDao).suggestSeriesStreams(expectedSqlQuery, 5)
    }

    @Test
    fun test_getSearchSuggestions_termsDoNotCrowdOutItems() = runTest {
        // Une requête matchant de nombreux acteurs/réalisateurs ne doit pas
        // remplir toute la limite de termes : les items (fiches précises)
        // doivent conserver au moins la moitié des emplacements.
        val query = "a"
        val expectedSqlQuery = "%a%"
        val limit = 6

        // 6 films, chacun avec un acteur distinct contenant "a" -> 6 termes potentiels.
        val vodEntities = (1..6).map { i ->
            VodStreamEntity(
                streamId = i,
                name = "Film $i",
                streamIcon = null,
                rating = null,
                added = null,
                categoryId = "1",
                cachedAt = 0L,
                actors = "Actor$i Alpha",
                director = null,
                genre = null
            )
        }

        whenever(favoritesDao.suggestLiveStreams(expectedSqlQuery, limit)).thenReturn(emptyList())
        whenever(favoritesDao.suggestVodStreams(expectedSqlQuery, limit)).thenReturn(vodEntities)
        whenever(favoritesDao.suggestSeriesStreams(expectedSqlQuery, limit)).thenReturn(emptyList())

        val results = repository.getSearchSuggestions(query, limit = limit)

        val terms = results.filterIsInstance<SearchSuggestion.Term>()
        val items = results.filterIsInstance<SearchSuggestion.Item>()

        // Total borné par la limite.
        assertEquals(limit, results.size)
        // Les termes sont plafonnés (limit / 2), laissant de la place aux items.
        assertTrue("Les termes ne doivent pas dépasser la moitié", terms.size <= limit / 2)
        assertTrue("Au moins un item (film) doit rester visible", items.isNotEmpty())
    }
}
