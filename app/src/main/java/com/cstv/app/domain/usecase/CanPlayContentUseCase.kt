package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.repository.ContentClassificationRepository
import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.domain.model.AgeRating
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.model.ParentalAccessPolicy
import com.cstv.app.domain.model.ParentalActionType
import com.cstv.app.domain.model.AccessDecision
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
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

    /**
     * F44 : le profil actif est bridé et cette œuvre dépasse son niveau
     * autorisé (ou n'est pas classifiée). [mediaUid] identifie précisément le
     * média pour l'écran de saisie PIN et le grant one-shot émis à sa suite.
     */
    data class RequiresParentalPin(val reason: BlockReason, val mediaUid: String) : PlaybackAvailability
}

/**
 * Autorisation de lecture. Ne construit aucune URL : il dit seulement si la
 * lecture peut partir.
 *
 * F44 : appelé par toutes les surfaces qui lancent réellement une lecture
 * (Accueil, VOD, Séries, Favoris — Live TV est hors périmètre F44), c'est
 * l'unique verrou parental pour la lecture : un deep link, une reprise ou un
 * second écran passent tous par ce même use case (§8.4).
 */
class CanPlayContentUseCase @Inject constructor(
    private val isContentDownloadedUseCase: IsContentDownloadedUseCase,
    private val networkMonitor: NetworkMonitor,
    private val credentialsManager: CredentialsManager,
    private val profileRepository: ProfileRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val classificationRepository: ContentClassificationRepository,
    private val parentalAccessPolicy: ParentalAccessPolicy,
    private val oneShotPlaybackGrantStore: OneShotPlaybackGrantStore,
) {
    /**
     * [contentId] suit la convention des téléchargements (`movie_123`,
     * `episode_456`) ; `null` pour un flux Live, jamais téléchargeable ni
     * concerné par F44 (aucune source de classification fiable pour le direct).
     *
     * [oneShotGrantNonce] : nonce émis par `ParentalPinStore` après un PIN
     * correct sur l'écran de refus (F44 §8.4) — consommé ici, jamais réutilisable
     * pour une relecture ultérieure du même média.
     */
    suspend operator fun invoke(contentId: String?, oneShotGrantNonce: String? = null): PlaybackAvailability {
        // Ordre d'évaluation : un média téléchargé est lisible quelle que soit
        // la connectivité — c'est le chemin cache Media3 déjà opérationnel. F44
        // §8.7 : aucune revalidation parentale d'un téléchargement existant.
        if (isContentDownloadedUseCase(contentId)) return PlaybackAvailability.Allowed

        if (credentialsManager.getCredentials() == null) {
            return PlaybackAvailability.RequiresReauthentication
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            return PlaybackAvailability.RequiresConnection
        }

        if (contentId != null) {
            val profileId = profileRepository.currentProfileId()
            if (oneShotGrantNonce != null && oneShotPlaybackGrantStore.consume(profileId, contentId, oneShotGrantNonce)) {
                return PlaybackAvailability.Allowed
            }
            evaluateParentalAccess(contentId, profileId)?.let { return it }
        }

        return PlaybackAvailability.Allowed
    }

    /**
     * `null` = autorisé (profil non bridé, ou classification permise). Un
     * profil non bridé ne déclenche jamais [ContentClassificationRepository]
     * (§8.2, aucun surcoût sur le parcours adulte).
     */
    private suspend fun evaluateParentalAccess(contentId: String, profileId: Int): PlaybackAvailability.RequiresParentalPin? {
        val activeProfile = profileRepository.getProfiles().firstOrNull { it.id == profileId }
        val maxAgeRating = AgeRating.fromValueOrNull(activeProfile?.maxAgeRating) ?: return null

        val target = resolveClassificationTarget(contentId)
            ?: return PlaybackAvailability.RequiresParentalPin(BlockReason.UNCLASSIFIED, contentId)
        val classification = classificationRepository.classificationFor(target.kind, target.title, target.year, target.providerId)
            // Compatibilité des tests/fournisseurs legacy qui implémentent
            // encore la résolution par titre uniquement.
            ?: classificationRepository.classificationFor(target.kind, target.title, target.year)

        val decision = parentalAccessPolicy.evaluate(maxAgeRating, classification, ParentalActionType.PLAY)
        return (decision as? AccessDecision.PinRequired)?.let {
            PlaybackAvailability.RequiresParentalPin(it.reason, contentId)
        }
    }

    private data class ClassificationTarget(val kind: MediaClassificationKind, val title: String, val year: Int?, val providerId: Int)

    private suspend fun resolveClassificationTarget(contentId: String): ClassificationTarget? = when {
        contentId.startsWith(MOVIE_PREFIX) -> {
            val streamId = contentId.removePrefix(MOVIE_PREFIX).toIntOrNull()
            val stream = streamId?.let { vodRepository.getStreamById(it) }
            stream?.let { ClassificationTarget(MediaClassificationKind.MOVIE, it.cleanTitle.ifBlank { it.name }, it.releaseYear, it.streamId) }
        }
        contentId.startsWith(EPISODE_PREFIX) -> {
            val episodeId = contentId.removePrefix(EPISODE_PREFIX).toIntOrNull()
            // F44 : classification de la série entière, jamais par épisode (décision étape 1).
            val seriesId = episodeId?.let { seriesRepository.getSeriesIdForEpisode(it) }
            val series = seriesId?.let { seriesRepository.getStreamById(it) }
            series?.let { ClassificationTarget(MediaClassificationKind.SERIES, it.cleanTitle.ifBlank { it.name }, it.releaseYear, it.seriesId) }
        }
        else -> null
    }

    private companion object {
        const val MOVIE_PREFIX = "movie_"
        const val EPISODE_PREFIX = "episode_"
    }
}
