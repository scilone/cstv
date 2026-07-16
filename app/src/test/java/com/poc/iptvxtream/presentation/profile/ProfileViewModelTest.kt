package com.poc.iptvxtream.presentation.profile

import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.Profile
import com.poc.iptvxtream.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Mock
    private lateinit var profileRepository: ProfileRepository

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ProfileViewModel

    private fun profile(id: Int) = Profile(id, "Profil $id", 0, 0L)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(profiles: List<Profile>): ProfileViewModel {
        doReturn(flowOf(profiles)).whenever(profileRepository).observeProfiles()
        val vm = ProfileViewModel(profileRepository)
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun init_collecteProfilsEtProfilActif() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(2)
        viewModel = createViewModel(listOf(profile(1), profile(2)))

        val state = viewModel.state.value
        assertEquals(2, state.profiles.size)
        assertEquals(2, state.activeProfileId)
        assertFalse(state.initialized)
    }

    @Test
    fun ensureInitialized_zeroOuUnProfil_pasDEcranDeSelection() = runTest {
        // Le repository normalise : même partie de 0 profil, il en retourne 1.
        val normalized = listOf(profile(1))
        whenever(profileRepository.ensureInitialized()).thenReturn(normalized)
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        viewModel = createViewModel(normalized)

        val needsSelection = viewModel.ensureInitializedAndNeedsSelection()

        assertFalse(needsSelection)
        val state = viewModel.state.value
        assertTrue(state.initialized)
        assertEquals(normalized, state.profiles)
        assertEquals(1, state.activeProfileId)
    }

    @Test
    fun ensureInitialized_plusieursProfils_ecranDeSelectionRequis() = runTest {
        val profiles = listOf(profile(1), profile(2), profile(3))
        whenever(profileRepository.ensureInitialized()).thenReturn(profiles)
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        viewModel = createViewModel(profiles)

        assertTrue(viewModel.ensureInitializedAndNeedsSelection())
        assertTrue(viewModel.state.value.initialized)
    }

    @Test
    fun selectProfile_activeLeProfilEtMetAJourLEtat() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        viewModel = createViewModel(listOf(profile(1), profile(2)))

        viewModel.selectProfile(2)

        verify(profileRepository).setActiveProfile(2)
        assertEquals(2, viewModel.state.value.activeProfileId)
    }

    @Test
    fun deleteProfile_dernierProfilRefuse() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.deleteProfile(1)).thenReturn(false)
        viewModel = createViewModel(listOf(profile(1)))

        var result: Boolean? = null
        viewModel.deleteProfile(1) { result = it }
        testDispatcher.scheduler.runCurrent()

        assertEquals(false, result)
    }

    @Test
    fun deleteProfile_profilNonDernierSupprime() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.deleteProfile(2)).thenReturn(true)
        viewModel = createViewModel(listOf(profile(1), profile(2)))

        var result: Boolean? = null
        viewModel.deleteProfile(2) { result = it }
        testDispatcher.scheduler.runCurrent()

        assertEquals(true, result)
        verify(profileRepository).deleteProfile(2)
    }
}
