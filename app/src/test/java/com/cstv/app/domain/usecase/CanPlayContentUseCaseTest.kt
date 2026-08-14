package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.entity.DownloadedMediaEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.network.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CanPlayContentUseCaseTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock private lateinit var downloadDao: DownloadDao
    @Mock private lateinit var networkMonitor: NetworkMonitor
    @Mock private lateinit var credentialsManager: CredentialsManager
    @Mock private lateinit var accountKeyProvider: CurrentAccountKeyProvider

    private lateinit var useCase: CanPlayContentUseCase

    private val credentials = Credentials("panel.example.com", 8080, "user", "secret")
    private val accountKey = "account-key"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(accountKeyProvider.current()).thenReturn(accountKey)
        useCase = CanPlayContentUseCase(
            IsContentDownloadedUseCase(downloadDao, accountKeyProvider),
            networkMonitor,
            credentialsManager,
        )
    }

    // T20: contentId is derived from `(kind, providerId)`, not stored -- the entity only carries
    // the resolved `mediaUid` plus status/progress.
    private fun downloaded(status: DownloadStatus) = DownloadedMediaEntity(
        mediaUid = 1L,
        status = status.name,
        percent = 100,
        bytesDownloaded = 1L,
        totalBytes = 1L,
        createdAt = 0L
    )

    @Test
    fun onlineContentIsAllowed() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        whenever(downloadDao.getByRef(accountKey, "movie", 1)).thenReturn(null)

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_1"))
    }

    /**
     * Cas central du mode hors-ligne : un média téléchargé reste lisible sans
     * réseau, par le chemin cache Media3 déjà en place.
     */
    @Test
    fun downloadedContentIsAllowedEvenOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByRef(accountKey, "movie", 1)).thenReturn(downloaded(DownloadStatus.COMPLETED))

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_1"))
    }

    /** Un téléchargement inachevé n'est pas un média lisible. */
    @Test
    fun partiallyDownloadedContentRequiresConnectionOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByRef(accountKey, "movie", 1)).thenReturn(downloaded(DownloadStatus.DOWNLOADING))

        assertEquals(PlaybackAvailability.RequiresConnection, useCase("movie_1"))
    }

    @Test
    fun offlineAndNotDownloadedRequiresConnection() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByRef(accountKey, "movie", 1)).thenReturn(null)

        assertEquals(PlaybackAvailability.RequiresConnection, useCase("movie_1"))
    }

    /** Un flux Live n'est jamais téléchargeable : contentId nul. */
    @Test
    fun liveStreamOfflineRequiresConnection() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)

        assertEquals(PlaybackAvailability.RequiresConnection, useCase(null))
    }

    /**
     * Distinct de [PlaybackAvailability.RequiresConnection] : sans identifiants,
     * le message doit parler de session à revalider, pas de réseau absent (§5.5).
     */
    @Test
    fun missingCredentialsRequiresReauthentication() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        whenever(credentialsManager.getCredentials()).thenReturn(null)
        whenever(downloadDao.getByRef(accountKey, "movie", 1)).thenReturn(null)

        assertEquals(PlaybackAvailability.RequiresReauthentication, useCase("movie_1"))
    }
}
