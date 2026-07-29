package com.cstv.app.presentation.settings

import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.util.DiagnosticManager
import com.cstv.app.domain.model.SubtitleBackground
import com.cstv.app.domain.model.SubtitleStyle
import com.cstv.app.domain.model.SubtitleTextColor
import com.cstv.app.domain.model.SubtitleTextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private lateinit var diagnosticManager: DiagnosticManager

    @Mock
    private lateinit var context: android.content.Context

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock default behaviors
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        whenever(settingsManager.getSubtitleStyle()).thenReturn(SubtitleStyle())
        whenever(settingsManager.getDebugModeEnabled()).thenReturn(false)

        viewModel = SettingsViewModel(settingsManager, diagnosticManager, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_initialState_loadsFromSettingsManager() {
        val state = viewModel.state.value
        assertEquals(SyncFrequency.DISABLED, state.syncFrequency)
        assertEquals(false, state.debugModeEnabled)
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
        assertEquals(SubtitleTextSize.MEDIUM, style.size)
    }

    @Test
    fun test_subtitleBackground_argb_isBlackWithSelectedAlpha() {
        assertEquals(0x00000000L, SubtitleBackground.NONE.argb)
        assertEquals(0x80000000L, SubtitleBackground.SEMI.argb)
        assertEquals(0xCC000000L, SubtitleBackground.SOLID.argb)
    }

    @Test
    fun test_updateDebugModeEnabled_true_startsLogging() {
        viewModel.updateDebugModeEnabled(true)

        verify(settingsManager).setDebugModeEnabled(true)
        verify(diagnosticManager).startLogging()
        assertEquals(true, viewModel.state.value.debugModeEnabled)
    }

    @Test
    fun test_updateDebugModeEnabled_false_stopsLogging() {
        viewModel.updateDebugModeEnabled(false)

        verify(settingsManager).setDebugModeEnabled(false)
        verify(diagnosticManager).stopLogging()
        assertEquals(false, viewModel.state.value.debugModeEnabled)
    }

    @Test
    fun test_uploadDiagnosticLogs_success() = runTest {
        whenever(diagnosticManager.uploadLogs()).thenReturn("https://paste.rs/abcd")
        
        viewModel.uploadDiagnosticLogs()
        
        assertEquals(false, viewModel.state.value.isUploadingLogs)
        assertEquals("https://paste.rs/abcd", viewModel.state.value.uploadedLogsUrl)
        assertNull(viewModel.state.value.uploadLogsError)
    }

    @Test
    fun test_uploadDiagnosticLogs_error() = runTest {
        whenever(diagnosticManager.uploadLogs()).thenThrow(RuntimeException("Network Error"))
        
        viewModel.uploadDiagnosticLogs()
        
        assertEquals(false, viewModel.state.value.isUploadingLogs)
        assertNull(viewModel.state.value.uploadedLogsUrl)
        assertEquals("Network Error", viewModel.state.value.uploadLogsError)
    }

    @Test
    fun test_clearUploadStatus_resetsState() {
        viewModel.clearUploadStatus()
        
        val state = viewModel.state.value
        assertEquals(false, state.isUploadingLogs)
        assertNull(state.uploadedLogsUrl)
        assertNull(state.uploadLogsError)
    }
}
