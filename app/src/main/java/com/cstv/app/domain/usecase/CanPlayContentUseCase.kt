package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.network.NetworkMonitor
import javax.inject.Inject

sealed interface PlaybackAvailability {
    /** En ligne, ou média déjà téléchargé. */
    object Allowed : PlaybackAvailability

    /** Hors ligne et non téléchargé : le flux exige le serveur Xtream. */
    object RequiresConnection : PlaybackAvailability

    /**
     * Session locale sans validation possible. Distingué de
     * [RequiresConnection] parce qu'une erreur hors ligne et une erreur
     * d'authentification n'appellent pas la même action de l'utilisateur.
     */
    object RequiresReauthentication : PlaybackAvailability
}

/**
 * Autorisation de lecture. Ne construit aucune URL : il dit seulement si la
 * lecture peut partir.
 */
class CanPlayContentUseCase @Inject constructor(
    private val downloadDao: DownloadDao,
    private val networkMonitor: NetworkMonitor,
    private val credentialsManager: CredentialsManager
) {
    /**
     * [contentId] suit la convention des téléchargements (`movie_123`,
     * `episode_456`) ; `null` pour un flux Live, jamais téléchargeable.
     */
    suspend operator fun invoke(contentId: String?): PlaybackAvailability {
        // Ordre d'évaluation : un média téléchargé est lisible quelle que soit
        // la connectivité — c'est le chemin cache Media3 déjà opérationnel.
        if (contentId != null) {
            val downloaded = downloadDao.getByContentId(contentId)
            if (downloaded != null && downloaded.status == DownloadStatus.COMPLETED.name) {
                return PlaybackAvailability.Allowed
            }
        }

        if (credentialsManager.getCredentials() == null) {
            return PlaybackAvailability.RequiresReauthentication
        }

        return if (networkMonitor.isCurrentlyOnline()) {
            PlaybackAvailability.Allowed
        } else {
            PlaybackAvailability.RequiresConnection
        }
    }
}
