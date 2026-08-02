package com.cstv.app.presentation.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class TvInitialFocusTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Test
    fun stopsAsSoonAsAcquired() = runTest(StandardTestDispatcher()) {
        var acquired = false
        var calls = 0
        runInitialFocusAttempts(
            awaitReady = {},
            isAcquired = { acquired },
            requestFocus = { calls++; if (calls == 3) acquired = true },
            attempts = 10,
            retryMs = 80L
        )
        assertEquals(3, calls)
    }

    @Test
    fun givesUpAfterTheBoundedAttemptCount() = runTest(StandardTestDispatcher()) {
        var calls = 0
        runInitialFocusAttempts(
            awaitReady = {},
            isAcquired = { false },
            requestFocus = { calls++ },
            attempts = 10,
            retryMs = 80L
        )
        assertEquals(10, calls)
    }

    @Test
    fun waitsForReadyBeforeRequestingFocus() = runTest(StandardTestDispatcher()) {
        val readySignal = CompletableDeferred<Unit>()
        var calls = 0
        val job = launch {
            runInitialFocusAttempts(
                awaitReady = { readySignal.await() },
                isAcquired = { false },
                requestFocus = { calls++ },
                attempts = 1,
                retryMs = 80L
            )
        }
        advanceUntilIdle()
        assertEquals(0, calls)

        readySignal.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(1, calls)
    }

    @Test
    fun alreadyAcquiredNeverRequestsFocus() = runTest(StandardTestDispatcher()) {
        var calls = 0
        runInitialFocusAttempts(
            awaitReady = {},
            isAcquired = { true },
            requestFocus = { calls++ },
            attempts = 10,
            retryMs = 80L
        )
        assertEquals(0, calls)
    }
}
