package com.cstv.app.domain.repository

import com.cstv.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {

    fun observeProfiles(): Flow<List<Profile>>

    suspend fun getProfiles(): List<Profile>

    /**
     * Garantit qu'au moins un profil existe et qu'un profil actif valide est
     * sélectionné. Retourne la liste des profils après normalisation.
     * À appeler après connexion / auto-login, avant d'accéder à la Home.
     */
    suspend fun ensureInitialized(): List<Profile>

    suspend fun createProfile(name: String, avatarId: Int): Profile

    suspend fun renameProfile(id: Int, name: String)

    suspend fun updateAvatar(id: Int, avatarId: Int)

    /**
     * Supprime un profil et toutes ses données (favoris, historique, positions).
     * Refuse de supprimer le dernier profil restant (retourne false).
     */
    suspend fun deleteProfile(id: Int): Boolean

    fun currentProfileId(): Int

    fun setActiveProfile(id: Int)

    val activeProfileId: Flow<Int>
}
