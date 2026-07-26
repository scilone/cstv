package com.cstv.app.data.repository

import com.google.gson.JsonPrimitive
import com.google.gson.JsonArray
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.SeriesCategoryEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.dto.*
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.SeriesEpisode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesRepositoryImplTest {

    @Mock
    private lateinit var apiService: XtreamApiService

    @Mock
    private lateinit var seriesDao: SeriesDao

    @Mock
    private lateinit var vodDao: VodDao

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var profileManager: com.cstv.app.data.local.storage.ProfileManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var networkMonitor: com.cstv.app.domain.network.NetworkMonitor

    private lateinit var repository: SeriesRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)
    private val activeProfileId = 1

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        doReturn(activeProfileId).whenever(profileManager).currentProfileId()
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        repository = SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor)
    }

    // --- 1. PLAY URL CONSTRUCTION TESTS ---
    @Test
    fun test_seriesEpisodePlayUrlConstruction_isCorrect() {
        val episode = SeriesEpisode(
            id = 55555,
            episodeNum = 1,
            title = "Pilote",
            containerExtension = "mkv",
            plot = "Le début",
            duration = "00:45:00",
            releaseDate = "2020"
        )

        // Case A: normal base URL
        val urlA = episode.getPlayUrl("http://myprovider.com:8080", "user", "pass")
        assertEquals("http://myprovider.com:8080/series/user/pass/55555.mkv", urlA)

        // Case B: trailing slash and .mp4 extension
        val episodeB = episode.copy(containerExtension = ".mp4")
        val urlB = episodeB.getPlayUrl("https://myprovider.com/", "user", "pass")
        assertEquals("https://myprovider.com/series/user/pass/55555.mp4", urlB)
    }

    // --- 2. CACHING & EXPIRATION LOGIC TESTS ---
    @Test
    fun test_getSeriesCategories_servedFromCache_whenNotExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        val cachedCategories = listOf(
            SeriesCategoryEntity("10", "Drames", 0, currentTime - 5000L) // cached 5s ago (not expired)
        )

        whenever(seriesDao.getAllCategories()).thenReturn(cachedCategories)

        val result = repository.getCachedSeriesCategories()

        assertEquals(1, result.size)
        assertEquals("10", result[0].categoryId)
        assertEquals("Drames", result[0].categoryName)

        verifyNoInteractions(apiService)
    }

    @Test
    fun test_getSeriesCategories_fetchedFromNetwork_whenExpired() = runTest {
        val currentTime = System.currentTimeMillis()
        // cached 48 hours ago (expired)
        val cachedCategories = listOf(
            SeriesCategoryEntity("10", "Drames", 0, currentTime - (48 * 3600 * 1000L))
        )

        whenever(seriesDao.getAllCategories()).thenReturn(cachedCategories)

        val remoteCategories = listOf(
            SeriesCategoryDto("10", "Drames Updated", 0),
            SeriesCategoryDto("20", "Comédies", 0)
        )
        whenever(apiService.getSeriesCategories("username", "password")).thenReturn(remoteCategories)

        val result = repository.syncSeriesCategories()

        assertEquals(2, result.size)
        assertEquals("Drames Updated", result[0].categoryName)
        assertEquals("Comédies", result[1].categoryName)

        verify(seriesDao).replaceAllCategories(any())
    }

    // --- 3. DIRTY PARSING / MAPPING TESTS ---
    @Test
    fun test_getSeriesStreams_defensivelySkipsNullIdsOrNames() = runTest {
        whenever(seriesDao.getStreamsByCategory(any())).thenReturn(emptyList())

        val remoteStreamsWithDirtyData = listOf(
            SeriesStreamDto(123, "Breaking Bad", "cover.jpg", "9.5", "added_str", "10"),
            SeriesStreamDto(null, "Null ID Series", null, null, null, null), // dirty: null ID
            SeriesStreamDto(124, null, null, null, null, null)               // dirty: null name
        )
        whenever(apiService.getSeriesStreams("username", "password", "10")).thenReturn(remoteStreamsWithDirtyData)

        val result = repository.syncSeriesStreams("10")

        // Dirty items should be silently and defensively ignored (only 1 valid series remains)
        assertEquals(1, result.size)
        assertEquals(123, result[0].seriesId)
        assertEquals("Breaking Bad", result[0].name)
    }

    @Test
    fun test_getSeriesStreams_preservesRawServerOrderIndex() = runTest {
        whenever(seriesDao.getStreamsByCategory(any())).thenReturn(emptyList())

        val remoteStreams = listOf(
            SeriesStreamDto(101, "First Series", "cover1.jpg", "8.0", "123", "5"),
            SeriesStreamDto(102, "Second Series", "cover2.jpg", "7.5", "124", "5"),
            SeriesStreamDto(103, "Third Series", "cover3.jpg", "9.0", "125", "5")
        )
        whenever(apiService.getSeriesStreams("username", "password", "5")).thenReturn(remoteStreams)

        repository.syncSeriesStreams("5")

        val entitiesCaptor = argumentCaptor<List<SeriesStreamEntity>>()
        verify(seriesDao).replaceStreamsByCategoryWithFts(eq("5"), entitiesCaptor.capture())

        val inserted = entitiesCaptor.firstValue
        assertEquals(3, inserted.size)
        assertEquals(0, inserted[0].orderIndex)
        assertEquals(1, inserted[1].orderIndex)
        assertEquals(2, inserted[2].orderIndex)
    }

    @Test
    fun test_getSeriesDetails_defensivelyHandlesStringIdsAndNullEpisodes() = runTest {
        val seasonsDto = listOf(
            SeriesSeasonDto("2008", "Season 1", 7, 1, "s1_cover.jpg")
        )
        val episodesMapDto = mapOf(
            "1" to listOf(
                SeriesEpisodeDto("555", "1", "Pilot", "mkv", SeriesEpisodeInfoDto("The start", "45:00", "2008", null), null),
                SeriesEpisodeDto(null, "2", "Episode 2", null, null, null), // dirty: null episode ID
                SeriesEpisodeDto("556", null, null, null, null, null)      // dirty: null title/extension
            )
        )
        val remoteResponse = SeriesInfoResponseDto(seasonsDto, episodesMapDto, null)

        whenever(apiService.getSeriesInfo("username", "password", 123)).thenReturn(remoteResponse)
        whenever(vodDao.getAllPlaybackPositions(any())).thenReturn(emptyList())

        val result = repository.getSeriesDetails(123)

        assertNotNull(result)
        assertEquals(123, result.seriesId)
        assertEquals("Season 1", result.name) // Falls back to first season name
        assertEquals(1, result.seasons.size)
        assertEquals(1, result.seasons[0].seasonNumber)
        assertEquals("Inconnu", result.director)
        assertEquals("Inconnu", result.actors)

        // Verify mapped episodes (the dirty episode with null ID is filtered out)
        val season1Episodes = result.episodes[1]
        assertNotNull(season1Episodes)
        val episodes = season1Episodes!!
        assertEquals(2, episodes.size) // valid "555" and "556" mapped, null ID filtered
        assertEquals(555, episodes[0].id)
        assertEquals(1, episodes[0].episodeNum)
        assertEquals("Pilot", episodes[0].title)
    }

    @Test
    fun test_getSeriesDetails_fullInfoParsingAndEpisodeImage() = runTest {
        val seriesMetadata = SeriesInfoMetadataDto(
            name = "Breaking Bad",
            cover = "bb_cover.jpg",
            plot = "A high school chemistry teacher...",
            cast = JsonPrimitive("Bryan Cranston, Aaron Paul"),
            actors = null,
            director = "Vince Gilligan",
            releaseDate = "2008",
            releaseDate2 = null,
            genre = "Drama",
            rating = "9.55", // rounds to 9.6
            rating5 = null
        )
        val seasonsDto = listOf(
            SeriesSeasonDto("2008", "Saison 1", 7, 1, "bb_s1_cover.jpg")
        )
        val episodesMapDto = mapOf(
            "1" to listOf(
                SeriesEpisodeDto(
                    id = "555",
                    episodeNum = "1",
                    title = "Pilot",
                    containerExtension = "mkv",
                    info = SeriesEpisodeInfoDto("Walter White...", "45:00", "2008", "episode_thumbnail.jpg"),
                    movieImage = null
                )
            )
        )
        val remoteResponse = SeriesInfoResponseDto(seasonsDto, episodesMapDto, seriesMetadata)

        whenever(apiService.getSeriesInfo("username", "password", 123)).thenReturn(remoteResponse)

        // Mock saved progress for episode
        val savedPosition = PlaybackPositionEntity(
            streamId = 555,
            profileId = activeProfileId,
            positionMs = 1200000L,
            durationMs = 2700000L,
            lastAccessedAt = 999999999L
        )
        whenever(vodDao.getAllPlaybackPositions(activeProfileId)).thenReturn(listOf(savedPosition))

        val result = repository.getSeriesDetails(123)

        assertNotNull(result)
        assertEquals("Breaking Bad", result.name)
        assertEquals("bb_cover.jpg", result.cover)
        assertEquals("Vince Gilligan", result.director)
        assertEquals("Bryan Cranston, Aaron Paul", result.actors)
        assertEquals("Drama", result.genre)
        assertEquals("9.6", result.rating)
        assertEquals("A high school chemistry teacher...", result.plot)

        // Verify episodes
        val season1Episodes = result.episodes[1]
        assertNotNull(season1Episodes)
        val episode = season1Episodes!![0]
        assertEquals(555, episode.id)
        assertEquals(1, episode.episodeNum)
        assertEquals("episode_thumbnail.jpg", episode.movieImage)
        assertEquals(1200000L, episode.resumePositionMs)
        assertEquals(2700000L, episode.durationMs)
        assertEquals(999999999L, episode.lastAccessedAt)
        assertEquals(1, episode.seasonNum)
    }

    @Test
    fun test_backgroundEnrichment_triggersAndSavesDetails() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor, testDispatcher)

        val remoteStreams = listOf(
            SeriesStreamDto(12, "Game of Thrones", "cover.png", "9.0", "added", "5")
        )
        whenever(apiService.getSeriesStreams("username", "password", "5")).thenReturn(remoteStreams)
        whenever(seriesDao.getStreamsByCategory("5")).thenReturn(emptyList())

        val unenrichedEntity = SeriesStreamEntity(12, "Game of Thrones", "cover.png", "9.0", "added", "5", System.currentTimeMillis())
        whenever(seriesDao.getStreamsNeedingEnrichment(any())).thenReturn(listOf(unenrichedEntity))

        val infoResponse = SeriesInfoResponseDto(
            seasons = emptyList(),
            episodes = emptyMap(),
            info = SeriesInfoMetadataDto(
                name = "Game of Thrones",
                cover = "cover.png",
                plot = "Dragons and swords.",
                cast = null,
                actors = JsonPrimitive("Kit Harington, Emilia Clarke"),
                director = "David Benioff",
                releaseDate = "2011",
                releaseDate2 = null,
                genre = "Fantasy",
                rating = "9.0",
                rating5 = null
            )
        )
        whenever(apiService.getSeriesInfo("username", "password", 12)).thenReturn(infoResponse)
        whenever(seriesDao.getStreamById(12)).thenReturn(unenrichedEntity)

        localRepository.syncSeriesStreams("5")

        verify(seriesDao).insertStreamsWithFts(argThat {
            size == 1 && get(0).seriesId == 12 && get(0).actors == "Kit Harington, Emilia Clarke" && get(0).director == "David Benioff" && get(0).genre == "Fantasy"
        })
    }

    @Test
    fun test_backgroundEnrichment_requestsBoundedBatch() = runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val localRepository = SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager, com.cstv.app.data.remote.api.XtreamRequestGate(), networkMonitor, testDispatcher)

        whenever(apiService.getSeriesStreams("username", "password", "5")).thenReturn(emptyList())
        whenever(seriesDao.getStreamsByCategory("5")).thenReturn(emptyList())
        whenever(seriesDao.getStreamsNeedingEnrichment(any())).thenReturn(emptyList())

        localRepository.syncSeriesStreams("5")

        // Le balayage d'enrichissement doit demander un lot borné (LIMIT SQL),
        // jamais l'intégralité du catalogue non enrichi d'un coup.
        val limitCaptor = argumentCaptor<Int>()
        verify(seriesDao).getStreamsNeedingEnrichment(limitCaptor.capture())
        assertTrue(limitCaptor.firstValue in 1..50)
    }

    // --- enrichPendingSeries (sync forcé/planifié, Phase 22) ---

    private fun unenrichedEntity(id: Int) = SeriesStreamEntity(
        seriesId = id, name = "Série $id", cover = null, rating = null,
        added = null, categoryId = "1", cachedAt = 0L
    )

    private val genericInfoResponse = SeriesInfoResponseDto(
        seasons = null,
        episodes = null,
        info = SeriesInfoMetadataDto(
            name = "Titre", cover = null, plot = null, cast = null,
            actors = JsonPrimitive("Un acteur"), director = "Un réalisateur",
            releaseDate = null, releaseDate2 = null, genre = "Drame",
            rating = null, rating5 = null
        )
    )

    @Test
    fun test_enrichPendingSeries_returnsZero_whenNoCredentials() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(null)

        val result = repository.enrichPendingSeries()

        assertEquals(0, result)
        verifyNoInteractions(seriesDao)
    }

    @Test
    fun test_enrichPendingSeries_stopsAfterFirstBatch_whenBatchSmallerThanLimit() = runTest {
        val partialBatch = listOf(unenrichedEntity(1), unenrichedEntity(2))
        whenever(seriesDao.getStreamsNeedingEnrichment(any())).thenReturn(partialBatch)
        whenever(seriesDao.getStreamById(any())).thenAnswer { unenrichedEntity(it.getArgument(0)) }
        whenever(apiService.getSeriesInfo(eq("username"), eq("password"), any(), any())).thenReturn(genericInfoResponse)

        val result = repository.enrichPendingSeries(maxBatches = 5)

        assertEquals(2, result)
        verify(seriesDao, times(1)).getStreamsNeedingEnrichment(any())
    }

    @Test
    fun test_enrichPendingSeries_loopsUntilBatchIsNotFull() = runTest {
        val fullBatch = (1..50).map { unenrichedEntity(it) }
        val lastPartialBatch = listOf(unenrichedEntity(51), unenrichedEntity(52))
        whenever(seriesDao.getStreamsNeedingEnrichment(any()))
            .thenReturn(fullBatch)
            .thenReturn(lastPartialBatch)
        whenever(seriesDao.getStreamById(any())).thenAnswer { unenrichedEntity(it.getArgument(0)) }
        whenever(apiService.getSeriesInfo(eq("username"), eq("password"), any(), any())).thenReturn(genericInfoResponse)

        val result = repository.enrichPendingSeries(maxBatches = 5)

        assertEquals(52, result)
        verify(seriesDao, times(2)).getStreamsNeedingEnrichment(any())
    }

    // --- getRelatedSeries : catégories masquées exclues AVANT le classement ---
    private fun relatedEntity(id: Int, genre: String, categoryId: String, rating: String = "5.0", added: String = "100") =
        SeriesStreamEntity(
            seriesId = id, name = "Série $id", cover = null, rating = rating, added = added,
            categoryId = categoryId, cachedAt = 0L, genre = genre
        )

    @Test
    fun test_getRelatedSeries_excludesHiddenCategory_beforeRanking() = runTest {
        whenever(seriesDao.getStreamById(1)).thenReturn(relatedEntity(1, "Comedy", "cat_current"))
        whenever(seriesDao.getStreamsByGenre("%Comedy%")).thenReturn(
            listOf(
                relatedEntity(2, "Comedy", "cat_hidden", rating = "9.9"),
                relatedEntity(3, "Comedy", "cat_visible", rating = "1.0")
            )
        )

        val result = repository.getRelatedSeries(1, "Comedy", limit = 10, excludedCategoryIds = setOf("cat_hidden"))

        assertEquals(1, result.size)
        assertEquals(3, result[0].seriesId)
    }

    @Test
    fun test_getRelatedSeries_hiddenCandidatesDontDisplaceVisibleOnesBeyondLimit() = runTest {
        whenever(seriesDao.getStreamById(1)).thenReturn(relatedEntity(1, "Comedy", "cat_current"))
        val hidden = (10..12).map { relatedEntity(it, "Comedy", "cat_hidden", rating = "9.9") }
        val visible = relatedEntity(20, "Comedy", "cat_visible", rating = "1.0")
        whenever(seriesDao.getStreamsByGenre("%Comedy%")).thenReturn(hidden + listOf(visible))

        val result = repository.getRelatedSeries(1, "Comedy", limit = 2, excludedCategoryIds = setOf("cat_hidden"))

        assertEquals(1, result.size)
        assertEquals(20, result[0].seriesId)
    }

    // --- T4 : lecture locale, saisons/épisodes persistés et fiche dégradée ---

    /** Les lectures de catalogue ne doivent jamais toucher au panel. */
    @Test
    fun test_localReadsNeverHitTheNetwork() = runTest {
        whenever(seriesDao.getAllCategories()).thenReturn(
            listOf(SeriesCategoryEntity("1", "Drame", 0, 0L, 0))
        )
        whenever(seriesDao.getAllStreams()).thenReturn(
            listOf(SeriesStreamEntity(1, "Série", null, null, null, "1", 0L))
        )

        assertEquals(1, repository.getCachedSeriesCategories().size)
        assertEquals(1, repository.getCachedSeriesStreams("all").size)

        verifyNoInteractions(apiService)
    }

    /**
     * Une série jamais ouverte en ligne n'a ni saison ni épisode en base : la
     * fiche reste affichable, mais explicitement marquée incomplète.
     */
    @Test
    fun test_getSeriesDetails_returnsDegradedCardWhenNeverEnriched() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(seriesDao.getStreamById(12)).thenReturn(
            SeriesStreamEntity(12, "Série", "cover.jpg", "9.0", null, "1", 0L, detailsCachedAt = 1L)
        )
        whenever(seriesDao.getSeasons(12)).thenReturn(emptyList())
        whenever(seriesDao.getEpisodes(12)).thenReturn(emptyList())
        whenever(vodDao.getAllPlaybackPositions(any())).thenReturn(emptyList())

        val details = repository.getSeriesDetails(12)

        assertEquals("Série", details.name)
        assertTrue(details.isMetadataIncomplete)
        verifyNoInteractions(apiService)
    }

    /**
     * Une fiche déjà consultée en ligne est relue depuis Room, saisons et
     * épisodes compris : c'est ce qui la rend complète hors ligne.
     */
    @Test
    fun test_getSeriesDetails_readsPersistedSeasonsAndEpisodesOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(seriesDao.getStreamById(12)).thenReturn(
            SeriesStreamEntity(12, "Série", "cover.jpg", "9.0", null, "1", 0L, plot = "Résumé", detailsCachedAt = 1L)
        )
        whenever(seriesDao.getSeasons(12)).thenReturn(
            listOf(com.cstv.app.data.local.entity.SeriesSeasonEntity(12, 1, "Saison 1", 2, null, 0L))
        )
        whenever(seriesDao.getEpisodes(12)).thenReturn(
            listOf(
                com.cstv.app.data.local.entity.SeriesEpisodeEntity(101, 12, 1, 1, "E1", "mkv", cachedAt = 0L),
                com.cstv.app.data.local.entity.SeriesEpisodeEntity(102, 12, 1, 2, "E2", "mkv", cachedAt = 0L)
            )
        )
        whenever(vodDao.getAllPlaybackPositions(any())).thenReturn(emptyList())

        val details = repository.getSeriesDetails(12)

        assertEquals("Résumé", details.plot)
        assertEquals(1, details.seasons.size)
        assertEquals(2, details.episodes[1]?.size)
        // containerExtension réel, pas deviné : une reprise hors ligne sur un
        // .mkv échouerait avec un repli "mp4".
        assertEquals("mkv", details.episodes[1]?.first()?.containerExtension)
        assertFalse(details.isMetadataIncomplete)
        verifyNoInteractions(apiService)
    }

    /** Toute ouverture réussie en ligne persiste le détail pour plus tard. */
    @Test
    fun test_getSeriesDetails_persistsSeasonsAndEpisodesOnSuccess() = runTest {
        whenever(seriesDao.getStreamById(12)).thenReturn(
            SeriesStreamEntity(12, "Série", null, "9.0", null, "1", 0L)
        )
        whenever(vodDao.getAllPlaybackPositions(any())).thenReturn(emptyList())
        whenever(apiService.getSeriesInfo("username", "password", 12)).thenReturn(
            SeriesInfoResponseDto(
                seasons = listOf(SeriesSeasonDto(null, "Saison 1", 1, 1, null)),
                episodes = mapOf(
                    "1" to listOf(
                        SeriesEpisodeDto("101", "1", "E1", "mkv", null, null)
                    )
                ),
                info = SeriesInfoMetadataDto(
                    name = "Série", cover = null, plot = "Résumé", cast = null, actors = null,
                    director = "Réalisateur", releaseDate = "2020-01-01", releaseDate2 = null,
                    genre = "Drame", rating = "9.0", rating5 = null
                )
            )
        )

        repository.getSeriesDetails(12)

        val episodesCaptor = argumentCaptor<List<com.cstv.app.data.local.entity.SeriesEpisodeEntity>>()
        verify(seriesDao).replaceSeriesDetail(eq(12), any(), episodesCaptor.capture())
        assertEquals(1, episodesCaptor.firstValue.size)
        assertEquals("mkv", episodesCaptor.firstValue.first().containerExtension)
    }

    /** Panel en échec : repli sur ce qui est déjà en base, pas d'exception. */
    @Test
    fun test_getSeriesDetails_fallsBackToCacheWhenPanelFails() = runTest {
        whenever(seriesDao.getStreamById(12)).thenReturn(
            SeriesStreamEntity(12, "Série", null, "9.0", null, "1", 0L)
        )
        whenever(seriesDao.getSeasons(12)).thenReturn(emptyList())
        whenever(seriesDao.getEpisodes(12)).thenReturn(emptyList())
        whenever(vodDao.getAllPlaybackPositions(any())).thenReturn(emptyList())
        whenever(apiService.getSeriesInfo("username", "password", 12))
            .thenAnswer { throw java.io.IOException("panel injoignable") }

        val details = repository.getSeriesDetails(12)

        assertEquals("Série", details.name)
        assertTrue(details.isMetadataIncomplete)
    }

    /** Critère §5.4 : un échec de synchronisation n'efface pas le catalogue. */
    @Test
    fun test_failedSyncNeverClearsTheCatalog() = runTest {
        whenever(apiService.getSeriesStreams(any(), any(), eq("5"), any()))
            .thenAnswer { throw java.io.IOException("panel injoignable") }

        runCatching { repository.syncSeriesStreams("5") }

        verify(seriesDao, never()).clearAllStreams()
        verify(seriesDao, never()).clearStreamsByCategory(any())
        verify(seriesDao, never()).clearAllFts()
    }
}
