package com.cstv.app.domain.model

/**
 * Retour affiché par le composant de saisie PIN (F44, §8.6) après une
 * tentative infructueuse. Volontairement pauvre en détail (§8.8) : aucun
 * message ne distingue un mauvais PIN d'un autre échec exploitable.
 */
sealed interface ParentalPinFeedback {
    data object Incorrect : ParentalPinFeedback
    data class Locked(val remainingMillis: Long) : ParentalPinFeedback
}
