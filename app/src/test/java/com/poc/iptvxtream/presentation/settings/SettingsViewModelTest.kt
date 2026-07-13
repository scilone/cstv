package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.SyncFrequency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var context: android.content.Context

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Mock default behaviors
        whenever(settingsManager.getTvCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getVodCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getSeriesCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        
        viewModel = SettingsViewModel(settingsManager, context)
    }

    @Test
    fun test_initialState_loadsFromSettingsManager() {
        val state = viewModel.state.value
        assertEquals(CategorySorting.DEFAULT, state.tvSorting)
        assertEquals(CategorySorting.DEFAULT, state.vodSorting)
        assertEquals(CategorySorting.DEFAULT, state.seriesSorting)
    }

    @Test
    fun test_updateTvSorting_savesToSettingsManager_andUpdatesState() {
        viewModel.updateTvSorting(CategorySorting.ALPHABETICAL)
        
        verify(settingsManager).setTvCategorySorting(CategorySorting.ALPHABETICAL)
        assertEquals(CategorySorting.ALPHABETICAL, viewModel.state.value.tvSorting)
    }

    @Test
    fun test_updateVodSorting_savesToSettingsManager_andUpdatesState() {
        viewModel.updateVodSorting(CategorySorting.ALPHABETICAL)
        
        verify(settingsManager).setVodCategorySorting(CategorySorting.ALPHABETICAL)
        assertEquals(CategorySorting.ALPHABETICAL, viewModel.state.value.vodSorting)
    }

    @Test
    fun test_updateSeriesSorting_savesToSettingsManager_andUpdatesState() {
        viewModel.updateSeriesSorting(CategorySorting.ALPHABETICAL)
        
        verify(settingsManager).setSeriesCategorySorting(CategorySorting.ALPHABETICAL)
        assertEquals(CategorySorting.ALPHABETICAL, viewModel.state.value.seriesSorting)
    }

    @Test
    fun test_updateSyncFrequency_savesToSettingsManager_andUpdatesState() {
        viewModel.updateSyncFrequency(SyncFrequency.DAILY)

        verify(settingsManager).setSyncFrequency(SyncFrequency.DAILY)
        assertEquals(SyncFrequency.DAILY, viewModel.state.value.syncFrequency)
    }

    @Test
    fun test_initialState_isNotSyncingNow() {
        assertEquals(false, viewModel.state.value.isSyncingNow)
    }

    @Test
    fun test_forceSyncNow_doesNotThrow_whenWorkManagerNotInitialized() {
        // WorkManager.getInstance() throws IllegalStateException outside an
        // instrumented/initialized Android context; forceSyncNow must degrade
        // gracefully instead of crashing the settings screen.
        viewModel.forceSyncNow()

        assertEquals(false, viewModel.state.value.isSyncingNow)
    }
}
