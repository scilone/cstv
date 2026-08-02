package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.FavoriteItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


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
        val searchQuery = "t"
        val expectedSqlQuery = "%t%"

        // Mock database responses for Live, VOD, and Series tables
        val liveEntities = listOf(
            LiveStreamEntity(1, "TF1 HD", null, null, 1, "10", 0L, searchText = "tf1 hd\n10")
        )
        val vodEntities = listOf(
            VodStreamEntity(
                2, "The Fast and the Furious", null, null, null, "5", 0L,
                actors = "Vin Diesel", director = "Rob Cohen", genre = "Action", releaseYear = 2001, searchText = "the fast and the furious\nvin diesel\nrob cohen\naction\n5"
            )
        )
        val seriesEntities = listOf(
            SeriesStreamEntity(
                3, "The Flash", null, null, null, "12", 0L,
                actors = "Grant Gustin", director = "Greg Berlanti", genre = "Superhero", releaseYear = 2014, searchText = "the flash\ngrant gustin\ngreg berlanti\nsuperhero\n12"
            )
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
        assertEquals("Vin Diesel", result.vodResults[0].actors)
        assertEquals("Rob Cohen", result.vodResults[0].director)
        assertEquals("Action", result.vodResults[0].genre)
        assertEquals(2001, result.vodResults[0].releaseYear)

        // Series results
        assertEquals(1, result.seriesResults.size)
        assertEquals("The Flash", result.seriesResults[0].name)
        assertEquals("Grant Gustin", result.seriesResults[0].actors)
        assertEquals("Greg Berlanti", result.seriesResults[0].director)
        assertEquals("Superhero", result.seriesResults[0].genre)
        assertEquals(2014, result.seriesResults[0].releaseYear)

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
    fun test_searchUnified_keepsInvalidReleaseYearAsNull() = runTest {
        val expectedSqlQuery = "%movie%"
        whenever(favoritesDao.searchLiveStreams(expectedSqlQuery)).thenReturn(emptyList())
        whenever(favoritesDao.searchVodStreams(expectedSqlQuery)).thenReturn(
            listOf(VodStreamEntity(2, "Movie", null, null, null, "5", 0L, releaseYear = 0, searchText = "movie\n\n\n\n5"))
        )
        whenever(favoritesDao.searchSeriesStreams(expectedSqlQuery)).thenReturn(
            listOf(SeriesStreamEntity(3, "Series", null, null, null, "12", 0L, releaseYear = -1, searchText = "movie\n\n\n\n12"))
        )

        val result = repository.searchUnified("movie")

        assertNull(result.vodResults.single().releaseYear)
        assertNull(result.seriesResults.single().releaseYear)
    }
    @Test
    fun searchUnifiedUsesLongestTokenAndFiltersRemainingTokens() = runTest {
        val expectedSqlQuery = "%jean%"
        val matching = VodStreamEntity(
            1, "Film", null, null, null, "5", 0L,
            actors = "Jean Reno", searchText = "film\njean reno\n\n\n5"
        )
        val sqlOnlyMatch = VodStreamEntity(
            2, "Autre", null, null, null, "5", 0L,
            actors = "Jean Dujardin", searchText = "autre\njean dujardin\n\n\n5"
        )
        whenever(favoritesDao.searchLiveStreams(expectedSqlQuery)).thenReturn(emptyList())
        whenever(favoritesDao.searchVodStreams(expectedSqlQuery)).thenReturn(listOf(matching, sqlOnlyMatch))
        whenever(favoritesDao.searchSeriesStreams(expectedSqlQuery)).thenReturn(emptyList())

        val result = repository.searchUnified("jean reno")

        assertEquals(listOf(1), result.vodResults.map { it.streamId })
        verify(favoritesDao).searchVodStreams(expectedSqlQuery)
    }
}
