package com.cstv.app.presentation.cstv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.repository.CstvException
import com.cstv.app.domain.model.CstvError
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.usecase.RequestOtpUseCase
import com.cstv.app.domain.usecase.ResolveCstvSessionUseCase
import com.cstv.app.domain.usecase.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.StringRes
import com.cstv.app.R

data class CstvAuthUiState(val email: String = "", val otpRequested: Boolean = false, val loading: Boolean = false, @StringRes val messageRes: Int? = null)

@HiltViewModel
class CstvAuthViewModel @Inject constructor(
    private val repository: CstvAuthRepository,
    private val requestOtpUseCase: RequestOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val resolveCstvSessionUseCase: ResolveCstvSessionUseCase
) : ViewModel() {
    val sessionState: StateFlow<CstvSessionState> = repository.sessionState
    private val mutableUi = MutableStateFlow(CstvAuthUiState())
    val uiState: StateFlow<CstvAuthUiState> = mutableUi.asStateFlow()
    init {
        viewModelScope.launch { resolveCstvSessionUseCase() }
        viewModelScope.launch {
            repository.sessionState.collect { state ->
                if (state is CstvSessionState.SignedOut && state.reason != com.cstv.app.domain.model.SignedOutReason.NoSession && mutableUi.value.email.isBlank()) {
                    repository.storedEmail()?.let { email -> mutableUi.value = mutableUi.value.copy(email = email) }
                }
            }
        }
    }
    fun updateEmail(email: String) { mutableUi.value = mutableUi.value.copy(email = email, messageRes = null) }
    fun requestOtp() = viewModelScope.launch {
        val email = mutableUi.value.email.trim()
        if (!EMAIL_REGEX.matches(email)) { mutableUi.value = mutableUi.value.copy(messageRes = R.string.cstv_invalid_email); return@launch }
        mutableUi.value = mutableUi.value.copy(loading = true, messageRes = null)
        val result = requestOtpUseCase(email)
        mutableUi.value = mutableUi.value.copy(loading = false, otpRequested = result.isSuccess, messageRes = result.exceptionOrNull().toUiMessageRes())
    }
    fun verifyOtp(code: String) = viewModelScope.launch {
        if (code.length != 6 || !code.all(Char::isDigit)) { mutableUi.value = mutableUi.value.copy(messageRes = R.string.cstv_invalid_otp_format); return@launch }
        mutableUi.value = mutableUi.value.copy(loading = true, messageRes = null)
        val result = verifyOtpUseCase(mutableUi.value.email, code)
        mutableUi.value = mutableUi.value.copy(loading = false, messageRes = result.exceptionOrNull().toUiMessageRes())
    }
    fun resetOtp() { mutableUi.value = mutableUi.value.copy(otpRequested = false, messageRes = null) }
    fun retry() = viewModelScope.launch { resolveCstvSessionUseCase() }
    fun signOut() = repository.signOut()
    private fun Throwable?.toUiMessageRes(): Int? = when ((this as? CstvException)?.cstvError) {
        null -> this?.let { R.string.cstv_unknown_error }
        CstvError.InvalidOtp -> R.string.cstv_invalid_otp
        CstvError.InvalidEmail -> R.string.cstv_invalid_email
        CstvError.RateLimited -> R.string.cstv_rate_limited
        CstvError.Unavailable -> R.string.cstv_network_unavailable
        CstvError.ServerError -> R.string.cstv_server_unavailable
        CstvError.Rejected -> R.string.cstv_session_rejected
        CstvError.Disabled -> R.string.cstv_account_disabled
        CstvError.Expired -> R.string.cstv_account_expired
        CstvError.LastProfile -> R.string.profile_delete_error
        else -> R.string.cstv_unknown_error
    }
    private companion object { val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }
}
