package com.cstv.app.domain.model

import javax.inject.Inject
import javax.inject.Singleton

/** Pourquoi une lecture/un téléchargement est refusé à un profil bridé (F44, §7.2/§7.6). */
enum class BlockReason { TOO_MATURE, UNCLASSIFIED }

/** Action soumise à la garde parentale (F44, §8.4). */
enum class ParentalActionType { PLAY, DOWNLOAD }

/** Décision de `ParentalAccessPolicy` (F44, §8.4). */
sealed interface AccessDecision {
    data object Allowed : AccessDecision
    data class PinRequired(val reason: BlockReason) : AccessDecision
}

/**
 * Règle d'accès parentale pure (F44, §8.4/§9.2) : ne fait jamais d'appel
 * réseau et ne connaît pas `ContentClassificationRepository`. C'est
 * l'appelant (use case, F44 tâche 5) qui décide s'il doit même récupérer une
 * classification — pour un profil non bridé (`maxAgeRating == null`), il ne
 * doit jamais le faire (§8.2, aucun surcoût sur le parcours adulte).
 *
 * Les grants one-shot ([OneShotPlaybackGrantStore]) ne sont pas un paramètre
 * de cette règle : l'appelant les consulte *avant* d'appeler [evaluate], et
 * saute l'appel entièrement si un grant valide couvre déjà cette lecture
 * précise. La modification du niveau autorisé d'un profil n'utilise jamais un
 * grant de lecture : elle revérifie le PIN indépendamment (§8.4).
 */
@Singleton
class ParentalAccessPolicy @Inject constructor() {
    /**
     * @param maxAgeRating niveau autorisé du profil ; `null` = profil non bridé.
     * @param classification classification de l'œuvre ; `null` = inconnue.
     */
    fun evaluate(
        maxAgeRating: AgeRating?,
        classification: AgeRating?,
        action: ParentalActionType,
    ): AccessDecision {
        if (maxAgeRating == null) return AccessDecision.Allowed
        if (classification == null) return AccessDecision.PinRequired(BlockReason.UNCLASSIFIED)
        return if (classification.value <= maxAgeRating.value) {
            AccessDecision.Allowed
        } else {
            AccessDecision.PinRequired(BlockReason.TOO_MATURE)
        }
    }
}
