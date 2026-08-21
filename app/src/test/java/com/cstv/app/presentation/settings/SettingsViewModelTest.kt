package com.cstv.app.presentation.settings

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.data.util.DiagnosticManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.SubtitleBackground
import com.cstv.app.domain.model.SubtitleStyle
import com.cstv.app.domain.model.SubtitleTextColor
import com.cstv.app.domain.model.SubtitleTextSize
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.CloudSyncStatus
import com.cstv.app.domain.usecase.SignOutCstvUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var diagnosticManager: DiagnosticManager

    @Mock
    private lateinit var context: android.content.Context

    @Mock
    private lateinit var cstvAuthRepository: CstvAuthRepository

    @Mock
    private lateinit var cloudSyncManager: CloudSyncManager

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var signOutCstvUseCase: SignOutCstvUseCase

    @Mock
    private lateinit var profileManager: ProfileManager

    @Mock
    private lateinit var externalMetadataRepository: com.cstv.app.domain.repository.ExternalMetadataRepository

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
        whenever(settingsManager.getLiveQualityModeDefault()).thenReturn(false)
        whenever(profileManager.currentProfileId()).thenReturn(7)
        whenever(settingsManager.getAutoPlayNextEpisode(7)).thenReturn(true)
        whenever(cstvAuthRepository.storedEmail()).thenReturn(null)
        whenever(cloudSyncManager.status).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(CloudSyncStatus.Idle))
        whenever(credentialsManager.getCredentials()).thenReturn(
            Credentials(host = "http://panel.test", port = 80, username = "user42", password = "secret")
        )
        whenever(credentialsManager.getLastUserInfo()).thenReturn(null)
        whenever(externalMetadataRepository.observeCoverage()).thenReturn(kotlinx.coroutines.flow.emptyFlow())

        viewModel = buildViewModel()
    }

    private fun buildViewModel() = SettingsViewModel(
        settingsManager,
        diagnosticManager,
        cstvAuthRepository,
        cloudSyncManager,
        credentialsManager,
        signOutCstvUseCase,
        context,
        profileManager,
        externalMetadataRepository
    )

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
    fun `live quality preference is persisted`() {
        assertEquals(false, viewModel.state.value.liveQualityModeDefault)
        viewModel.updateLiveQualityModeDefault(true)
        verify(settingsManager).setLiveQualityModeDefault(true)
        assertEquals(true, viewModel.state.value.liveQualityModeDefault)
    }

    @Test
    fun `episode autoplay preference is profile scoped and marked dirty for cloud sync`() = runTest {
        assertEquals(true, viewModel.state.value.autoPlayNextEpisode)

        viewModel.updateAutoPlayNextEpisode(false)

        verify(settingsManager).setAutoPlayNextEpisode(7, false)
        verify(cloudSyncManager).markDirty(7, com.cstv.app.domain.sync.SyncNamespace.PROFILE_PREFERENCES)
        assertEquals(false, viewModel.state.value.autoPlayNextEpisode)
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

    /**
     * F33 §5.7 : « Se déconnecter du compte CSTV » ne touche jamais aux
     * identifiants Xtream. Depuis B27, `SettingsViewModel` connaît
     * `CredentialsManager` (lecture de l'identifiant affiché dans la carte
     * « Comptes ») : la garantie n'est plus structurelle, on la vérifie.
     */
    @Test
    fun test_signOutCstv_delegatesOnlyToCstvAuthRepository() = runTest {
        viewModel.signOutCstv()

        verify(signOutCstvUseCase).invoke()
        verify(credentialsManager, never()).clearCredentials()
    }

    /** B27 : la carte « Comptes » affiche l'identifiant Xtream courant. */
    @Test
    fun test_initialState_exposesIptvUsernameFromCredentials() {
        assertEquals("user42", viewModel.state.value.iptvUsername)
    }

    /** B27 : sans identifiants mémorisés, repli sur le dernier `UserInfo` connu. */
    @Test
    fun test_initialState_fallsBackToLastUserInfoUsername() {
        whenever(credentialsManager.getCredentials()).thenReturn(null)
        whenever(credentialsManager.getLastUserInfo()).thenReturn(
            UserInfo(
                username = "offline_user",
                auth = true,
                status = "Active",
                expiryDate = "Inconnu",
                maxConnections = 1,
                activeConnections = 0,
                message = "",
                isOfflineSession = true
            )
        )

        assertEquals("offline_user", buildViewModel().state.value.iptvUsername)
    }

    /** B27 : aucune source d'identifiant → la carte retombe sur son libellé de repli. */
    @Test
    fun test_initialState_iptvUsernameNullWhenNoCredentials() {
        whenever(credentialsManager.getCredentials()).thenReturn(null)
        whenever(credentialsManager.getLastUserInfo()).thenReturn(null)

        assertNull(buildViewModel().state.value.iptvUsername)
    }

    @Test
    fun `F46 externalMetadataCoverage is null before the first Room emission`() {
        assertNull(viewModel.state.value.externalMetadataCoverage)
    }

    @Test
    fun `F46 externalMetadataCoverage exposes the first emitted coverage`() {
        val coverage = com.cstv.app.domain.model.ExternalMetadataCoverage(
            total = 10, linked = 8, unresolved = 1,
            movies = com.cstv.app.domain.model.ExternalMetadataCoverageByKind(6, 5, 1),
            series = com.cstv.app.domain.model.ExternalMetadataCoverageByKind(4, 3, 0),
        )
        whenever(externalMetadataRepository.observeCoverage()).thenReturn(kotlinx.coroutines.flow.flowOf(coverage))

        val fresh = buildViewModel()

        assertEquals(coverage, fresh.state.value.externalMetadataCoverage)
    }

    @Test
    fun `F46 externalMetadataCoverage updates are propagated as they are emitted`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(
            com.cstv.app.domain.model.ExternalMetadataCoverage(
                total = 10, linked = 5, unresolved = 0,
                movies = com.cstv.app.domain.model.ExternalMetadataCoverageByKind(10, 5, 0),
                series = com.cstv.app.domain.model.ExternalMetadataCoverageByKind(0, 0, 0),
            )
        )
        whenever(externalMetadataRepository.observeCoverage()).thenReturn(flow)

        val fresh = buildViewModel()
        assertEquals(5, fresh.state.value.externalMetadataCoverage?.linked)

        flow.value = flow.value.copy(linked = 9)
        assertEquals(9, fresh.state.value.externalMetadataCoverage?.linked)
    }
}
