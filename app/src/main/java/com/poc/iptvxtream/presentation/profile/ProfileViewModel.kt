package com.poc.iptvxtream.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.Profile
import com.poc.iptvxtream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: Int = -1,
    val initialized: Boolean = false
)

/**
 * ViewModel partagé pour la sélection de profil (après login) et la gestion des
 * profils (Paramètres). Phase 27.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeProfiles().collect { profiles ->
                _state.update {
                    it.copy(profiles = profiles, activeProfileId = profileRepository.currentProfileId())
                }
            }
        }
    }

    /**
     * Garantit un profil actif valide. Retourne true s'il faut afficher l'écran
     * de sélection (plusieurs profils), false si on peut aller directement à la
     * Home (0 ou 1 profil).
     */
    suspend fun ensureInitializedAndNeedsSelection(): Boolean {
        val profiles = profileRepository.ensureInitialized()
        _state.update {
            it.copy(
                profiles = profiles,
                activeProfileId = profileRepository.currentProfileId(),
                initialized = true
            )
        }
        return profiles.size > 1
    }

    fun selectProfile(id: Int) {
        profileRepository.setActiveProfile(id)
        _state.update { it.copy(activeProfileId = id) }
    }

    fun createProfile(name: String, avatarId: Int) {
        viewModelScope.launch { profileRepository.createProfile(name, avatarId) }
    }

    fun renameProfile(id: Int, name: String) {
        viewModelScope.launch { profileRepository.renameProfile(id, name) }
    }

    fun updateAvatar(id: Int, avatarId: Int) {
        viewModelScope.launch { profileRepository.updateAvatar(id, avatarId) }
    }

    fun deleteProfile(id: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(profileRepository.deleteProfile(id)) }
    }
}
