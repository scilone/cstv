package com.poc.iptvxtream.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.usecase.GetSavedCredentialsUseCase
import com.poc.iptvxtream.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val getSavedCredentialsUseCase: GetSavedCredentialsUseCase
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _savedCredentials = MutableStateFlow<Credentials?>(null)
    val savedCredentials: StateFlow<Credentials?> = _savedCredentials.asStateFlow()

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        val creds = getSavedCredentialsUseCase()
        _savedCredentials.value = creds
    }

    fun login(credentials: Credentials) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val userInfo = loginUseCase(credentials)
                _loginState.value = LoginState.Success(userInfo)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Une erreur inconnue est survenue.")
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}
