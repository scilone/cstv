package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.PlaybackListRow
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.EpisodeRef
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesWatchState
import com.cstv.app.domain.notification.NewEpisodeNotifier
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.SeriesWatchStateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetectNewEpisodesUseCaseTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var seriesWatchStateRepository: SeriesWatchStateRepository
    @Mock private lateinit var vodDao: VodDao
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var notifier: NewEpisodeNotifier
    @Mock private lateinit var settingsManager: SettingsManager
    @Mock private lateinit var accountKeyProvider: CurrentAccountKeyProvider

    private lateinit var useCase: DetectNewEpisodesUseCase
    private val accountKey = "account-key"

    private fun profile(id: Int) = Profile(id, "P$id", 0, 0L)

    private fun episode(season: Int, num: Int) = SeriesEpisode(
        id = season * 100 + num,
        episodeNum = num,
        title = "E$num",
        containerExtension = "mp4",
        plot = "",
        duration = "40:00",
        releaseDate = "",
        seasonNum = season
    )

    private fun details(seriesId: Int, name: String = "Série", episodes: Map<Int, List<SeriesEpisode>>, incomplete: Boolean = false) =
        SeriesDetails(seriesId, name, null, null, emptyList(), episodes, isMetadataIncomplete = incomplete)

    private fun completedPosition(seriesId: Int, season: Int, episodeNum: Int, lastAccessedAt: Long = 0L) =
        PlaybackListRow(
            providerId = seriesId * 1000 + episodeNum,
            kind = "episode",
            positionMs = 100_000L,
            durationMs = 100_000L,
            lastAccessedAt = lastAccessedAt,
            title = null, coverUrl = null, containerExtension = null,
            seriesId = seriesId,
            episodeNum = episodeNum,
            seasonNum = season,
            plot = null, duration = null, releaseDate = null, categoryId = null, genre = null
        )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(accountKeyProvider.current()).thenReturn(accountKey)
        useCase = DetectNewEpisodesUseCase(
            profileRepository,
            seriesWatchStateRepository,
            vodDao,
            seriesRepository,
            notifier,
            settingsManager,
            accountKeyProvider
        )
        runTest { whenever(vodDao.getAllPlaybackPositions(any(), any())).thenReturn(emptyList()) }
    }

    @Test
    fun isolatesNotificationsPerProfile() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1), profile(2)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(setOf(10))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(2)).thenReturn(setOf(20))

        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(
            mapOf(10 to SeriesWatchState(1, 10, EpisodeRef(1, 5), null))
        )
        whenever(seriesWatchStateRepository.getStates(2)).thenReturn(emptyMap())

        whenever(vodDao.getAllPlaybackPositions(1, accountKey)).thenReturn(listOf(completedPosition(10, 1, 5)))
        whenever(vodDao.getAllPlaybackPositions(2, accountKey)).thenReturn(emptyList())

        whenever(seriesRepository.getSeriesDetails(10)).thenReturn(
            details(10, episodes = mapOf(1 to listOf(episode(1, 5), episode(1, 6))))
        )

        useCase()

        verify(notifier).notifyNewEpisodes(eq(1), eq(10), any(), eq(EpisodeRef(1, 6)))
        verify(notifier, never()).notifyNewEpisodes(eq(2), any(), any(), any())
        // Profil 2 : aucun état stocké -> baseline, aucune requête réseau nécessaire
        // puisque sans stored state on ne peut pas décider "terminé" (pré-filtre laisse passer, mais ici seriesId 20 n'a pas de détail mocké).
    }

    @Test
    fun prefilter_skipsNetworkCall_forUnfinishedSeriesWithExistingState() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(setOf(10))
        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(
            mapOf(10 to SeriesWatchState(1, 10, EpisodeRef(1, 5), null))
        )
        // Aucune position enregistrée -> latestCompleted == null -> pas terminé.
        whenever(vodDao.getAllPlaybackPositions(1, accountKey)).thenReturn(emptyList())

        useCase()

        verifyNoInteractions(seriesRepository)
    }

    @Test
    fun networkErrorOnOneSeries_doesNotBlockOthersAndLeavesStateUnchanged() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(setOf(10, 20))
        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(
            mapOf(
                10 to SeriesWatchState(1, 10, EpisodeRef(1, 5), null),
                20 to SeriesWatchState(1, 20, EpisodeRef(1, 5), null)
            )
        )
        whenever(vodDao.getAllPlaybackPositions(1, accountKey)).thenReturn(
            listOf(completedPosition(10, 1, 5), completedPosition(20, 1, 5))
        )
        whenever(seriesRepository.getSeriesDetails(10)).thenThrow(RuntimeException("timeout"))
        whenever(seriesRepository.getSeriesDetails(20)).thenReturn(
            details(20, episodes = mapOf(1 to listOf(episode(1, 5), episode(1, 6))))
        )

        useCase()

        verify(notifier).notifyNewEpisodes(eq(1), eq(20), any(), eq(EpisodeRef(1, 6)))
        // La série 10 a levé une exception : aucun upsert ne doit référencer son état modifié.
        verify(seriesWatchStateRepository, never()).upsert(argThat { seriesId == 10 })
    }

    @Test
    fun notifierFailure_stillPersistsState() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(setOf(10))
        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(
            mapOf(10 to SeriesWatchState(1, 10, EpisodeRef(1, 5), null))
        )
        whenever(vodDao.getAllPlaybackPositions(1, accountKey)).thenReturn(listOf(completedPosition(10, 1, 5)))
        whenever(seriesRepository.getSeriesDetails(10)).thenReturn(
            details(10, episodes = mapOf(1 to listOf(episode(1, 5), episode(1, 6))))
        )
        whenever(notifier.notifyNewEpisodes(any(), any(), any(), any())).thenThrow(SecurityException("no permission"))

        useCase()

        // L'échec du notifier (permission refusée) ne doit pas empêcher la
        // persistance de l'état : sinon la série redéclencherait une tentative
        // de notification à chaque synchronisation suivante.
        verify(seriesWatchStateRepository).upsert(
            argThat { seriesId == 10 && lastKnown == EpisodeRef(1, 6) && lastNotified == EpisodeRef(1, 6) }
        )
    }

    @Test
    fun globalBudget_isSharedAcrossProfiles_andCursorRotates() = runTest {
        val manySeriesProfile1 = (1..15).toSet()
        val manySeriesProfile2 = (101..115).toSet()
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1), profile(2)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(manySeriesProfile1)
        whenever(seriesWatchStateRepository.eligibleSeriesIds(2)).thenReturn(manySeriesProfile2)
        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(emptyMap())
        whenever(seriesWatchStateRepository.getStates(2)).thenReturn(emptyMap())
        whenever(vodDao.getAllPlaybackPositions(any(), any())).thenReturn(emptyList())
        whenever(settingsManager.getNewEpisodesCheckCursor()).thenReturn(0)

        useCase()

        // Budget global = 20 : profil 1 (15 séries sans état -> baseline, candidates)
        // consomme 15, il reste 5 pour le profil 2.
        val captor = argumentCaptor<Int>()
        verify(seriesRepository, times(20)).getSeriesDetails(captor.capture())
        assertEquals(15, captor.allValues.count { it in manySeriesProfile1 })
        assertEquals(5, captor.allValues.count { it in manySeriesProfile2 })
        verify(settingsManager).setNewEpisodesCheckCursor(1)
    }

    @Test
    fun perProfileSeriesCursor_rotatesSoAllCandidatesEventuallyGetChecked() = runTest {
        // F12-R1 : un seul profil avec plus de séries candidates que le budget
        // global (20). Sans curseur par profil, les mêmes 20 premières séries
        // (triées par lastAccessedAt) seraient interrogées à chaque exécution
        // et les séries 21-25 ne seraient jamais atteintes.
        val allSeries = (1..25).toSet()
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(1)))
        whenever(seriesWatchStateRepository.eligibleSeriesIds(1)).thenReturn(allSeries)
        whenever(seriesWatchStateRepository.getStates(1)).thenReturn(emptyMap())
        whenever(vodDao.getAllPlaybackPositions(1, accountKey)).thenReturn(emptyList())
        whenever(settingsManager.getNewEpisodesCheckCursor()).thenReturn(0)

        var storedSeriesCursor = 0
        whenever(settingsManager.getNewEpisodesSeriesCursor(1)).thenAnswer { storedSeriesCursor }
        whenever(settingsManager.setNewEpisodesSeriesCursor(eq(1), any())).thenAnswer { invocation ->
            storedSeriesCursor = invocation.getArgument(1)
            Unit
        }

        useCase() // 1ère exécution : consomme les séries 1..20, curseur -> 20
        useCase() // 2ème exécution : reprend à partir de 20, couvre le reste + revient sur le début

        val captor = argumentCaptor<Int>()
        verify(seriesRepository, times(40)).getSeriesDetails(captor.capture())
        assertEquals(allSeries, captor.allValues.toSet())
    }
}
