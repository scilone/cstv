package com.cstv.app.data.worker

import androidx.work.ListenableWorker
import com.cstv.app.domain.model.IptvBackupOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvCredentialsBackupWorkerTest {
    @Test fun deferredOperationRequestsRetryAndCompletedOperationSucceeds() {
        assertEquals(ListenableWorker.Result.retry(), iptvBackupWorkerResult(IptvBackupOutcome.Deferred))
        assertEquals(ListenableWorker.Result.success(), iptvBackupWorkerResult(IptvBackupOutcome.Deleted))
    }
}
