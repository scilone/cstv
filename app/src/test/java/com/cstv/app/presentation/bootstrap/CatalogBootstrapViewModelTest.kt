package com.cstv.app.presentation.bootstrap

import com.cstv.app.R
import com.cstv.app.domain.sync.CatalogStatus
import com.cstv.app.domain.sync.CatalogSection
import com.cstv.app.domain.sync.CatalogSyncManager
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncOutcome
import com.cstv.app.domain.sync.SyncState
import com.cstv.app.domain.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogBootstrapViewModelTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `incomplete catalog blocks and starts the first sync once`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager()
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isResolved)
        assertTrue(viewModel.state.value.blocking)
        viewModel.startIfNeeded()
        advanceUntilIdle()
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(SyncTrigger.STARTUP), manager.syncTriggers)
    }

    @Test
    fun `complete catalog unblocks without starting a sync`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(initialStatus = CatalogStatus(isComplete = true, isStale = false))
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.blocking)
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertTrue(manager.syncTriggers.isEmpty())
    }

    @Test
    fun `running catalog sections map to their visible labels and one based counters`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager()
        val viewModel = CatalogBootstrapViewModel(manager)
        val expected = listOf(
            CatalogSection.LIVE_CATEGORIES to R.string.catalog_bootstrap_live_categories,
            CatalogSection.LIVE_STREAMS to R.string.catalog_bootstrap_live_streams,
            CatalogSection.VOD_CATEGORIES to R.string.catalog_bootstrap_vod_categories,
            CatalogSection.VOD_STREAMS to R.string.catalog_bootstrap_vod_streams,
            CatalogSection.SERIES_CATEGORIES to R.string.catalog_bootstrap_series_categories,
            CatalogSection.SERIES_STREAMS to R.string.catalog_bootstrap_series_streams
        )

        expected.forEachIndexed { index, (section, labelRes) ->
            manager.sync.value = SyncState.Running(section, index, CATALOG_STEP_COUNT)
            advanceUntilIdle()

            assertEquals(labelRes, viewModel.state.value.stepLabelRes)
            assertEquals(index + 1, viewModel.state.value.stepIndex)
            assertEquals(CATALOG_STEP_COUNT, viewModel.state.value.stepCount)
        }
    }

    @Test
    fun `typed failure is exposed and prevents an automatic retry`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager()
        val viewModel = CatalogBootstrapViewModel(manager)
        manager.sync.value = SyncState.Failed(SyncFailureKind.PANEL, at = 1L)
        advanceUntilIdle()

        assertEquals(SyncFailureKind.PANEL, viewModel.state.value.failure)
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertTrue(manager.syncTriggers.isEmpty())
    }

    @Test
    fun `every synchronization failure kind is preserved for the screen message mapper`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager()
        val viewModel = CatalogBootstrapViewModel(manager)

        SyncFailureKind.entries.forEach { kind ->
            manager.sync.value = SyncState.Failed(kind, at = 1L)
            advanceUntilIdle()
            assertEquals(kind, viewModel.state.value.failure)
        }
    }

    @Test
    fun `retry explicitly starts a synchronization after an error`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager()
        val viewModel = CatalogBootstrapViewModel(manager)
        manager.sync.value = SyncState.Failed(SyncFailureKind.AUTH, at = 1L)
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(SyncTrigger.STARTUP), manager.syncTriggers)
    }

    @Test
    fun `retry returns the gate to a visible running state`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(emitRunningOnSync = true)
        val viewModel = CatalogBootstrapViewModel(manager)
        manager.sync.value = SyncState.Failed(SyncFailureKind.PANEL, at = 1L)
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSyncing)
        assertEquals(R.string.catalog_bootstrap_live_categories, viewModel.state.value.stepLabelRes)
    }

    @Test
    fun `offline state blocks without starting a synchronization`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(initialStatus = CatalogStatus(isNetworkOnline = false, isOffline = true))
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.blocking)
        assertTrue(viewModel.state.value.offline)
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertTrue(manager.syncTriggers.isEmpty())
    }

    @Test
    fun `a running sync does not receive a second startup request`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(
            initialStatus = CatalogStatus(isSyncing = true),
            initialSync = SyncState.Running(CatalogSection.LIVE_CATEGORIES, done = 0, total = CATALOG_STEP_COUNT)
        )
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSyncing)
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertTrue(manager.syncTriggers.isEmpty())
    }

    @Test
    fun `persistent network failure does not masquerade as an offline device`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(
            initialStatus = CatalogStatus(
                isNetworkOnline = true,
                isOffline = true,
                lastFailureKind = SyncFailureKind.NETWORK
            )
        )
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.offline)
        assertNull(viewModel.state.value.failure)
        viewModel.startIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(SyncTrigger.STARTUP), manager.syncTriggers)
    }

    @Test
    fun `enrichment keeps an indeterminate progress indicator while the catalog is incomplete`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(
            initialStatus = CatalogStatus(isSyncing = true),
            initialSync = SyncState.Running(CatalogSection.ENRICHMENT, 6, 7)
        )
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        assertNull(viewModel.state.value.stepLabelRes)
        assertTrue(viewModel.state.value.showIndeterminateProgress)
    }

    @Test
    fun `a catalog purge reblocks a previously complete catalog`() = runTest(dispatcher) {
        val manager = FakeCatalogSyncManager(initialStatus = CatalogStatus(isComplete = true, isStale = false))
        val viewModel = CatalogBootstrapViewModel(manager)
        advanceUntilIdle()

        manager.status.value = CatalogStatus()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.blocking)
    }

    private class FakeCatalogSyncManager(
        initialStatus: CatalogStatus = CatalogStatus(),
        initialSync: SyncState = SyncState.Idle,
        private val emitRunningOnSync: Boolean = false
    ) : CatalogSyncManager {
        val status = MutableStateFlow(initialStatus)
        val sync = MutableStateFlow(initialSync)
        val syncTriggers = mutableListOf<SyncTrigger>()

        override val syncState: StateFlow<SyncState> = sync
        override val catalogStatus: Flow<CatalogStatus> = status

        override suspend fun syncIfStale(): SyncOutcome = SyncOutcome.Skipped

        override suspend fun syncNow(trigger: SyncTrigger): SyncOutcome {
            syncTriggers += trigger
            if (emitRunningOnSync) {
                sync.value = SyncState.Running(CatalogSection.LIVE_CATEGORIES, done = 0, total = CATALOG_STEP_COUNT)
                status.value = status.value.copy(isSyncing = true)
            }
            return SyncOutcome.Skipped
        }

        override suspend fun onAccountAuthenticated(accountKey: String): Boolean = false
    }
}
