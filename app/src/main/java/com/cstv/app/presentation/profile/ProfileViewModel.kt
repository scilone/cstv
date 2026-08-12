package com.cstv.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.R
import androidx.annotation.StringRes
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
    val autoStartProfileId: Int = -1,
    val cloudCrudEnabled: Boolean = false,
    @StringRes val profileActionErrorRes: Int? = null
)

/**
 * ViewModel partagé pour la sélection de profil (après login) et la gestion des
 * profils (Paramètres). Phase 27.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val cstvAuthRepository: CstvAuthRepository? = null
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
        cstvAuthRepository?.let { repository ->
            viewModelScope.launch {
                repository.sessionState.collect { session ->
                    _state.update {
                        it.copy(
                            cloudCrudEnabled = session is CstvSessionState.Active,
                            profileActionErrorRes = if (session is CstvSessionState.Offline) {
                                R.string.profile_cloud_offline
                            } else {
                                it.profileActionErrorRes
                            }
                        )
                    }
                }
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
        mutateProfile { profileRepository.createProfile(name, avatarId) }
    }

    fun renameProfile(id: Int, name: String) {
        mutateProfile { profileRepository.renameProfile(id, name) }
    }

    fun updateAvatar(id: Int, avatarId: Int) {
        mutateProfile { profileRepository.updateAvatar(id, avatarId) }
    }

    fun deleteProfile(id: Int, onResult: (Boolean) -> Unit = {}) {
        if (!canMutateProfiles()) return onResult(false)
        viewModelScope.launch {
            try {
                onResult(profileRepository.deleteProfile(id))
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(profileActionErrorRes = R.string.profile_cloud_action_failed) }
                onResult(false)
            }
        }
    }

    fun clearProfileActionError() = _state.update { it.copy(profileActionErrorRes = null) }

    private fun canMutateProfiles(): Boolean {
        if (_state.value.cloudCrudEnabled || cstvAuthRepository == null) return true
        _state.update { it.copy(profileActionErrorRes = R.string.profile_cloud_offline) }
        return false
    }

    private fun mutateProfile(action: suspend () -> Unit) {
        if (!canMutateProfiles()) return
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(profileActionErrorRes = R.string.profile_cloud_action_failed) }
            }
        }
    }
}
