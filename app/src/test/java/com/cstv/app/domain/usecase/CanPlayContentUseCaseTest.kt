package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.entity.DownloadedMediaEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.network.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CanPlayContentUseCaseTest {

    @Mock private lateinit var downloadDao: DownloadDao
    @Mock private lateinit var networkMonitor: NetworkMonitor
    @Mock private lateinit var credentialsManager: CredentialsManager

    private lateinit var useCase: CanPlayContentUseCase

    private val credentials = Credentials("panel.example.com", 8080, "user", "secret", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        useCase = CanPlayContentUseCase(downloadDao, networkMonitor, credentialsManager)
    }

    private fun downloaded(contentId: String, status: DownloadStatus) = DownloadedMediaEntity(
        contentId = contentId,
        type = "movie",
        streamId = 1,
        title = "Film",
        coverUrl = null,
        containerExtension = "mkv",
        status = status.name,
        percent = 100,
        bytesDownloaded = 1L,
        totalBytes = 1L,
        createdAt = 0L
    )

    @Test
    fun onlineContentIsAllowed() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        whenever(downloadDao.getByContentId("movie_1")).thenReturn(null)

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_1"))
    }

    /**
     * Cas central du mode hors-ligne : un média téléchargé reste lisible sans
     * réseau, par le chemin cache Media3 déjà en place.
     */
    @Test
    fun downloadedContentIsAllowedEvenOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByContentId("movie_1"))
            .thenReturn(downloaded("movie_1", DownloadStatus.COMPLETED))

        assertEquals(PlaybackAvailability.Allowed, useCase("movie_1"))
    }

    /** Un téléchargement inachevé n'est pas un média lisible. */
    @Test
    fun partiallyDownloadedContentRequiresConnectionOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByContentId("movie_1"))
            .thenReturn(downloaded("movie_1", DownloadStatus.DOWNLOADING))

        assertEquals(PlaybackAvailability.RequiresConnection, useCase("movie_1"))
    }

    @Test
    fun offlineAndNotDownloadedRequiresConnection() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        whenever(downloadDao.getByContentId("movie_1")).thenReturn(null)

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
        whenever(downloadDao.getByContentId("movie_1")).thenReturn(null)

        assertEquals(PlaybackAvailability.RequiresReauthentication, useCase("movie_1"))
    }
}
