package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.entity.LiveCategoryEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.dto.EpgResponseDto
import com.cstv.app.data.remote.dto.EpgListingDto
import com.google.gson.JsonPrimitive
import com.cstv.app.data.remote.dto.LiveCategoryDto
import com.cstv.app.data.remote.dto.LiveStreamDto
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.LiveStream
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

    @Mock
    private lateinit var profileManager: ProfileManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var networkMonitor: com.cstv.app.domain.network.NetworkMonitor

    private lateinit var repository: LiveTvRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        doReturn(1).whenever(profileManager).currentProfileId()
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        repository = LiveTvRepositoryImpl(apiService, liveTvDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor)
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

        val result = repository.getCachedLiveCategories()

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

        val result = repository.syncLiveCategories()

        // Should return fresh fetched categories
        assertEquals(2, result.size)
        assertEquals("Général Updated", result[0].categoryName)
        assertEquals("Sports", result[1].categoryName)

        // Verify that categories cache was cleared and fresh entries were inserted
        verify(liveTvDao).replaceAllCategories(any())
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

        val result = repository.syncLiveCategories()

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

        val result = repository.syncLiveStreams("10")

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
            com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity(
                streamId = 12345,
                profileId = 1,
                name = "TF1 HD",
                streamIcon = "icon.png",
                categoryId = "10",
                num = 1,
                watchedAt = System.currentTimeMillis()
            )
        )
        whenever(liveTvDao.getRecentlyWatched(any(), any())).thenReturn(mockedRecentlyWatched)

        val result = repository.getRecentlyWatched()
        assertEquals(1, result.size)
        assertEquals(12345, result[0].streamId)
        assertEquals("TF1 HD", result[0].name)
        assertEquals("icon.png", result[0].streamIcon)
        assertEquals("10", result[0].categoryId)
        assertEquals(1, result[0].num)
    }

    @Test
    fun test_getLiveEpg_success_and_decodesBase64() = runTest {
        val streamId = 12345
        val base64Title = "VGVzdCBQcm9ncmFtbWU=" // "Test Programme"
        val base64Desc = "VGVzdCBEZXNjcmlwdGlvbg==" // "Test Description"
        
        val nowSec = System.currentTimeMillis() / 1000L
        val startSec = nowSec - 1800L // started 30 mins ago
        val endSec = nowSec + 1800L   // ends in 30 mins

        val response = EpgResponseDto(
            epgListings = listOf(
                EpgListingDto(
                    title = base64Title,
                    description = base64Desc,
                    start = null,
                    end = null,
                    startTimestamp = JsonPrimitive(startSec),
                    endTimestamp = JsonPrimitive(endSec)
                )
            )
        )

        whenever(liveTvDao.getEpgNow(eq(streamId), any())).thenReturn(null)
        whenever(apiService.getShortEpg(any(), any(), eq(streamId), any())).thenReturn(response)

        val program = repository.getLiveEpg(streamId, forceRefresh = false)

        assertNotNull(program)
        assertEquals("Test Programme", program!!.title)
        assertEquals("Test Description", program.description)
        assertEquals(startSec, program.startTimestamp)
        assertEquals(endSec, program.endTimestamp)

        // Verify it was saved to local cache
        verify(liveTvDao).replaceEpgForStream(eq(streamId), any())
    }
}
