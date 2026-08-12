package com.cstv.app.presentation.profile

import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.StartupProfileResolution
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var cstvAuthRepository: CstvAuthRepository

    // MutableStateFlow contrôlé (F31-R1) : seul moyen de prouver que l'état UI
    // se met bien à jour quand la source émet, et non un simple relais figé
    // d'un `flowOf` immuable.
    private lateinit var autoStartFlow: MutableStateFlow<Int>

    private fun profile(id: Int, name: String = "P$id") = Profile(id, name, 0, id.toLong())

    private fun buildViewModel(
        initialAutoStart: Int = -1,
        cstvRepository: CstvAuthRepository? = null
    ): ProfileViewModel {
        whenever(profileRepository.observeProfiles()).thenReturn(flowOf(listOf(profile(5), profile(6))))
        autoStartFlow = MutableStateFlow(initialAutoStart)
        whenever(profileRepository.autoStartProfileId).thenReturn(autoStartFlow)
        return ProfileViewModel(profileRepository, cstvRepository)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_ensureInitializedAndNeedsSelection_appliesValidAutoStart_bypassesSelector() = runTest {
        val viewModel = buildViewModel()
        whenever(profileRepository.resolveStartupProfile()).thenReturn(
            StartupProfileResolution(listOf(profile(5), profile(6)), needsSelection = false)
        )
        doReturn(6).whenever(profileRepository).currentProfileId()
        doReturn(6).whenever(profileRepository).currentAutoStartProfileId()

        val needsSelection = viewModel.ensureInitializedAndNeedsSelection()

        assertFalse(needsSelection)
        assertEquals(6, viewModel.state.value.autoStartProfileId)
        assertFalse(viewModel.state.value.profiles.isEmpty())
    }

    @Test
    fun test_ensureInitializedAndNeedsSelection_noAutoStart_needsSelectionWithMultipleProfiles() = runTest {
        val viewModel = buildViewModel()
        whenever(profileRepository.resolveStartupProfile()).thenReturn(
            StartupProfileResolution(listOf(profile(5), profile(6)), needsSelection = true)
        )
        doReturn(5).whenever(profileRepository).currentProfileId()
        doReturn(-1).whenever(profileRepository).currentAutoStartProfileId()

        val needsSelection = viewModel.ensureInitializedAndNeedsSelection()

        assertTrue(needsSelection)
    }

    @Test
    fun test_selectProfile_manualChange_doesNotModifyAutoStart() = runTest {
        val viewModel = buildViewModel()

        viewModel.selectProfile(6)

        assertEquals(6, viewModel.state.value.activeProfileId)
        verify(profileRepository, never()).setAutoStartProfile(any())
    }

    @Test
    fun test_setAutoStartProfile_delegatesToRepository() = runTest {
        val viewModel = buildViewModel()

        viewModel.setAutoStartProfile(6)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(profileRepository).setAutoStartProfile(6)
    }

    @Test
    fun test_autoStartProfileId_propagatesReactively_toUiState_onReplacement() = runTest {
        val viewModel = buildViewModel(initialAutoStart = 5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, viewModel.state.value.autoStartProfileId)

        // Le remplacement A(5) → B(6) est une seule émission : jamais deux
        // valeurs observables entre les deux, jamais aucune valeur retombée.
        whenever(profileRepository.setAutoStartProfile(6)).doSuspendableAnswer { autoStartFlow.value = 6 }
        viewModel.setAutoStartProfile(6)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.state.value.autoStartProfileId)
    }

    @Test
    fun test_autoStartProfileId_propagatesReactively_toUiState_onDisable() = runTest {
        val viewModel = buildViewModel(initialAutoStart = 5)
        testDispatcher.scheduler.advanceUntilIdle()

        whenever(profileRepository.setAutoStartProfile(null)).doSuspendableAnswer { autoStartFlow.value = -1 }
        viewModel.setAutoStartProfile(null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(-1, viewModel.state.value.autoStartProfileId)
    }

    @Test
    fun test_renameProfile_doesNotChangeAutoStartInUiState() = runTest {
        val viewModel = buildViewModel(initialAutoStart = 6)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameProfile(5, "Nouveau nom")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.state.value.autoStartProfileId)
        verify(profileRepository, never()).setAutoStartProfile(any())
    }

    @Test
    fun test_updateAvatar_doesNotChangeAutoStartInUiState() = runTest {
        val viewModel = buildViewModel(initialAutoStart = 6)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateAvatar(5, 3)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.state.value.autoStartProfileId)
        verify(profileRepository, never()).setAutoStartProfile(any())
    }

    @Test
    fun test_offlineCstv_disablesProfileCrud_andPreventsLocalMutation() = runTest {
        val session = CstvSession("token", "account", "mail@example.test", Long.MAX_VALUE)
        whenever(cstvAuthRepository.sessionState).thenReturn(MutableStateFlow(CstvSessionState.Offline(session)))
        val viewModel = buildViewModel(cstvRepository = cstvAuthRepository)

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.renameProfile(5, "Nouveau nom")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.cloudCrudEnabled)
        assertEquals(com.cstv.app.R.string.profile_cloud_offline, viewModel.state.value.profileActionErrorRes)
        verify(profileRepository, never()).renameProfile(any(), any())
    }
}
