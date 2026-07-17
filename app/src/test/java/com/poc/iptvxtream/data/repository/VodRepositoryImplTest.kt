package com.poc.iptvxtream.data.repository

import com.google.gson.JsonPrimitive
import com.google.gson.JsonArray
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.VodCategoryEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.dto.*
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.VodStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VodRepositoryImplTest {

    @Mock
    private lateinit var apiService: XtreamApiService

    @Mock
    private lateinit var vodDao: VodDao

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var profileManager: ProfileManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    private lateinit var repository: VodRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)
    private val activeProfileId = 1

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        doReturn(activeProfileId).whenever(profileManager).currentProfileId()
        doReturn(0L).whenever(settingsManager).getVodAllStreamsSyncedAt()
        repository = VodRepositoryImpl(apiService, vodDao, credentialsManager, profileManager, com.poc.iptvxtream.data.remote.api.XtreamRequestGate(), settingsManager)
    }

    // --- 1. PLAY URL CONSTRUCTION TESTS ---
    @Test
    fun test_vodPlayUrlConstruction_isCorrect() {
        val movie = VodStream(
            streamId = 999,
            name = "Inception",
            streamIcon = "inception.jpg",
            rating = "8.8",
            added = "123456",
            categoryId = "5"
        )

        // Case A: normal base URL and mp4 extension
        val urlA = movie.getPlayUrl("http://myprovider.com:8080", "user", "pass", "mp4")
        assertEquals("http://myprovider.com:8080/movie/user/pass/999.mp4", urlA)

        // Case B: trailing slash and mkv extension
        val urlB = movie.getPlayUrl("https://myprovider.com/", "user", "pass", ".mkv")
        assertEquals("https://myprovider.com/movie/user/pass/999.mkv", urlB)
    }

    // --- 2. CACHING & EXPIRATION LOGIC TESTS ---
    @Test
    fun test_getVodCategories_servedFromCache_whenNotExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        val cachedCategories = listOf(
            VodCategoryEntity("1", "Action", 0, currentTime - 5000L) // cached 5s ago (not expired)
        )

        whenever(vodDao.getAllCategories()).thenReturn(cachedCategories)

        val result = repository.getVodCategories(forceRefresh = false)

        assertEquals(1, result.size)
        assertEquals("1", result[0].categoryId)
        assertEquals("Action", result[0].categoryName)

        verifyNoInteractions(apiService)
    }

    @Test
    fun test_getVodCategories_fetchedFromNetwork_whenExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        // cached 48 hours ago (expired)
        val cachedCategories = listOf(
            VodCategoryEntity("1", "Action", 0, currentTime - (48 * 3600 * 1000L))
        )

        whenever(vodDao.getAllCategories()).thenReturn(cachedCategories)

        val remoteCategories = listOf(
            VodCategoryDto("1", "Action Updated", 0),
            VodCategoryDto("2", "Sci-Fi", 0)
        )
        whenever(apiService.getVodCategories("username", "password")).thenReturn(remoteCategories)

        val result = repository.getVodCategories(forceRefresh = false)

        assertEquals(2, result.size)
        assertEquals("Action Updated", result[0].categoryName)
        assertEquals("Sci-Fi", result[1].categoryName)

        verify(vodDao).clearCategories()
        verify(vodDao).insertCategories(any())
    }

    // --- 3. DIRTY PARSING / MAPPING TESTS ---
    @Test
    fun test_getVodStreams_defensivelySkipsNullIdsOrNames() = runTest {
        whenever(vodDao.getStreamsByCategory(any())).thenReturn(emptyList())

        val remoteStreamsWithDirtyData = listOf(
            VodStreamDto(999, "Inception", "cover.jpg", "8.8", "added_str", "5"),
            VodStreamDto(null, "Null ID Film", null, null, null, null), // dirty: null ID
            VodStreamDto(1000, null, null, null, null, null)            // dirty: null name
        )
        whenever(apiService.getVodStreams("username", "password", "5")).thenReturn(remoteStreamsWithDirtyData)

        val result = repository.getVodStreams("5", forceRefresh = false)

        // Slices out dirty inputs defensively (only 1 valid film remains)
        assertEquals(1, result.size)
        assertEquals(999, result[0].streamId)
        assertEquals("Inception", result[0].name)
    }

    // --- 4. DETAILED VOD INFO & RESUME PERSISTENCE TESTS ---
    @Test
    fun test_getVodDetails_returnsFullDetailsWithSavedPlaybackPosition() = runTest {
        val infoDto = VodInfoDto(
            name = "Inception",
            director = "Christopher Nolan",
            actors = JsonPrimitive("Leonardo DiCaprio, Elliot Page"),
            cast = null,
            releaseDate = "2010",
            genre = "Sci-Fi",
            plot = "A thief who steals corporate secrets through the use of dream-sharing technology.",
            rating = "8.8",
            rating5 = "4.4",
            coverBig = "inception_big.jpg",
            movieImage = "inception_img.jpg",
            duration = JsonPrimitive("6120") // 6120s = 102m = 1h 42min
        )
        val movieDataDto = VodMovieDataDto(999, "mkv")
        val remoteResponse = VodInfoResponseDto(infoDto, movieDataDto)

        whenever(apiService.getVodInfo("username", "password", 999)).thenReturn(remoteResponse)

        // Mock saved resume position in Room DB (e.g., played up to 45m 30s)
        val savedPosition = PlaybackPositionEntity(999, activeProfileId, 2730000L, 9000000L, System.currentTimeMillis())
        whenever(vodDao.getPlaybackPosition(999, activeProfileId)).thenReturn(savedPosition)

        val result = repository.getVodDetails(999)

        assertNotNull(result)
        assertEquals(999, result.streamId)
        assertEquals("Inception", result.name)
        assertEquals("Christopher Nolan", result.director)
        assertEquals("Leonardo DiCaprio, Elliot Page", result.actors)
        assertEquals("2010", result.releaseDate)
        assertEquals("Sci-Fi", result.genre)
        assertEquals("mkv", result.containerExtension)
        assertEquals("1h 42min", result.duration)
        
        // Sensationally verify that resume positions are correctly retrieved and adjoined!
        assertEquals(2730000L, result.resumePositionMs)
        assertEquals(9000000L, result.durationMs)
    }

    @Test
    fun test_getVodDetails_defensiveAndPhase10Parsing() = runTest {
        // Prepare some extremely dirty mock data:
        // - actors is a JSON array
        // - cast is a comma-separated string containing "Inconnu" and duplicates
        // - duration is in minutes instead of seconds (e.g. 102)
        // - rating is 7.85 (should round to 7.9)
        val actorsArray = JsonArray().apply {
            add("Leonardo DiCaprio")
            add("Elliot Page")
        }
        val infoDto = VodInfoDto(
            name = "Inception",
            director = "Christopher Nolan",
            actors = actorsArray,
            cast = JsonPrimitive("Leonardo DiCaprio, Tom Hardy, Inconnu"),
            releaseDate = "2010",
            genre = "Sci-Fi",
            plot = "A thief.",
            rating = "7.85",
            rating5 = null,
            coverBig = null,
            movieImage = null,
            duration = JsonPrimitive("102") // 102 minutes -> should map to 1h 42min as well!
        )
        val movieDataDto = VodMovieDataDto(999, "mp4")
        val remoteResponse = VodInfoResponseDto(infoDto, movieDataDto)

        whenever(apiService.getVodInfo("username", "password", 999)).thenReturn(remoteResponse)
        whenever(vodDao.getPlaybackPosition(999, activeProfileId)).thenReturn(null)

        val result = repository.getVodDetails(999)

        assertNotNull(result)
        // Leonardo DiCaprio, Elliot Page, Tom Hardy (deduplicated, "Inconnu" stripped out)
        assertEquals("Leonardo DiCaprio, Elliot Page, Tom Hardy", result.actors)
        assertEquals("7.9", result.rating)
        assertEquals("1h 42min", result.duration)

        // Verify alternative rating with 8 -> 8.0 rounding
        val infoDto2 = infoDto.copy(rating = "8", duration = JsonPrimitive("45")) // 45m -> "45min"
        val remoteResponse2 = VodInfoResponseDto(infoDto2, movieDataDto)
        whenever(apiService.getVodInfo("username", "password", 999)).thenReturn(remoteResponse2)
        val result2 = repository.getVodDetails(999)
        assertEquals("8.0", result2.rating)
        assertEquals("45min", result2.duration)

        // Verify colon-separated duration formatting (e.g. "01:42:00" -> "1h 42min")
        val infoDto3 = infoDto.copy(duration = JsonPrimitive("01:42:00"))
        val remoteResponse3 = VodInfoResponseDto(infoDto3, movieDataDto)
        whenever(apiService.getVodInfo("username", "password", 999)).thenReturn(remoteResponse3)
        val result3 = repository.getVodDetails(999)
        assertEquals("1h 42min", result3.duration)
    }

    @Test
    fun test_savePlaybackPosition_preservesExistingData_whenNewValuesAreNull() = runTest {
        // Mock existing entity in DB
        val existingEntity = PlaybackPositionEntity(
            streamId = 123,
            profileId = activeProfileId,
            positionMs = 1000L,
            durationMs = 5000L,
            lastAccessedAt = 100L,
            title = "Breaking Bad S1E1",
            coverUrl = "bb_cover.jpg",
            type = "series"
        )
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(existingEntity)

        // Save position with nulls (resuming from Home)
        repository.savePlaybackPosition(
            streamId = 123,
            positionMs = 2000L,
            durationMs = 5000L,
            title = null,
            coverUrl = null,
            type = null,
            containerExtension = null,
            seriesId = null,
            episodeNum = null,
            seasonNum = null,
            plot = null,
            duration = null,
            releaseDate = null
        )

        // Sensationally verify that it loaded existing, merged them, and saved correctly!
        verify(vodDao).getPlaybackPosition(123, activeProfileId)
        verify(vodDao).savePlaybackPosition(argThat {
            streamId == 123 &&
            profileId == activeProfileId &&
            positionMs == 2000L &&
            durationMs == 5000L &&
            title == "Breaking Bad S1E1" &&
            coverUrl == "bb_cover.jpg" &&
            type == "series"
        })
    }

    @Test
    fun test_backgroundEnrichment_triggersAndSavesDetails() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = VodRepositoryImpl(apiService, vodDao, credentialsManager, profileManager, com.poc.iptvxtream.data.remote.api.XtreamRequestGate(), settingsManager, testDispatcher)

        val remoteStreams = listOf(
            VodStreamDto(12, "Star Wars", "icon.png", "8.0", "added", "5")
        )
        whenever(apiService.getVodStreams("username", "password", "5")).thenReturn(remoteStreams)
        whenever(vodDao.getStreamsByCategory("5")).thenReturn(emptyList())

        val unenrichedEntity = VodStreamEntity(12, "Star Wars", "icon.png", "8.0", "added", "5", System.currentTimeMillis())
        whenever(vodDao.getStreamsNeedingEnrichment(any())).thenReturn(listOf(unenrichedEntity))

        val infoResponse = VodInfoResponseDto(
            info = VodInfoDto(
                name = "Star Wars",
                director = "George Lucas",
                actors = JsonPrimitive("Mark Hamill, Harrison Ford"),
                cast = null,
                releaseDate = "1977",
                genre = "Sci-Fi",
                plot = "Space opera.",
                rating = "8.0",
                rating5 = null,
                coverBig = null,
                movieImage = null,
                duration = null
            ),
            movieData = null
        )
        whenever(apiService.getVodInfo("username", "password", 12)).thenReturn(infoResponse)
        whenever(vodDao.getStreamById(12)).thenReturn(unenrichedEntity)

        localRepository.getVodStreams("5", forceRefresh = true)

        verify(vodDao).insertStreamsWithFts(argThat {
            size == 1 && get(0).streamId == 12 && get(0).actors == "Mark Hamill, Harrison Ford" && get(0).director == "George Lucas" && get(0).genre == "Sci-Fi"
        })
    }

    @Test
    fun test_backgroundEnrichment_requestsBoundedBatch() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = VodRepositoryImpl(apiService, vodDao, credentialsManager, profileManager, com.poc.iptvxtream.data.remote.api.XtreamRequestGate(), settingsManager, testDispatcher)

        whenever(apiService.getVodStreams("username", "password", "5")).thenReturn(emptyList())
        whenever(vodDao.getStreamsByCategory("5")).thenReturn(emptyList())
        whenever(vodDao.getStreamsNeedingEnrichment(any())).thenReturn(emptyList())

        localRepository.getVodStreams("5", forceRefresh = true)

        // Le balayage d'enrichissement doit demander un lot borné (LIMIT SQL),
        // jamais l'intégralité du catalogue non enrichi d'un coup.
        val limitCaptor = argumentCaptor<Int>()
        verify(vodDao).getStreamsNeedingEnrichment(limitCaptor.capture())
        assertTrue(limitCaptor.firstValue in 1..50)
    }

    // --- 5. enrichPendingMovies (sync forcé/planifié, Phase 22) ---

    private fun unenrichedEntity(id: Int) = VodStreamEntity(
        streamId = id, name = "Film $id", streamIcon = null, rating = null,
        added = null, categoryId = "1", cachedAt = 0L
    )

    private val genericInfoResponse = VodInfoResponseDto(
        VodInfoDto(
            name = "Titre", director = "Un réalisateur",
            actors = JsonPrimitive("Un acteur"), cast = null,
            releaseDate = null, genre = "Drame", plot = null,
            rating = null, rating5 = null, coverBig = null, movieImage = null,
            duration = null
        ),
        movieData = null
    )

    @Test
    fun test_enrichPendingMovies_returnsZero_whenNoCredentials() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(null)

        val result = repository.enrichPendingMovies()

        assertEquals(0, result)
        verifyNoInteractions(vodDao)
    }

    @Test
    fun test_enrichPendingMovies_stopsAfterFirstBatch_whenBatchSmallerThanLimit() = runTest {
        val partialBatch = listOf(unenrichedEntity(1), unenrichedEntity(2))
        whenever(vodDao.getStreamsNeedingEnrichment(any())).thenReturn(partialBatch)
        whenever(vodDao.getStreamById(any())).thenAnswer { unenrichedEntity(it.getArgument(0)) }
        whenever(apiService.getVodInfo(eq("username"), eq("password"), any(), any())).thenReturn(genericInfoResponse)

        val result = repository.enrichPendingMovies(maxBatches = 5)

        assertEquals(2, result)
        // Lot < taille max -> catalogue rattrapé, pas de second appel.
        verify(vodDao, times(1)).getStreamsNeedingEnrichment(any())
    }

    @Test
    fun test_enrichPendingMovies_loopsUntilBatchIsNotFull() = runTest {
        val fullBatch = (1..50).map { unenrichedEntity(it) }
        val lastPartialBatch = listOf(unenrichedEntity(51), unenrichedEntity(52))
        whenever(vodDao.getStreamsNeedingEnrichment(any()))
            .thenReturn(fullBatch)
            .thenReturn(lastPartialBatch)
        whenever(vodDao.getStreamById(any())).thenAnswer { unenrichedEntity(it.getArgument(0)) }
        whenever(apiService.getVodInfo(eq("username"), eq("password"), any(), any())).thenReturn(genericInfoResponse)

        val result = repository.enrichPendingMovies(maxBatches = 5)

        assertEquals(52, result)
        verify(vodDao, times(2)).getStreamsNeedingEnrichment(any())
    }

    @Test
    fun test_enrichPendingMovies_respectsMaxBatchesCap_evenIfCatalogNotExhausted() = runTest {
        val fullBatch = (1..50).map { unenrichedEntity(it) }
        whenever(vodDao.getStreamsNeedingEnrichment(any())).thenReturn(fullBatch)
        whenever(vodDao.getStreamById(any())).thenAnswer { unenrichedEntity(it.getArgument(0)) }
        whenever(apiService.getVodInfo(eq("username"), eq("password"), any(), any())).thenReturn(genericInfoResponse)

        val result = repository.enrichPendingMovies(maxBatches = 3)

        assertEquals(150, result)
        verify(vodDao, times(3)).getStreamsNeedingEnrichment(any())
    }

    // --- getRelatedMovies : catégories masquées exclues AVANT le classement ---
    private fun relatedEntity(id: Int, genre: String, categoryId: String, rating: String = "5.0", added: String = "100") =
        VodStreamEntity(
            streamId = id, name = "Movie $id", streamIcon = null, rating = rating, added = added,
            categoryId = categoryId, cachedAt = 0L, genre = genre
        )

    @Test
    fun test_getRelatedMovies_excludesHiddenCategory_beforeRanking() = runTest {
        whenever(vodDao.getStreamById(1)).thenReturn(relatedEntity(1, "Action", "cat_current"))
        whenever(vodDao.getStreamsByGenre("%Action%")).thenReturn(
            listOf(
                relatedEntity(2, "Action", "cat_hidden", rating = "9.9"),
                relatedEntity(3, "Action", "cat_visible", rating = "1.0")
            )
        )

        val result = repository.getRelatedMovies(1, "Action", limit = 10, excludedCategoryIds = setOf("cat_hidden"))

        assertEquals(1, result.size)
        assertEquals(3, result[0].streamId)
    }

    @Test
    fun test_getRelatedMovies_hiddenCandidatesDontDisplaceVisibleOnesBeyondLimit() = runTest {
        // 3 candidats masqués mieux classés (note max) + 1 visible moins bien classé, limit=2.
        // Filtrer APRÈS le classement (bug corrigé) aurait tronqué avant d'atteindre le visible.
        whenever(vodDao.getStreamById(1)).thenReturn(relatedEntity(1, "Action", "cat_current"))
        val hidden = (10..12).map { relatedEntity(it, "Action", "cat_hidden", rating = "9.9") }
        val visible = relatedEntity(20, "Action", "cat_visible", rating = "1.0")
        whenever(vodDao.getStreamsByGenre("%Action%")).thenReturn(hidden + listOf(visible))

        val result = repository.getRelatedMovies(1, "Action", limit = 2, excludedCategoryIds = setOf("cat_hidden"))

        assertEquals(1, result.size)
        assertEquals(20, result[0].streamId)
    }

    @Test
    fun test_getRelatedMovies_noExcludedCategories_defaultsToAllCandidates() = runTest {
        whenever(vodDao.getStreamById(1)).thenReturn(relatedEntity(1, "Action", "cat_current"))
        whenever(vodDao.getStreamsByGenre("%Action%")).thenReturn(listOf(relatedEntity(2, "Action", "cat_other")))

        val result = repository.getRelatedMovies(1, "Action")

        assertEquals(1, result.size)
        assertEquals(2, result[0].streamId)
    }
}
