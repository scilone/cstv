package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.repository.ContentClassificationRepository
import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.domain.model.AgeRating
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.model.ParentalAccessPolicy
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.ParentalAuthorizationRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.*

/**
 * F44, tâche 5 : vérifie que `CanPlayContentUseCase` — l'unique point d'entrée
 * appelé par Accueil, VOD, Séries et Favoris avant toute lecture (§8.4) —
 * applique bien la garde parentale, y compris son coût nul pour un profil non
 * bridé et le fonctionnement du grant one-shot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CanPlayContentParentalGuardTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val downloadDao: DownloadDao = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val credentialsManager: CredentialsManager = mock()
    private val accountKeyProvider: CurrentAccountKeyProvider = mock()
    private val profileRepository: ProfileRepository = mock()
    private val vodRepository: VodRepository = mock()
    private val seriesRepository: SeriesRepository = mock()
    private val classificationRepository: ContentClassificationRepository = mock()
    private val parentalAuthorizationRepository: ParentalAuthorizationRepository = mock()
    private val grantStore = OneShotPlaybackGrantStore()

    private lateinit var useCase: CanPlayContentUseCase

    private val credentials = Credentials("panel.example.com", 8080, "user", "secret")
    private val movie = VodStream(streamId = 42, name = "Dune", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2021)

    @Before
    fun setUp() {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(accountKeyProvider.current()).thenReturn("account-key")
        // Mockito (sans mockito-inline) renvoie `null` par défaut pour une méthode
        // suspend d'interface, quel que soit son type de retour déclaré (le bytecode
        // CPS renvoie `Any`) : sans ce stub explicite, l'unboxing en `Boolean`
        // provoque un NPE (même piège que documenté dans AGENTS.md, étendu ici aux
        // fonctions suspend). Chaque test F45 réécrit ce stub pour son cas précis.
        parentalAuthorizationRepository.stub { onBlocking { isAuthorized(any(), any(), any()) }.doReturn(false) }
        useCase = CanPlayContentUseCase(
            IsContentDownloadedUseCase(downloadDao, accountKeyProvider),
            networkMonitor,
            credentialsManager,
            profileRepository,
            vodRepository,
            seriesRepository,
            classificationRepository,
            ParentalAccessPolicy(),
            grantStore,
            parentalAuthorizationRepository,
        )
    }

    private fun profile(maxAgeRating: Int?) = Profile(id = 1, name = "Nico", avatarId = 0, createdAt = 0L, maxAgeRating = maxAgeRating)

    @Test
    fun `an unbridged profile never queries the classification repository`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(null)))

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_42"))

        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `a bridged profile is refused with TOO_MATURE for an over-the-limit movie`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)).thenReturn(AgeRating.SIXTEEN)

        val result = useCase("movie_42")

        assertEquals(
            PlaybackAvailability.RequiresParentalPin(
                BlockReason.TOO_MATURE, "movie_42", AgeRating.SIXTEEN,
                authorizationTarget = ParentalAuthorizationTarget(MediaClassificationKind.MOVIE, 42),
            ),
            result
        )
    }

    @Test
    fun `a bridged profile is refused with UNCLASSIFIED when T22 has no answer`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)).thenReturn(null)

        val result = useCase("movie_42")

        assertEquals(
            PlaybackAvailability.RequiresParentalPin(
                BlockReason.UNCLASSIFIED, "movie_42",
                authorizationTarget = ParentalAuthorizationTarget(MediaClassificationKind.MOVIE, 42),
            ),
            result,
        )
    }

    @Test
    fun `a bridged profile refuses a movie missing from the local catalog`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(null)

        assertEquals(
            PlaybackAvailability.RequiresParentalPin(BlockReason.UNCLASSIFIED, "movie_42"),
            useCase("movie_42")
        )
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `a bridged profile is allowed for a compatible classification`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(16)))
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)).thenReturn(AgeRating.TWELVE)

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_42"))
    }

    @Test
    fun `episode classification uses the parent series, not the episode`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(10)))
        whenever(seriesRepository.getSeriesIdForEpisode(99)).thenReturn(7)
        whenever(seriesRepository.getStreamById(7)).thenReturn(
            com.cstv.app.domain.model.SeriesStream(seriesId = 7, name = "Supergirl", cover = null, rating = null, added = null, categoryId = "cat", releaseYear = 2015)
        )
        whenever(classificationRepository.classificationFor(MediaClassificationKind.SERIES, "Supergirl", 2015)).thenReturn(AgeRating.SIXTEEN)

        val result = useCase("episode_99")

        assertEquals(
            PlaybackAvailability.RequiresParentalPin(
                BlockReason.TOO_MATURE, "episode_99", AgeRating.SIXTEEN,
                authorizationTarget = ParentalAuthorizationTarget(MediaClassificationKind.SERIES, 7),
            ),
            result
        )
    }

    @Test
    fun `a valid one-shot grant bypasses the classification check entirely`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        val nonce = grantStore.issue(profileId = 1, mediaUid = "movie_42")

        val result = useCase("movie_42", oneShotGrantNonce = nonce)

        assertEquals(PlaybackAvailability.Allowed, result)
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `a consumed grant does not authorize a second playback of the same media`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021)).thenReturn(AgeRating.SIXTEEN)
        val nonce = grantStore.issue(profileId = 1, mediaUid = "movie_42")

        useCase("movie_42", oneShotGrantNonce = nonce) // consomme le grant

        val secondAttempt = useCase("movie_42", oneShotGrantNonce = nonce)
        assertEquals(
            PlaybackAvailability.RequiresParentalPin(
                BlockReason.TOO_MATURE, "movie_42", AgeRating.SIXTEEN,
                authorizationTarget = ParentalAuthorizationTarget(MediaClassificationKind.MOVIE, 42),
            ),
            secondAttempt
        )
    }

    @Test
    fun `an already downloaded movie is never revalidated against the parental policy`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(0)))
        whenever(downloadDao.getByRef("account-key", "movie", 42))
            .thenReturn(
                com.cstv.app.data.local.entity.DownloadedMediaEntity(
                    mediaUid = 1L, status = "COMPLETED", percent = 100, bytesDownloaded = 1L, totalBytes = 1L, createdAt = 0L
                )
            )

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_42"))
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `F45 - a permanent authorization bypasses the classification check entirely`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(movie)
        whenever(parentalAuthorizationRepository.isAuthorized(1, MediaClassificationKind.MOVIE, 42)).thenReturn(true)

        val result = useCase("movie_42")

        assertEquals(PlaybackAvailability.Allowed, result)
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `F45 - a permanent authorization on a series covers every episode`() = runTest {
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(10)))
        whenever(seriesRepository.getSeriesIdForEpisode(99)).thenReturn(7)
        whenever(seriesRepository.getStreamById(7)).thenReturn(
            com.cstv.app.domain.model.SeriesStream(seriesId = 7, name = "Supergirl", cover = null, rating = null, added = null, categoryId = "cat", releaseYear = 2015)
        )
        whenever(parentalAuthorizationRepository.isAuthorized(1, MediaClassificationKind.SERIES, 7)).thenReturn(true)

        val result = useCase("episode_99")

        assertEquals(PlaybackAvailability.Allowed, result)
        verifyNoInteractions(classificationRepository)
    }
}
