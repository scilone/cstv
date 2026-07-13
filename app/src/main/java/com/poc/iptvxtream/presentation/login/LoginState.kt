package com.poc.iptvxtream.presentation.login

import com.poc.iptvxtream.domain.model.UserInfo

sealed interface LoginState {
    object Idle : LoginState
    object Loading : LoginState
    data class Success(val userInfo: UserInfo) : LoginState
    data class Error(val message: String) : LoginState
}

sealed interface AutoLoginState {
    object Checking : AutoLoginState
    object NoCredentials : AutoLoginState
    data class Success(val userInfo: UserInfo) : AutoLoginState
    data class Error(val message: String) : AutoLoginState
}
