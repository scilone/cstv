package com.cstv.app.domain.repository

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo

interface AuthRepository {
    suspend fun login(credentials: Credentials): UserInfo

    /**
     * Connexion automatique au démarrage. Contrairement à [login], un échec
     * réseau n'est pas un refus : une session hors ligne est accordée si — et
     * seulement si — une validation réseau a déjà réussi et que le catalogue
     * local est complet pour ce compte.
     */
    suspend fun autoLogin(): AutoLoginOutcome
    fun saveCredentials(credentials: Credentials)
    fun getSavedCredentials(): Credentials?
    fun clearCredentials()
}
