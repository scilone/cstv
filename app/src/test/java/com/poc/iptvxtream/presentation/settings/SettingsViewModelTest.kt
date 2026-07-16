package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.domain.model.SubtitleBackground
import com.poc.iptvxtream.domain.model.SubtitleStyle
import com.poc.iptvxtream.domain.model.SubtitleTextColor
import com.poc.iptvxtream.domain.model.SubtitleTextSize
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
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        whenever(settingsManager.getSubtitleStyle()).thenReturn(SubtitleStyle())

        viewModel = SettingsViewModel(settingsManager, context)
    }

    @Test
    fun test_initialState_loadsFromSettingsManager() {
        val state = viewModel.state.value
        assertEquals(SyncFrequency.DISABLED, state.syncFrequency)
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

    @Test
    fun test_updateSubtitleSize_persistsFullStyle_andUpdatesState() {
        viewModel.updateSubtitleSize(SubtitleTextSize.LARGE)

        verify(settingsManager).setSubtitleStyle(
            SubtitleStyle(size = SubtitleTextSize.LARGE)
        )
        assertEquals(SubtitleTextSize.LARGE, viewModel.state.value.subtitleStyle.size)
    }

    @Test
    fun test_updateSubtitleColorAndBackground_areCombinedIntoOneStyle() {
        viewModel.updateSubtitleColor(SubtitleTextColor.YELLOW)
        viewModel.updateSubtitleBackground(SubtitleBackground.SOLID)

        val style = viewModel.state.value.subtitleStyle
        assertEquals(SubtitleTextColor.YELLOW, style.textColor)
        assertEquals(SubtitleBackground.SOLID, style.background)
        // La taille par défaut n'est pas écrasée par les mises à jour partielles.
        assertEquals(SubtitleTextSize.MEDIUM, style.size)
    }

    @Test
    fun test_subtitleBackground_argb_isBlackWithSelectedAlpha() {
        // Vérifie le calcul ARGB pur (fond noir + alpha) utilisé par le mapping Media3.
        assertEquals(0x00000000L, SubtitleBackground.NONE.argb)
        assertEquals(0x80000000L, SubtitleBackground.SEMI.argb)
        assertEquals(0xCC000000L, SubtitleBackground.SOLID.argb)
    }
}
