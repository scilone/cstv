package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.data.local.storage.AppAccentColor
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
        whenever(settingsManager.getTvCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getVodCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getSeriesCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        whenever(settingsManager.getSubtitleStyle()).thenReturn(SubtitleStyle())
        whenever(settingsManager.getAccentColor()).thenReturn(AppAccentColor.LAVANDE)

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

    @Test
    fun test_updateAccentColor_savesToSettingsManager_andUpdatesState() {
        viewModel.updateAccentColor(AppAccentColor.SARCELLE)
        
        verify(settingsManager).setAccentColor(AppAccentColor.SARCELLE)
        assertEquals(AppAccentColor.SARCELLE, viewModel.state.value.accentColor)
    }
}
