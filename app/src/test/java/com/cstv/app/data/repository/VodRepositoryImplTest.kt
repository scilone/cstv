package com.cstv.app.data.repository

import com.google.gson.JsonPrimitive
import com.google.gson.JsonArray
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.dto.*
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.VodStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class VodRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var apiService: XtreamApiService

    @Mock
    private lateinit var vodDao: VodDao

    @Mock
    private lateinit var seriesDao: SeriesDao

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var profileManager: ProfileManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var networkMonitor: com.cstv.app.domain.network.NetworkMonitor

    private lateinit var repository: VodRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)
    private val activeProfileId = 1

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        doReturn(activeProfileId).whenever(profileManager).currentProfileId()
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        repository = VodRepositoryImpl(apiService, vodDao, seriesDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor)
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

        val result = repository.getCachedVodCategories()

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

        val result = repository.syncVodCategories()

        assertEquals(2, result.size)
        assertEquals("Action Updated", result[0].categoryName)
        assertEquals("Sci-Fi", result[1].categoryName)

        val categoriesCaptor = argumentCaptor<List<VodCategoryEntity>>()
        verify(vodDao).replaceAllCategories(categoriesCaptor.capture())
        assertEquals(2, categoriesCaptor.firstValue.size)
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

        val result = repository.syncVodStreams("5")

        // Slices out dirty inputs defensively (only 1 valid film remains)
        assertEquals(1, result.size)
        assertEquals(999, result[0].streamId)
        assertEquals("Inception", result[0].name)
    }

    @Test
    fun test_getVodStreams_preservesRawServerOrderIndex() = runTest {
        whenever(vodDao.getStreamsByCategory(any())).thenReturn(emptyList())

        val remoteStreams = listOf(
            VodStreamDto(101, "First Film", "cover1.jpg", "8.0", "123", "5"),
            VodStreamDto(102, "Second Film", "cover2.jpg", "7.5", "124", "5"),
            VodStreamDto(103, "Third Film", "cover3.jpg", "9.0", "125", "5")
        )
        whenever(apiService.getVodStreams("username", "password", "5")).thenReturn(remoteStreams)

        repository.syncVodStreams("5")

        val entitiesCaptor = argumentCaptor<List<VodStreamEntity>>()
        verify(vodDao).replaceStreamsByCategory(eq("5"), entitiesCaptor.capture())

        val inserted = entitiesCaptor.firstValue
        assertEquals(3, inserted.size)
        assertEquals(0, inserted[0].orderIndex)
        assertEquals(1, inserted[1].orderIndex)
        assertEquals(2, inserted[2].orderIndex)
    }

    @Test
    fun test_syncVodStreams_numbersCategoryRankPerCategory() = runTest {
        whenever(vodDao.getAllStreams()).thenReturn(emptyList())

        // Réponse « Tout » avec catégories entrelacées : `orderIndex` suit la
        // réponse complète, `categoryRank` repart de 0 dans chaque catégorie.
        // C'est ce rang que la requête de l'onglet « Tout » plafonne.
        val remoteStreams = listOf(
            VodStreamDto(101, "A", null, null, null, "69"),
            VodStreamDto(102, "B", null, null, null, "78"),
            VodStreamDto(103, "C", null, null, null, "69"),
            VodStreamDto(104, "D", null, null, null, "78"),
            VodStreamDto(105, "E", null, null, null, "69")
        )
        whenever(apiService.getVodStreams("username", "password", null)).thenReturn(remoteStreams)

        repository.syncVodStreams("all")

        val entitiesCaptor = argumentCaptor<List<VodStreamEntity>>()
        verify(vodDao).replaceAllStreams(entitiesCaptor.capture())

        val inserted = entitiesCaptor.firstValue
        assertEquals(listOf(0, 1, 2, 3, 4), inserted.map { it.orderIndex })
        assertEquals(listOf(0, 0, 1, 1, 2), inserted.map { it.categoryRank })
    }

    // --- 4. DETAILED VOD INFO & RESUME PERSISTENCE TESTS ---
    @Test
    fun test_getVodDetails_fallsBackOnCachedRow_whenPanelRefusesMetadata() = runTest {
        val refused = retrofit2.HttpException(
            retrofit2.Response.error<Any>(
                403,
                okhttp3.ResponseBody.Companion.create(null as okhttp3.MediaType?, "")
            )
        )
        whenever(apiService.getVodInfo("username", "password", 777)).thenThrow(refused)
        whenever(vodDao.getStreamById(777)).thenReturn(
            VodStreamEntity(
                streamId = 777,
                name = "Film bloqué",
                streamIcon = "cover.jpg",
                rating = "7.5",
                added = "1700000000",
                categoryId = "12",
                cachedAt = 0L,
                actors = "Une Actrice",
                director = "Un Réalisateur",
                genre = "Drame",
                releaseYear = 2019
            )
        )
        whenever(vodDao.getPlaybackPosition(777, activeProfileId)).thenReturn(null)

        val result = repository.getVodDetails(777)

        assertEquals("Film bloqué", result.name)
        assertEquals("Un Réalisateur", result.director)
        assertEquals("Une Actrice", result.actors)
        assertEquals("Drame", result.genre)
        assertEquals("2019", result.releaseDate)
        assertEquals("cover.jpg", result.coverBig)
        assertEquals("mp4", result.containerExtension)
        assertTrue(result.isMetadataIncomplete)
    }

    @Test(expected = retrofit2.HttpException::class)
    fun test_getVodDetails_rethrows_whenPanelRefusesAndNothingIsCached() = runTest {
        val refused = retrofit2.HttpException(
            retrofit2.Response.error<Any>(
                403,
                okhttp3.ResponseBody.Companion.create(null as okhttp3.MediaType?, "")
            )
        )
        whenever(apiService.getVodInfo("username", "password", 778)).thenThrow(refused)
        whenever(vodDao.getStreamById(778)).thenReturn(null)

        repository.getVodDetails(778)
    }

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
    fun test_savePlaybackPosition_resolvesMovieCategoryOnlyOnce() = runTest {
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(null)
        whenever(vodDao.getCategoryIdForStream(123)).thenReturn("action")

        repository.savePlaybackPosition(123, 2_000L, 5_000L)

        verify(vodDao).getCategoryIdForStream(123)
        verify(seriesDao, never()).getCategoryIdForSeries(any())
        verify(vodDao).savePlaybackPosition(argThat { categoryId == "action" })
    }

    @Test
    fun test_savePlaybackPosition_usesExistingCategoryWithoutResolutionQuery() = runTest {
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(
            PlaybackPositionEntity(123, activeProfileId, 1_000L, 5_000L, 1L, categoryId = "action")
        )

        repository.savePlaybackPosition(123, 2_000L, 5_000L)

        verify(vodDao, never()).getCategoryIdForStream(any())
        verify(seriesDao, never()).getCategoryIdForSeries(any())
        verify(vodDao).savePlaybackPosition(argThat { categoryId == "action" })
    }

    @Test
    fun test_savePlaybackPosition_resolvesSeriesCategoryWithoutVodLookup() = runTest {
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(null)
        whenever(seriesDao.getCategoryIdForSeries(9)).thenReturn("drama")

        repository.savePlaybackPosition(123, 2_000L, 5_000L, seriesId = 9)

        verify(seriesDao).getCategoryIdForSeries(9)
        verify(vodDao, never()).getCategoryIdForStream(any())
        verify(vodDao).savePlaybackPosition(argThat { categoryId == "drama" })
    }

    @Test
    fun test_savePlaybackPosition_explicitCategorySkipsResolution() = runTest {
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(null)

        repository.savePlaybackPosition(123, 2_000L, 5_000L, categoryId = "action")

        verify(vodDao, never()).getCategoryIdForStream(any())
        verify(seriesDao, never()).getCategoryIdForSeries(any())
        verify(vodDao).savePlaybackPosition(argThat { categoryId == "action" })
    }

    @Test
    fun test_savePlaybackPosition_existingUnknownCategoryDoesNotResolveAgain() = runTest {
        whenever(vodDao.getPlaybackPosition(123, activeProfileId)).thenReturn(
            PlaybackPositionEntity(123, activeProfileId, 1_000L, 5_000L, 1L)
        )

        repository.savePlaybackPosition(123, 2_000L, 5_000L)

        verify(vodDao, never()).getCategoryIdForStream(any())
        verify(seriesDao, never()).getCategoryIdForSeries(any())
    }

    @Test
    fun test_playbackPositionCategoryIsMappedForListAndFlow() = runTest {
        val entity = PlaybackPositionEntity(123, activeProfileId, 1_000L, 5_000L, 1L, categoryId = "action")
        whenever(vodDao.getAllPlaybackPositions(activeProfileId)).thenReturn(listOf(entity))
        whenever(vodDao.observeAllPlaybackPositions(activeProfileId)).thenReturn(flowOf(listOf(entity)))
        doReturn(kotlinx.coroutines.flow.MutableStateFlow(activeProfileId)).whenever(profileManager).activeProfileId

        assertEquals("action", repository.getAllPlaybackPositions().single().categoryId)
        assertEquals("action", repository.observeAllPlaybackPositions().first().single().categoryId)
    }

    @Test
    fun test_backgroundEnrichment_triggersAndSavesDetails() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = VodRepositoryImpl(apiService, vodDao, seriesDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor, testDispatcher)

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

        localRepository.syncVodStreams("5")

        verify(vodDao).insertStreams(argThat {
            size == 1 && get(0).streamId == 12 && get(0).actors == "Mark Hamill, Harrison Ford" && get(0).director == "George Lucas" && get(0).genre == "Sci-Fi"
        })
    }

    @Test
    fun test_backgroundEnrichment_requestsBoundedBatch() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = VodRepositoryImpl(apiService, vodDao, seriesDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor, testDispatcher)

        whenever(apiService.getVodStreams("username", "password", "5")).thenReturn(emptyList())
        whenever(vodDao.getStreamsByCategory("5")).thenReturn(emptyList())
        whenever(vodDao.getStreamsNeedingEnrichment(any())).thenReturn(emptyList())

        localRepository.syncVodStreams("5")

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

    // --- T4 : lecture locale, repli et persistance du détail ---

    /** Les lectures de catalogue ne doivent jamais toucher au panel. */
    @Test
    fun test_localReadsNeverHitTheNetwork() = runTest {
        whenever(vodDao.getAllCategories()).thenReturn(
            listOf(VodCategoryEntity("1", "Action", 0, 0L, 0))
        )
        whenever(vodDao.getAllStreams()).thenReturn(
            listOf(VodStreamEntity(1, "Film", null, null, null, "1", 0L))
        )

        assertEquals(1, repository.getCachedVodCategories().size)
        assertEquals(1, repository.getCachedVodStreams("all").size)

        verifyNoInteractions(apiService)
    }

    /**
     * Une fiche enrichie et fraîche est servie depuis Room : c'est ce qui évite
     * de multiplier les get_vod_info, principal contributeur au bannissement.
     */
    @Test
    fun test_getVodDetails_servesFreshCacheWithoutCallingPanel() = runTest {
        whenever(vodDao.getStreamById(7)).thenReturn(
            VodStreamEntity(
                streamId = 7, name = "Film", streamIcon = null, rating = "8.0", added = null,
                categoryId = "1", cachedAt = 0L, plot = "Résumé", duration = "1h 47min",
                containerExtension = "mkv", detailsCachedAt = System.currentTimeMillis()
            )
        )

        val details = repository.getVodDetails(7)

        assertEquals("Résumé", details.plot)
        assertEquals("mkv", details.containerExtension)
        assertFalse(details.isMetadataIncomplete)
        verifyNoInteractions(apiService)
    }

    /** Hors ligne, la fiche est servie quel que soit son âge. */
    @Test
    fun test_getVodDetails_servesStaleCacheWhenOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(vodDao.getStreamById(7)).thenReturn(
            VodStreamEntity(
                streamId = 7, name = "Film", streamIcon = null, rating = null, added = null,
                categoryId = "1", cachedAt = 0L, plot = "Vieux résumé",
                containerExtension = "mkv", detailsCachedAt = 1L
            )
        )

        val details = repository.getVodDetails(7)

        assertEquals("Vieux résumé", details.plot)
        verifyNoInteractions(apiService)
    }

    /**
     * Panel en échec : la fiche est reconstruite depuis le catalogue local et
     * signalée comme incomplète plutôt que de rester inaccessible.
     */
    @Test
    fun test_getVodDetails_fallsBackToCacheWhenPanelFails() = runTest {
        whenever(vodDao.getStreamById(7)).thenReturn(
            VodStreamEntity(7, "Film", null, "8.0", null, "1", 0L)
        )
        whenever(apiService.getVodInfo(any(), any(), eq(7), any()))
            .thenAnswer { throw java.io.IOException("panel injoignable") }

        val details = repository.getVodDetails(7)

        assertEquals("Film", details.name)
        assertTrue(details.isMetadataIncomplete)
    }

    /** plot / duration / containerExtension doivent survivre à la fermeture de la fiche. */
    @Test
    fun test_getVodDetails_persistsPlotDurationAndContainerExtension() = runTest {
        whenever(vodDao.getStreamById(7)).thenReturn(
            VodStreamEntity(7, "Film", null, "8.0", null, "1", 0L)
        )
        whenever(apiService.getVodInfo(any(), any(), eq(7), any())).thenReturn(
            VodInfoResponseDto(
                info = VodInfoDto(
                    name = "Film", plot = "Un résumé", director = "Réalisateur",
                    actors = JsonPrimitive("Acteur"), cast = null, releaseDate = "2020-01-01",
                    genre = "Action", rating = "8.0", rating5 = null, coverBig = null,
                    movieImage = null, duration = JsonPrimitive(6420)
                ),
                movieData = VodMovieDataDto(streamId = 7, containerExtension = "mkv")
            )
        )

        repository.getVodDetails(7)

        val captor = argumentCaptor<List<VodStreamEntity>>()
        verify(vodDao).insertStreams(captor.capture())
        val persisted = captor.firstValue.first()
        assertEquals("Un résumé", persisted.plot)
        assertEquals("mkv", persisted.containerExtension)
        assertNotNull(persisted.detailsCachedAt)
    }

    /**
     * Critère §5.4 : un échec ne doit effacer aucun catalogue déjà synchronisé.
     * Le garde de remplacement vit dans le DAO ; on vérifie ici que le
     * repository n'émet pas d'ordre d'effacement séparé.
     */
    @Test
    fun test_failedSyncNeverClearsTheCatalog() = runTest {
        whenever(apiService.getVodStreams(any(), any(), eq("5"), any()))
            .thenAnswer { throw java.io.IOException("panel injoignable") }

        runCatching { repository.syncVodStreams("5") }

        verify(vodDao, never()).clearAllStreams()
        verify(vodDao, never()).clearStreamsByCategory(any())
    }

    /**
     * L'appariement TMDB chargeait tout le catalogue non enrichi d'un coup et
     * dépassait le CursorWindow de 2 Mo. La requête est désormais bornée par
     * année et lue page par page jusqu'à épuisement : une page incomplète
     * termine la boucle.
     *
     * Le motif de titre reste `%année%`, sans délimiteur : un motif plus étroit
     * (« (2026) », « 2026 » entouré d'espaces) exclurait ici des titres que
     * TmdbCatalogMatcher.yearFromTitle accepte — année en fin de nom, séparée
     * par des points, entre crochets.
     */
    @Test
    fun test_getCachedVodStreamsByYears_readsEveryPageForTheYear() = runTest {
        val pageSize = VodRepositoryImpl.YEAR_MATCH_PAGE_SIZE
        val fullPage = (1..pageSize).map { id ->
            VodStreamEntity(id, "Film $id (2026)", null, "8.0", null, "1", 0L, releaseYear = 2026)
        }
        val lastPage = listOf(
            VodStreamEntity(9001, "Odyssee.2026.MULTI.1080p", null, "7.0", null, "1", 0L, releaseYear = 0)
        )
        whenever(vodDao.getStreamsByReleaseYearPage(2026, "%2026%", pageSize, 0)).thenReturn(fullPage)
        whenever(vodDao.getStreamsByReleaseYearPage(2026, "%2026%", pageSize, pageSize)).thenReturn(lastPage)

        val result = repository.getCachedVodStreamsByYears(setOf(2026))

        assertEquals(pageSize + 1, result.size)
        assertTrue(result.any { it.streamId == 9001 })
        verify(vodDao, never()).getStreamsByReleaseYearPage(2026, "%2026%", pageSize, pageSize * 2)
    }

    /** Un titre daté de plusieurs années demandées ne doit ressortir qu'une fois. */
    @Test
    fun test_getCachedVodStreamsByYears_deduplicatesAcrossYears() = runTest {
        val shared = VodStreamEntity(7, "Saga 2025 2026", null, "8.0", null, "1", 0L, releaseYear = 0)
        whenever(vodDao.getStreamsByReleaseYearPage(eq(2025), any(), any(), any())).thenReturn(listOf(shared))
        whenever(vodDao.getStreamsByReleaseYearPage(eq(2026), any(), any(), any())).thenReturn(listOf(shared))

        val result = repository.getCachedVodStreamsByYears(setOf(2025, 2026))

        assertEquals(1, result.size)
        assertEquals(7, result.first().streamId)
    }
}
