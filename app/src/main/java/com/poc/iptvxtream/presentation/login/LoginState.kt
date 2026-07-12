package com.poc.iptvxtream.presentation.login

import com.poc.iptvxtream.domain.model.UserInfo

sealed interface LoginState {
    object Idle : LoginState
    object Loading : LoginState
    data class Success(val userInfo: UserInfo) : LoginState
    data class Error(val message: String) : LoginState
}
