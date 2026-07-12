package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.entity.LiveCategoryEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.dto.LiveCategoryDto
import com.poc.iptvxtream.data.remote.dto.LiveStreamDto
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.LiveStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvRepositoryImplTest {

    @Mock
    private lateinit var apiService: XtreamApiService

    @Mock
    private lateinit var liveTvDao: LiveTvDao

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    private lateinit var repository: LiveTvRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        repository = LiveTvRepositoryImpl(apiService, liveTvDao, credentialsManager)
    }

    // --- 1. PLAY URL CONSTRUCTION TESTS ---
    @Test
    fun test_playUrlConstruction_isCorrect() {
        val stream = LiveStream(
            streamId = 12345,
            name = "TF1 HD",
            streamIcon = "icon.png",
            epgChannelId = "tf1",
            num = 1,
            categoryId = "10"
        )

        // Case A: normal base URL and m3u8
        val urlA = stream.getPlayUrl("http://myprovider.com:8080", "user", "pass", "m3u8")
        assertEquals("http://myprovider.com:8080/live/user/pass/12345.m3u8", urlA)

        // Case B: trailing slash and .ts extension
        val urlB = stream.getPlayUrl("https://myprovider.com/", "user", "pass", ".ts")
        assertEquals("https://myprovider.com/live/user/pass/12345.ts", urlB)
    }

    // --- 2. CACHING & EXPIRATION LOGIC TESTS ---
    @Test
    fun test_getCategories_servedFromCache_whenNotExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        val cachedCategories = listOf(
            LiveCategoryEntity("1", "Général", 0, currentTime - 10000L) // cached 10 seconds ago (not expired)
        )

        whenever(liveTvDao.getAllCategories()).thenReturn(cachedCategories)

        val result = repository.getLiveCategories(forceRefresh = false)

        // Should return the cached categories
        assertEquals(1, result.size)
        assertEquals("1", result[0].categoryId)
        assertEquals("Général", result[0].categoryName)

        // Verify that apiService was NEVER called
        verifyNoInteractions(apiService)
    }

    @Test
    fun test_getCategories_fetchedFromNetwork_whenExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        // cached 48 hours ago (expired)
        val cachedCategories = listOf(
            LiveCategoryEntity("1", "Général", 0, currentTime - (48 * 3600 * 1000L))
        )

        whenever(liveTvDao.getAllCategories()).thenReturn(cachedCategories)
        
        val remoteCategories = listOf(
            LiveCategoryDto("1", "Général Updated", 0),
            LiveCategoryDto("2", "Sports", 0)
        )
        whenever(apiService.getLiveCategories("username", "password")).thenReturn(remoteCategories)

        val result = repository.getLiveCategories(forceRefresh = false)

        // Should return fresh fetched categories
        assertEquals(2, result.size)
        assertEquals("Général Updated", result[0].categoryName)
        assertEquals("Sports", result[1].categoryName)

        // Verify that categories cache was cleared and fresh entries were inserted
        verify(liveTvDao).clearCategories()
        verify(liveTvDao).insertCategories(any())
    }

    @Test
    fun test_getCategories_fetchedFromNetwork_whenForceRefresh() = runTest {
        val currentTime = System.currentTimeMillis()
        val cachedCategories = listOf(
            LiveCategoryEntity("1", "Général", 0, currentTime - 1000L) // not expired but we force refresh
        )

        whenever(liveTvDao.getAllCategories()).thenReturn(cachedCategories)
        
        val remoteCategories = listOf(
            LiveCategoryDto("1", "Général Updated", 0)
        )
        whenever(apiService.getLiveCategories("username", "password")).thenReturn(remoteCategories)

        val result = repository.getLiveCategories(forceRefresh = true)

        assertEquals(1, result.size)
        assertEquals("Général Updated", result[0].categoryName)

        verify(apiService).getLiveCategories("username", "password")
    }

    // --- 3. DIRTY PARSING / MAPPING TESTS ---
    @Test
    fun test_getStreams_defensivelySkipsNullIdsOrNames() = runTest {
        whenever(liveTvDao.getStreamsByCategory(any())).thenReturn(emptyList()) // cache empty

        val remoteStreamsWithDirtyData = listOf(
            LiveStreamDto(1, "TF1", "icon.png", "tf1", 1, "added_string"),
            LiveStreamDto(null, "Null ID", null, null, null, null), // dirty: null id
            LiveStreamDto(2, null, null, null, null, null)         // dirty: null name
        )
        whenever(apiService.getLiveStreams("username", "password", "10")).thenReturn(remoteStreamsWithDirtyData)

        val result = repository.getLiveStreams("10", forceRefresh = false)

        // Dirty items should be silently and defensively ignored (only 1 valid stream remains)
        assertEquals(1, result.size)
        assertEquals(1, result[0].streamId)
        assertEquals("TF1", result[0].name)
    }

    // --- 4. RECENTLY WATCHED TESTS ---
    @Test
    fun test_recentlyWatched_persistenceAndRetrieval() = runTest {
        val stream = LiveStream(
            streamId = 12345,
            name = "TF1 HD",
            streamIcon = "icon.png",
            epgChannelId = "tf1",
            num = 1,
            categoryId = "10"
        )

        // Verify save
        repository.saveRecentlyWatched(stream)
        verify(liveTvDao).insertRecentlyWatched(any())

        // Verify retrieve
        val mockedRecentlyWatched = listOf(
            com.poc.iptvxtream.data.local.entity.RecentlyWatchedLiveEntity(
                streamId = 12345,
                name = "TF1 HD",
                streamIcon = "icon.png",
                categoryId = "10",
                num = 1,
                watchedAt = System.currentTimeMillis()
            )
        )
        whenever(liveTvDao.getRecentlyWatched(any())).thenReturn(mockedRecentlyWatched)

        val result = repository.getRecentlyWatched()
        assertEquals(1, result.size)
        assertEquals(12345, result[0].streamId)
        assertEquals("TF1 HD", result[0].name)
        assertEquals("icon.png", result[0].streamIcon)
        assertEquals("10", result[0].categoryId)
        assertEquals(1, result[0].num)
    }
}
