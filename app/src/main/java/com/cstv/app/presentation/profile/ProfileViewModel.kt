package com.cstv.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.repository.ProfileRepository
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
    val initialized: Boolean = false,
    val autoStartProfileId: Int = -1
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
        viewModelScope.launch {
            profileRepository.autoStartProfileId.collect { id ->
                _state.update { it.copy(autoStartProfileId = id) }
            }
        }
    }

    /**
     * Garantit un profil actif valide et applique le profil de démarrage
     * automatique s'il en existe un. Retourne true s'il faut afficher l'écran
     * de sélection, false si on peut aller directement à la Home (profil
     * automatique appliqué, ou 0/1 profil sans réglage automatique).
     */
    suspend fun ensureInitializedAndNeedsSelection(): Boolean {
        val resolution = profileRepository.resolveStartupProfile()
        _state.update {
            it.copy(
                profiles = resolution.profiles,
                activeProfileId = profileRepository.currentProfileId(),
                autoStartProfileId = profileRepository.currentAutoStartProfileId(),
                initialized = true
            )
        }
        return resolution.needsSelection
    }

    fun selectProfile(id: Int) {
        profileRepository.setActiveProfile(id)
        _state.update { it.copy(activeProfileId = id) }
    }

    /** Aucun effet sur le profil actif de la session en cours. `null` désactive. */
    fun setAutoStartProfile(id: Int?) {
        viewModelScope.launch { profileRepository.setAutoStartProfile(id) }
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
