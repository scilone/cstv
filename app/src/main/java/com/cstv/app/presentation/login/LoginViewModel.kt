package com.cstv.app.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.AutoLoginOutcome
import android.content.Context
import com.cstv.app.R
import com.cstv.app.domain.model.AutoLoginRejection
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvCloudBackupNotice
import com.cstv.app.domain.model.IptvCloudBackupNotifier
import com.cstv.app.domain.usecase.AutoLoginUseCase
import com.cstv.app.domain.usecase.LoginUseCase
import com.cstv.app.domain.usecase.LogoutUseCase
import com.cstv.app.domain.usecase.SetIptvCloudBackupConsentUseCase
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val autoLoginUseCase: AutoLoginUseCase,
    private val catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager,
    private val backupRepository: IptvCredentialsBackupRepository,
    private val setCloudBackupConsent: SetIptvCloudBackupConsentUseCase,
    private val backupNotifier: IptvCloudBackupNotifier,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _autoLoginState = MutableStateFlow<AutoLoginState>(AutoLoginState.Checking)
    val autoLoginState: StateFlow<AutoLoginState> = _autoLoginState.asStateFlow()

    private val _cloudBackupVisible = MutableStateFlow(false)
    val cloudBackupVisible: StateFlow<Boolean> = _cloudBackupVisible.asStateFlow()
    private val _cloudBackupEnabled = MutableStateFlow(false)
    val cloudBackupEnabled: StateFlow<Boolean> = _cloudBackupEnabled.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var autoLoginStarted = false

    init {
        viewModelScope.launch {
            backupNotifier.notices.collect { notice ->
                _messages.send(context.getString(when (notice) {
                    IptvCloudBackupNotice.SAVE_DEFERRED -> R.string.login_cloud_backup_deferred
                    IptvCloudBackupNotice.DELETE_DEFERRED -> R.string.login_cloud_backup_delete_deferred
                }))
            }
        }
    }

    /** Called only after the CSTV gate has resolved (F33). */
    fun startAutoLogin() {
        if (autoLoginStarted) return
        autoLoginStarted = true
        refreshCloudBackupState()
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
                is AutoLoginOutcome.Rejected -> AutoLoginState.Error(messageFor(outcome))
            }

            // Déclencheur STARTUP : jamais bloquant, jamais attendu par l'UI, et
            // sans effet si le catalogue est frais ou l'appareil hors ligne.
            // C'est aussi lui qui rattrape une synchronisation planifiée en
            // échec, puisqu'un échec ne renouvelle pas la date de fraîcheur.
            if (outcome is AutoLoginOutcome.Online) {
                runCatching { catalogSyncManager.syncIfStale() }
            }
            refreshCloudBackupState()
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

    fun onCloudBackupChange(enabled: Boolean) {
        viewModelScope.launch {
            when (setCloudBackupConsent(enabled)) {
                com.cstv.app.domain.model.IptvBackupOutcome.Deferred -> _messages.send(
                    context.getString(R.string.login_cloud_backup_delete_deferred)
                )
                else -> Unit
            }
            refreshCloudBackupState()
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
        _cloudBackupEnabled.value = false
        autoLoginStarted = false
    }

    private fun refreshCloudBackupState() {
        viewModelScope.launch(Dispatchers.IO) {
            val visible = backupRepository.linkedAccountId() != null
            val enabled = if (visible) backupRepository.isConsentEnabled() else false
            _cloudBackupVisible.value = visible
            _cloudBackupEnabled.value = enabled
        }
    }

    private fun messageFor(outcome: AutoLoginOutcome.Rejected): String = when (outcome.reason) {
        AutoLoginRejection.CLOUD_CREDENTIALS_INVALID -> context.getString(R.string.login_cloud_credentials_invalid)
        AutoLoginRejection.CLOUD_RESTORE_FAILED -> context.getString(R.string.login_cloud_restore_failed)
        AutoLoginRejection.CLOUD_RESTORE_UNAVAILABLE -> context.getString(R.string.cstv_server_unavailable)
        else -> outcome.message
    }
}
