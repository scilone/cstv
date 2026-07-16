package com.poc.iptvxtream.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.usecase.GetSavedCredentialsUseCase
import com.poc.iptvxtream.domain.usecase.LoginUseCase
import com.poc.iptvxtream.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val getSavedCredentialsUseCase: GetSavedCredentialsUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _autoLoginState = MutableStateFlow<AutoLoginState>(AutoLoginState.Checking)
    val autoLoginState: StateFlow<AutoLoginState> = _autoLoginState.asStateFlow()

    private val _savedCredentials = MutableStateFlow<Credentials?>(null)
    val savedCredentials: StateFlow<Credentials?> = _savedCredentials.asStateFlow()

    init {
        checkAutoLogin()
    }

    private fun checkAutoLogin() {
        val creds = getSavedCredentialsUseCase()
        _savedCredentials.value = creds
        if (creds != null && creds.rememberMe) {
            _autoLoginState.value = AutoLoginState.Checking
            viewModelScope.launch {
                try {
                    val userInfo = loginUseCase(creds)
                    _autoLoginState.value = AutoLoginState.Success(userInfo)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _autoLoginState.value = AutoLoginState.Error(e.message ?: "La connexion automatique a échoué.")
                }
            }
        } else {
            _autoLoginState.value = AutoLoginState.NoCredentials
        }
    }

    fun login(credentials: Credentials) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val userInfo = loginUseCase(credentials)
                _loginState.value = LoginState.Success(userInfo)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _loginState.value = LoginState.Error(e.message ?: "Une erreur inconnue est survenue.")
            }
        }
    }

    fun setError(message: String) {
        _loginState.value = LoginState.Error(message)
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    fun logout() {
        logoutUseCase()
        _loginState.value = LoginState.Idle
        _autoLoginState.value = AutoLoginState.NoCredentials
        _savedCredentials.value = null
    }
}
