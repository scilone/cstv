package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.DynamicBaseUrlInterceptor
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.api.XtreamRequestGate
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val credentialsManager: CredentialsManager,
    private val baseUrlInterceptor: DynamicBaseUrlInterceptor,
    private val requestGate: XtreamRequestGate
) : AuthRepository {

    override suspend fun login(credentials: Credentials): UserInfo {
        baseUrlInterceptor.hostUrl = credentials.baseUrl

        try {
            val response = requestGate.acquire { apiService.login(credentials.username, credentials.password) }
            val userInfoDto = response.userInfo

            if (userInfoDto == null || userInfoDto.auth != 1) {
                throw InvalidCredentialsException("Identifiants incorrects.")
            }

            if (userInfoDto.status != "Active") {
                val formattedExpiry = formatExpiryDate(userInfoDto.expDate)
                throw AccountExpiredException("Compte inactif ou expiré.", formattedExpiry)
            }

            val formattedExpiry = formatExpiryDate(userInfoDto.expDate)
            
            if (userInfoDto.expDate != null && userInfoDto.expDate > 0L) {
                val currentTimestamp = System.currentTimeMillis() / 1000L
                if (userInfoDto.expDate < currentTimestamp) {
                    throw AccountExpiredException("Compte expiré depuis le $formattedExpiry.", formattedExpiry)
                }
            }

            return UserInfo(
                username = userInfoDto.username ?: credentials.username,
                auth = true,
                status = userInfoDto.status,
                expiryDate = formattedExpiry,
                maxConnections = userInfoDto.maxConnections ?: 1,
                activeConnections = userInfoDto.activeCons ?: 0,
                message = userInfoDto.message ?: ""
            )

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            when (e) {
                is InvalidCredentialsException -> throw e
                is AccountExpiredException -> throw e
                is SocketTimeoutException -> throw NetworkTimeoutException("Connexion au serveur expirée (Timeout). Veuillez réessayer.", e)
                is ConnectException, is UnknownHostException -> throw ServerUnreachableException("Serveur injoignable. Vérifiez l'URL du serveur.", e)
                is IOException -> throw ServerUnreachableException("Erreur réseau. Vérifiez votre connexion internet.", e)
                else -> throw UnknownAuthException("Une erreur inconnue est survenue : ${e.localizedMessage}", e)
            }
        }
    }

    override fun saveCredentials(credentials: Credentials) {
        credentialsManager.saveCredentials(credentials)
    }

    // Déchiffrement Keystore (IPC vers le daemon keystore) : coûteux et
    // synchrone dans CredentialsManager, ne doit jamais tourner sur Main
    // (voir historique de LoginViewModel.checkAutoLogin, appelé depuis
    // ViewModel.init avant la première frame Compose).
    override suspend fun getSavedCredentials(): Credentials? = withContext(Dispatchers.IO) {
        credentialsManager.getCredentials()
    }

    override fun clearCredentials() {
        credentialsManager.clearCredentials()
    }

    private fun formatExpiryDate(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "Illimité"
        return try {
            val date = Date(timestamp * 1000L)
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "Inconnu"
        }
    }
}
