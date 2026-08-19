package com.cstv.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.repository.CstvException
import com.cstv.app.domain.model.CstvError
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
    @StringRes val profileActionErrorRes: Int? = null,
    /**
     * F44 : demande de changement de niveau d'âge en attente de PIN.
     * `requiresPinCreation = true` signifie qu'aucun PIN appareil n'existe
     * encore — l'écran doit d'abord en faire créer un (§8.6, première
     * activation) avant de redemander confirmation.
     */
    val pendingAgeRatingChange: PendingAgeRatingChange? = null,
    val ageRatingChangeFeedback: com.cstv.app.domain.model.ParentalPinFeedback? = null
)

data class PendingAgeRatingChange(val profileId: Int, val newMaxAgeRating: Int?, val requiresPinCreation: Boolean)

/**
 * ViewModel partagé pour la sélection de profil (après login) et la gestion des
 * profils (Paramètres). Phase 27.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val cstvAuthRepository: CstvAuthRepository? = null,
    /** F44 : nullable pour ne pas casser les tests existants qui construisent ce ViewModel positionnellement. */
    private val parentalPinStore: com.cstv.app.data.security.ParentalPinStore? = null,
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

    // --- F44 : niveau d'âge protégé par PIN ---

    /**
     * Modifier un niveau autorisé exige toujours le PIN (§7.2/§8.6), y
     * compris pour débrider (`newMaxAgeRating = null`). Si aucun PIN appareil
     * n'existe encore, l'écran doit d'abord en faire créer un.
     */
    fun requestMaxAgeRatingChange(profileId: Int, newMaxAgeRating: Int?) {
        val store = parentalPinStore ?: return
        _state.update {
            it.copy(
                pendingAgeRatingChange = PendingAgeRatingChange(profileId, newMaxAgeRating, requiresPinCreation = !store.hasPin()),
                ageRatingChangeFeedback = null
            )
        }
    }

    /** Première activation (§8.6) : crée le PIN appareil puis applique le changement en attente. */
    fun createDevicePinAndApplyPendingChange(pin: String) {
        val store = parentalPinStore ?: return
        val pending = _state.value.pendingAgeRatingChange ?: return
        store.createPin(pin)
        applyAgeRatingChange(pending)
    }

    /** Confirme un changement en attente avec le PIN existant. */
    fun confirmAgeRatingChange(pin: String) {
        val store = parentalPinStore ?: return
        val pending = _state.value.pendingAgeRatingChange ?: return
        when (val verification = store.verifyPin(pin)) {
            com.cstv.app.data.security.PinVerificationResult.Correct -> applyAgeRatingChange(pending)
            com.cstv.app.data.security.PinVerificationResult.Incorrect ->
                _state.update { it.copy(ageRatingChangeFeedback = com.cstv.app.domain.model.ParentalPinFeedback.Incorrect) }
            is com.cstv.app.data.security.PinVerificationResult.JustLocked ->
                _state.update { it.copy(ageRatingChangeFeedback = com.cstv.app.domain.model.ParentalPinFeedback.Locked(verification.remainingMillis)) }
            is com.cstv.app.data.security.PinVerificationResult.Locked ->
                _state.update { it.copy(ageRatingChangeFeedback = com.cstv.app.domain.model.ParentalPinFeedback.Locked(verification.remainingMillis)) }
        }
    }

    fun cancelAgeRatingChange() {
        _state.update { it.copy(pendingAgeRatingChange = null, ageRatingChangeFeedback = null) }
    }

    private fun applyAgeRatingChange(pending: PendingAgeRatingChange) {
        _state.update { it.copy(pendingAgeRatingChange = null, ageRatingChangeFeedback = null) }
        viewModelScope.launch {
            try {
                profileRepository.updateMaxAgeRating(pending.profileId, pending.newMaxAgeRating)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(profileActionErrorRes = error.toProfileActionMessageRes()) }
            }
        }
    }

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
                _state.update { it.copy(profileActionErrorRes = error.toProfileActionMessageRes()) }
            }
        }
    }

    /** Backend quota rejections (T14) get their own message; anything else stays generic. */
    @StringRes
    private fun Throwable.toProfileActionMessageRes(): Int = when ((this as? CstvException)?.cstvError) {
        CstvError.ProfileLimit -> R.string.cstv_profile_limit
        CstvError.StorageQuota -> R.string.cstv_storage_quota
        else -> R.string.profile_cloud_action_failed
    }
}
