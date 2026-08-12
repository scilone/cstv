package com.cstv.app.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.usecase.AutoLoginUseCase
import com.cstv.app.domain.usecase.GetSavedCredentialsUseCase
import com.cstv.app.domain.usecase.LoginUseCase
import com.cstv.app.domain.usecase.LogoutUseCase
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
    private val logoutUseCase: LogoutUseCase,
    private val autoLoginUseCase: AutoLoginUseCase,
    private val catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _autoLoginState = MutableStateFlow<AutoLoginState>(AutoLoginState.Checking)
    val autoLoginState: StateFlow<AutoLoginState> = _autoLoginState.asStateFlow()

    private val _savedCredentials = MutableStateFlow<Credentials?>(null)
    val savedCredentials: StateFlow<Credentials?> = _savedCredentials.asStateFlow()

    private var autoLoginStarted = false

    /** Called only after the CSTV gate has resolved (F33). */
    fun startAutoLogin() {
        if (autoLoginStarted) return
        autoLoginStarted = true
        _savedCredentials.value = getSavedCredentialsUseCase()
        _autoLoginState.value = AutoLoginState.Checking
        viewModelScope.launch {
            // Le repli hors ligne est décidé côté AuthRepository : un échec
            // réseau ne renvoie plus systématiquement à l'écran de connexion,
            // ce qui empêchait l'application d'atteindre ses écrans sans réseau.
            val outcome = autoLoginUseCase()
            _autoLoginState.value = when (outcome) {
                is AutoLoginOutcome.NoCredentials -> AutoLoginState.NoCredentials
                is AutoLoginOutcome.Online -> AutoLoginState.Success(outcome.userInfo, offline = false)
                is AutoLoginOutcome.OfflineSession -> AutoLoginState.Success(outcome.userInfo, offline = true)
                is AutoLoginOutcome.Rejected -> AutoLoginState.Error(outcome.message)
            }

            // Déclencheur STARTUP : jamais bloquant, jamais attendu par l'UI, et
            // sans effet si le catalogue est frais ou l'appareil hors ligne.
            // C'est aussi lui qui rattrape une synchronisation planifiée en
            // échec, puisqu'un échec ne renouvelle pas la date de fraîcheur.
            if (outcome is AutoLoginOutcome.Online) {
                runCatching { catalogSyncManager.syncIfStale() }
            }
        }
    }

    fun login(credentials: Credentials) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val userInfo = loginUseCase(credentials)
                _loginState.value = LoginState.Success(userInfo)

                // Une connexion manuelle est le seul moment où le catalogue peut
                // être vide alors que le réseau est disponible : première
                // installation, ou purge qui vient de suivre un changement de
                // compte. Sans ce déclencheur, les écrans resteraient vides
                // jusqu'au worker planifié ou à un rafraîchissement manuel.
                // syncIfStale() ne coûte rien si le catalogue est déjà frais —
                // cas d'une reconnexion au même compte.
                //
                // runCatching : un échec de synchronisation ne doit pas faire
                // basculer en erreur une connexion qui, elle, a réussi.
                runCatching { catalogSyncManager.syncIfStale() }
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
        autoLoginStarted = false
    }
}
