package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCacheUseCaseTest {

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var liveTvRepository: LiveTvRepository

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    private lateinit var useCase: SyncCacheUseCase

    private val credentials = Credentials("host.example.com", 8080, "user", "pass", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = SyncCacheUseCase(credentialsManager, liveTvRepository, vodRepository, seriesRepository)
    }

    @Test
    fun test_invoke_skipsSync_whenNoCredentialsStored() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(null)

        val result = useCase()

        assertEquals(SyncCacheResult.SKIPPED_NO_CREDENTIALS, result)
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    @Test
    fun test_invoke_forceRefreshesAllCatalogSources_whenCredentialsPresent() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)

        val result = useCase()

        assertEquals(SyncCacheResult.SUCCESS, result)
        verify(liveTvRepository).getLiveCategories(forceRefresh = true)
        verify(liveTvRepository).getLiveStreams(categoryId = "all", forceRefresh = true)
        verify(vodRepository).getVodCategories(forceRefresh = true)
        verify(vodRepository).getVodStreams(categoryId = "all", forceRefresh = true)
        verify(seriesRepository).getSeriesCategories(forceRefresh = true)
        verify(seriesRepository).getSeriesStreams(categoryId = "all", forceRefresh = true)
        verify(vodRepository).enrichPendingMovies()
        verify(seriesRepository).enrichPendingSeries()
    }

    @Test
    fun test_invoke_returnsFailed_whenRepositoryThrows() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(liveTvRepository.getLiveCategories(forceRefresh = true))
            .thenThrow(RuntimeException("Network timeout"))

        val result = useCase()

        assertEquals(SyncCacheResult.FAILED, result)
    }

    @Test
    fun test_invoke_returnsFailed_whenLaterRepositoryCallThrows() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(seriesRepository.getSeriesStreams(categoryId = "all", forceRefresh = true))
            .thenThrow(RuntimeException("Malformed response"))

        val result = useCase()

        assertEquals(SyncCacheResult.FAILED, result)
        verify(liveTvRepository).getLiveStreams(categoryId = "all", forceRefresh = true)
        verify(vodRepository).getVodStreams(categoryId = "all", forceRefresh = true)
    }
}
