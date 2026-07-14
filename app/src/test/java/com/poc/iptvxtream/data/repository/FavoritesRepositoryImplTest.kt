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
}
