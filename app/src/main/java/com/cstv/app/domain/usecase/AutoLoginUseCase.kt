package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class AutoLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val restoreIptvCredentials: RestoreIptvCredentialsUseCase,
    private val backup: IptvCredentialsBackupRepository,
    @javax.inject.Named("applicationScope") private val applicationScope: CoroutineScope
) {
    suspend operator fun invoke(): AutoLoginOutcome {
        val result = authRepository.autoLogin()
        return when (result) {
            AutoLoginOutcome.NoCredentials -> restoreIptvCredentials()
            is AutoLoginOutcome.Online -> {
                authRepository.getSavedCredentials()?.let { credentials ->
                    applicationScope.launch { backup.onAuthenticated(credentials) }
                }
                result
            }
            // Un refus du panel ne révoque plus la sauvegarde cloud.
            //
            // `auth != 1` couvre bien plus que « mot de passe faux » : les panels
            // Xtream répondent aussi ainsi quand le compte est momentanément au
            // plafond de connexions simultanées — par exemple un second appareil
            // qu'on associe pendant qu'un film tourne sur le premier. Une seule
            // réponse de ce genre suffisait à supprimer les identifiants du
            // cloud, donc à désassocier **tous** les appareils, y compris celui
            // qui lisait le film. La sauvegarde ne se retire désormais que sur
            // décision explicite : case de consentement décochée, déconnexion
            // IPTV, ou déconnexion du compte CSTV.
            else -> result
        }
    }
}
