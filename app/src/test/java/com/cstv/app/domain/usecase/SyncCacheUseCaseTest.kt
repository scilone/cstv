package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.sync.CatalogSyncManager
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncOutcome
import com.cstv.app.domain.sync.SyncTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * Depuis T4, ce use case n'orchestre plus rien : il ne fait que traduire le
 * verdict du [CatalogSyncManager] pour le worker. L'orchestration elle-même est
 * couverte par `CatalogSyncManagerImplTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCacheUseCaseTest {

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var catalogSyncManager: CatalogSyncManager

    private lateinit var useCase: SyncCacheUseCase

    private val credentials = Credentials("host.example.com", 8080, "user", "pass", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = SyncCacheUseCase(credentialsManager, catalogSyncManager)
    }

    @Test
    fun test_invoke_skipsSync_whenNoCredentialsStored() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(null)

        val result = useCase()

        assertEquals(SyncCacheResult.SKIPPED_NO_CREDENTIALS, result)
        verifyNoInteractions(catalogSyncManager)
    }

    @Test
    fun test_invoke_delegatesToManager_withGivenTrigger() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(catalogSyncManager.syncNow(SyncTrigger.SCHEDULED)).thenReturn(SyncOutcome.Success(1L))

        val result = useCase(SyncTrigger.SCHEDULED)

        assertEquals(SyncCacheResult.SUCCESS, result)
        verify(catalogSyncManager).syncNow(SyncTrigger.SCHEDULED)
    }

    @Test
    fun test_invoke_returnsFailed_whenSyncFails() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(catalogSyncManager.syncNow(any()))
            .thenReturn(SyncOutcome.Failure(SyncFailureKind.NETWORK, 1L))

        assertEquals(SyncCacheResult.FAILED_RETRYABLE, useCase())
    }

    @Test
    fun test_invoke_returnsPermanentFailure_forStorageAndAuthentication() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)

        whenever(catalogSyncManager.syncNow(any()))
            .thenReturn(SyncOutcome.Failure(SyncFailureKind.STORAGE, 1L))
        assertEquals(SyncCacheResult.FAILED_PERMANENT, useCase())

        whenever(catalogSyncManager.syncNow(any()))
            .thenReturn(SyncOutcome.Failure(SyncFailureKind.AUTH, 1L))
        assertEquals(SyncCacheResult.FAILED_PERMANENT, useCase())
    }

    /**
     * Une synchronisation déjà en cours n'est pas un échec : la signaler comme
     * tel ferait retenter le worker, donc générer le trafic panel que T4 supprime.
     */
    @Test
    fun test_invoke_treatsAlreadyRunningAndSkippedAsSuccess() = runTest {
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)

        whenever(catalogSyncManager.syncNow(any())).thenReturn(SyncOutcome.AlreadyRunning)
        assertEquals(SyncCacheResult.SUCCESS, useCase())

        whenever(catalogSyncManager.syncNow(any())).thenReturn(SyncOutcome.Skipped)
        assertEquals(SyncCacheResult.SUCCESS, useCase())
    }
}
