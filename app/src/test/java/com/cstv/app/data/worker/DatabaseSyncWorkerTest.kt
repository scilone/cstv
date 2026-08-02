package com.cstv.app.data.worker

import com.cstv.app.domain.usecase.SyncCacheResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [DatabaseSyncWorker.runPostSyncDetection] (F12-R2) : le worker
 * lui-même utilise `EntryPointAccessors.fromApplication`, non testable en JVM
 * sans instrumentation, mais la logique d'orchestration post-sync qu'il
 * expose est extraite en fonction pure suspendue et testable isolément.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseSyncWorkerTest {

    @Test
    fun detectionRunsOnlyAfterSuccess() = runTest {
        var detectCalled = false

        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.FAILED_RETRYABLE) { detectCalled = true }
        assertFalse(detectCalled)

        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.FAILED_PERMANENT) { detectCalled = true }
        assertFalse(detectCalled)

        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.SKIPPED_NO_CREDENTIALS) { detectCalled = true }
        assertFalse(detectCalled)

        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.SUCCESS) { detectCalled = true }
        assertTrue(detectCalled)
    }

    @Test
    fun detectionExceptionIsIsolatedAndDoesNotPropagate() = runTest {
        // Un échec de détection ne doit jamais transformer un sync réussi en
        // retry : l'exception est interceptée et journalisée, pas propagée.
        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.SUCCESS) {
            throw RuntimeException("boom")
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationDuringDetectionIsRepropagated() = runTest {
        DatabaseSyncWorker.runPostSyncDetection(SyncCacheResult.SUCCESS) {
            throw CancellationException("cancelled")
        }
    }
}
