package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.DynamicBaseUrlInterceptor
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.sync.AccountKey
import com.cstv.app.data.sync.CatalogServerKey
import com.cstv.app.data.sync.CatalogSyncStateInitializer
import com.cstv.app.data.sync.MediaRefAccountBinder
import com.cstv.app.domain.model.*
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.TrailerRepository
import com.cstv.app.domain.sync.CatalogSyncManager
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
    private val requestGate: XtreamRequestGate,
    private val trailerRepository: TrailerRepository,
    private val networkMonitor: NetworkMonitor,
    private val syncStateDao: CatalogSyncStateDao,
    private val syncStateInitializer: CatalogSyncStateInitializer,
    private val catalogSyncManager: CatalogSyncManager,
    private val mediaRefAccountBinder: MediaRefAccountBinder
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

            val userInfo = UserInfo(
                username = userInfoDto.username ?: credentials.username,
                auth = true,
                status = userInfoDto.status,
                expiryDate = formattedExpiry,
                maxConnections = userInfoDto.maxConnections ?: 1,
                activeConnections = userInfoDto.activeCons ?: 0,
                message = userInfoDto.message ?: ""
            )

            // Marqueurs d'une validation réseau réelle : c'est eux, et rien
            // d'autre, qui autoriseront plus tard une session hors ligne.
            credentialsManager.setLastSuccessfulLoginAt(System.currentTimeMillis())
            credentialsManager.setLastValidatedAccountKey(AccountKey.from(credentials))
            credentialsManager.saveLastUserInfo(userInfo)

            // T20-6 : rattache les identités héritées (sentinelle accountKey='')
            // au premier compte authentifié — idempotent, sans effet dès que le
            // rattachement a déjà eu lieu.
            mediaRefAccountBinder.bindTo(AccountKey.from(credentials))

            // Purge à la connexion si le compte a changé, avant que le moindre
            // écran ne lise le catalogue du compte précédent.
            catalogSyncManager.onAccountAuthenticated(CatalogServerKey.from(credentials))

            return userInfo

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

    /**
     * Arbre de décision volontairement explicite : le seul motif d'accorder une
     * session sans réseau est une panne, jamais un refus du panel.
     */
    override suspend fun autoLogin(): AutoLoginOutcome {
        val credentials = credentialsManager.getCredentials()
        if (credentials == null) {
            return AutoLoginOutcome.NoCredentials
        }

        // Reprise des horodatages d'avant T4 : ici, les identifiants en place
        // sont encore ceux du dernier compte synchronisé.
        syncStateInitializer.ensureInitialized()

        if (!networkMonitor.isCurrentlyOnline()) {
            return offlineSessionOrRejection(credentials)
        }

        return try {
            AutoLoginOutcome.Online(login(credentials))
        } catch (e: InvalidCredentialsException) {
            // Réponse du serveur, pas une panne : la connexion au compte ne peut
            // pas être contournée hors ligne.
            credentialsManager.clearOfflineSessionValidation()
            AutoLoginOutcome.Rejected(
                AutoLoginRejection.INVALID_CREDENTIALS,
                e.message ?: "Identifiants incorrects."
            )
        } catch (e: AccountExpiredException) {
            credentialsManager.clearOfflineSessionValidation()
            AutoLoginOutcome.Rejected(
                AutoLoginRejection.ACCOUNT_EXPIRED,
                e.message ?: "Compte expiré."
            )
        } catch (e: ServerUnreachableException) {
            offlineSessionOrRejection(credentials)
        } catch (e: NetworkTimeoutException) {
            offlineSessionOrRejection(credentials)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AutoLoginOutcome.Rejected(
                AutoLoginRejection.UNKNOWN,
                e.message ?: "La connexion automatique a échoué."
            )
        }
    }

    private suspend fun offlineSessionOrRejection(credentials: Credentials): AutoLoginOutcome {
        val validatedBefore = credentialsManager.getLastSuccessfulLoginAt() > 0L
        val lastUserInfo = credentialsManager.getLastUserInfo()
        val accountKey = AccountKey.from(credentials)

        // Un catalogue partiel issu d'une première synchronisation interrompue
        // n'est pas un catalogue prêt pour le hors-ligne (cf. MIN à 0).
        val states = syncStateDao.getAll()
        val catalogServerKey = CatalogServerKey.from(credentials)
        val catalogComplete = states.isNotEmpty() &&
            states.firstOrNull()?.accountKey == catalogServerKey &&
            CatalogSection.CATALOG_SECTIONS.all { section ->
                (states.firstOrNull { it.section == section }?.lastSuccessAt ?: 0L) > 0L
            }

        return if (
            validatedBefore &&
            credentialsManager.getLastValidatedAccountKey() == accountKey &&
            lastUserInfo != null &&
            catalogComplete
        ) {
            // La clé ne peut pas avoir changé : la session repose sur les
            // identifiants déjà enregistrés.
            baseUrlInterceptor.hostUrl = credentials.baseUrl
            AutoLoginOutcome.OfflineSession(lastUserInfo)
        } else {
            AutoLoginOutcome.Rejected(
                AutoLoginRejection.NO_LOCAL_SESSION,
                "La première synchronisation nécessite une connexion Internet."
            )
        }
    }

    override fun saveCredentials(credentials: Credentials) {
        trailerRepository.clearSessionCache()
        credentialsManager.saveCredentials(credentials)
    }

    override fun getSavedCredentials(): Credentials? {
        return credentialsManager.getCredentials()
    }

    override fun clearCredentials() {
        trailerRepository.clearSessionCache()
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
