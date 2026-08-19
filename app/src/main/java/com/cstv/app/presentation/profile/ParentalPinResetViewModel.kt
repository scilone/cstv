package com.cstv.app.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.R
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.usecase.ParentalPinResetUseCase
import com.cstv.app.domain.usecase.RequestOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Étape du parcours « PIN oublié » (F44 §8.5). */
sealed interface ParentalPinResetStep {
    data class AwaitingOtp(val email: String) : ParentalPinResetStep
    data object AwaitingNewPin : ParentalPinResetStep
}

data class ParentalPinResetUiState(
    val step: ParentalPinResetStep? = null,
    val isSubmitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val completed: Boolean = false,
)

/**
 * Réinitialisation du PIN parental (F44 §8.5) : réutilise le flow OTP
 * existant en mode réauthentification, jamais un endpoint de PIN — le
 * backend n'en a pas et ne peut pas le révéler.
 */
@HiltViewModel
class ParentalPinResetViewModel @Inject constructor(
    private val requestOtpUseCase: RequestOtpUseCase,
    private val parentalPinResetUseCase: ParentalPinResetUseCase,
    private val cstvAuthRepository: CstvAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentalPinResetUiState())
    val state: StateFlow<ParentalPinResetUiState> = _state.asStateFlow()

    /** Lance l'envoi de l'OTP vers l'email du compte connecté. */
    fun start() {
        val email = cstvAuthRepository.storedEmail()
        if (email == null) {
            _state.update { it.copy(errorRes = R.string.parental_pin_reset_no_account) }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorRes = null) }
        viewModelScope.launch {
            val result = requestOtpUseCase(email)
            _state.update {
                it.copy(
                    isSubmitting = false,
                    step = if (result.isSuccess) ParentalPinResetStep.AwaitingOtp(email) else null,
                    errorRes = if (result.isSuccess) null else R.string.parental_pin_reset_otp_failed
                )
            }
        }
    }

    fun submitOtp(code: String) {
        val step = _state.value.step as? ParentalPinResetStep.AwaitingOtp ?: return
        _state.update { it.copy(isSubmitting = true, errorRes = null) }
        viewModelScope.launch {
            val result = parentalPinResetUseCase.verifyReauthentication(step.email, code)
            _state.update {
                it.copy(
                    isSubmitting = false,
                    step = if (result.isSuccess) ParentalPinResetStep.AwaitingNewPin else step,
                    errorRes = if (result.isSuccess) null else R.string.parental_pin_reset_otp_invalid
                )
            }
        }
    }

    /**
     * `false` si la fenêtre de fraîcheur (5 min) a expiré entre-temps : le PIN
     * existant reste intact, l'écran doit renvoyer au tout début du parcours.
     */
    fun submitNewPin(pin: String) {
        val succeeded = parentalPinResetUseCase.resetPin(pin)
        _state.update {
            if (succeeded) it.copy(step = null, completed = true, errorRes = null)
            else it.copy(step = null, errorRes = R.string.parental_pin_reset_expired)
        }
    }

    fun cancel() { _state.update { ParentalPinResetUiState() } }
    fun consumeCompleted() { _state.update { it.copy(completed = false) } }
    fun consumeError() { _state.update { it.copy(errorRes = null) } }
}
