package com.cstv.app.domain.repository

import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.StartupProfileResolution
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
     * F44 : `maxAgeRating = null` débride explicitement le profil. Synchronisé
     * dans le cloud comme le nom/avatar (arbitrage étape 3) ; l'appel du PIN
     * reste entièrement à la charge de l'appelant (ViewModel), ce repository
     * ne connaît pas le PIN.
     */
    suspend fun updateMaxAgeRating(id: Int, maxAgeRating: Int?)

    /**
     * Supprime un profil et toutes ses données (favoris, historique, positions).
     * Refuse de supprimer le dernier profil restant (retourne false).
     */
    suspend fun deleteProfile(id: Int): Boolean

    fun currentProfileId(): Int

    fun setActiveProfile(id: Int)

    val activeProfileId: Flow<Int>

    val autoStartProfileId: Flow<Int>

    fun currentAutoStartProfileId(): Int

    /** null désactive le démarrage automatique. */
    suspend fun setAutoStartProfile(id: Int?)

    /**
     * Normalise les profils (cf. [ensureInitialized]) puis applique le profil
     * de démarrage automatique s'il désigne encore un profil existant.
     * En cas d'erreur temporaire de lecture, ne modifie jamais le choix
     * mémorisé et retombe sur `needsSelection = true`.
     */
    suspend fun resolveStartupProfile(): StartupProfileResolution

    /** Removes every profile-scoped datum before another CSTV account is imported. */
    suspend fun purgeAllProfiles()
}
