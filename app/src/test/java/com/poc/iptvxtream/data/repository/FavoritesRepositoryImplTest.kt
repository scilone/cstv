package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.domain.model.FavoriteItem
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

    @Mock
    private lateinit var profileManager: ProfileManager

    private lateinit var repository: FavoritesRepositoryImpl

    private val activeProfileId = 1

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(activeProfileId).whenever(profileManager).currentProfileId()
        repository = FavoritesRepositoryImpl(favoritesDao, profileManager)
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
            assertEquals(activeProfileId, firstValue.profileId)
        }
    }

    @Test
    fun test_removeFavorite_removesEntityFromDatabase() = runTest {
        repository.removeFavorite(123, "movie")

        verify(favoritesDao).removeFavorite(123, "movie", activeProfileId)
    }

    @Test
    fun test_isFavorite_returnsTrue_whenEntityExists() = runTest {
        whenever(favoritesDao.isFavorite(123, "movie", activeProfileId)).thenReturn(true)

        val result = repository.isFavorite(123, "movie")

        assertTrue(result)
        verify(favoritesDao).isFavorite(123, "movie", activeProfileId)
    }

    // --- 2. UNIFIED SEARCH FILTERING TESTS ---
    @Test
    fun test_searchUnified_queriesAndReturnsAggregatedResults() = runTest {
        val searchQuery = "tf"
        val expectedSqlQuery = "\"tf\"*"

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

        val result = repository.searchUnified(searchQuery, null)

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
        val result = repository.searchUnified("  ", null)

        assertTrue(result.isEmpty)
        verifyNoInteractions(favoritesDao)
    }

    // --- 3. GENRE FILTER TESTS ---
    @Test
    fun test_searchUnified_withTextAndGenre_filtersByGenreAndDropsLive() = runTest {
        val matchQuery = "\"a\"*"
        val liveEntities = listOf(LiveStreamEntity(1, "Action News", null, null, 1, "10", 0L))
        val vodEntities = listOf(
            VodStreamEntity(2, "Die Hard", null, null, null, "5", 0L, genre = "Action, Thriller"),
            VodStreamEntity(3, "The Notebook", null, null, null, "6", 0L, genre = "Romance, Drame")
        )
        val seriesEntities = listOf(
            SeriesStreamEntity(4, "24", null, null, null, "12", 0L, genre = "action") // casse différente
        )
        whenever(favoritesDao.searchLiveStreams(matchQuery)).thenReturn(liveEntities)
        whenever(favoritesDao.searchVodStreams(matchQuery)).thenReturn(vodEntities)
        whenever(favoritesDao.searchSeriesStreams(matchQuery)).thenReturn(seriesEntities)

        val result = repository.searchUnified("a", "Action")

        // Live écarté quand un genre est sélectionné.
        assertTrue(result.liveResults.isEmpty())
        // Seul le film au genre "Action" est conservé.
        assertEquals(1, result.vodResults.size)
        assertEquals("Die Hard", result.vodResults[0].name)
        // Série "24" gardée malgré la casse ("action" vs "Action").
        assertEquals(1, result.seriesResults.size)
        assertEquals("24", result.seriesResults[0].name)
    }

    @Test
    fun test_searchUnified_withGenreOnly_usesGenrePrefilter() = runTest {
        val pattern = "%Comédie%"
        val vodEntities = listOf(
            VodStreamEntity(2, "Le Dîner de Cons", null, null, null, "5", 0L, genre = "Comédie"),
            // Faux positif du LIKE (préfiltre SQL) rejeté par le match exact.
            VodStreamEntity(3, "Faux", null, null, null, "6", 0L, genre = "Comédienne")
        )
        whenever(favoritesDao.getVodStreamsByGenre(pattern)).thenReturn(vodEntities)
        whenever(favoritesDao.getSeriesStreamsByGenre(pattern)).thenReturn(emptyList())

        val result = repository.searchUnified("", "Comédie")

        assertTrue(result.liveResults.isEmpty())
        assertEquals(1, result.vodResults.size)
        assertEquals("Le Dîner de Cons", result.vodResults[0].name)
        verify(favoritesDao).getVodStreamsByGenre(pattern)
        verify(favoritesDao).getSeriesStreamsByGenre(pattern)
        // Pas de recherche FTS en mode genre seul.
        verify(favoritesDao, never()).searchVodStreams(any())
    }

    @Test
    fun test_getAvailableGenres_aggregatesAndDeduplicatesAcrossMedia() = runTest {
        whenever(favoritesDao.getDistinctVodGenres()).thenReturn(listOf("Action, Thriller", "Comédie"))
        whenever(favoritesDao.getDistinctSeriesGenres()).thenReturn(listOf("action", "Drame"))

        val genres = repository.getAvailableGenres()

        // Dédupliqué (Action/action) et trié alphabétiquement.
        assertEquals(listOf("Action", "Comédie", "Drame", "Thriller"), genres)
    }
}
