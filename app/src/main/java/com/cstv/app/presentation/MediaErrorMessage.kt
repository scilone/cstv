package com.cstv.app.presentation

import retrofit2.HttpException

/**
 * "HTTP 403 Forbidden" ne dit rien à l'utilisateur, et surtout pas ce qu'il peut
 * faire. Sur un panel Xtream ce code signale presque toujours un quota de
 * requêtes atteint, pas un droit manquant : l'appel repasse quelques secondes
 * plus tard (le client réessaie déjà, cf. XtreamThrottleInterceptor).
 */
fun mediaLoadErrorMessage(error: Throwable, fallback: String, isOffline: Boolean = false): String {
    // L'absence de réseau est décidée par l'appelant, pas déduite du type
    // d'exception : un timeout peut très bien survenir en ligne, et le
    // confondre avec un état hors ligne enverrait un message faux.
    if (isOffline) return OFFLINE_PLAYBACK_MESSAGE

    val code = (error as? HttpException)?.code()
    return when (code) {
        403, 429 -> "Le serveur a refusé la requête (trop de requêtes en cours). Réessaie dans quelques secondes."
        401 -> "Session expirée. Reconnecte-toi pour continuer."
        in 500..599 -> "Le serveur du fournisseur est indisponible pour le moment."
        else -> error.message ?: fallback
    }
}

/** Message unique du refus de lecture hors ligne, partagé par tous les points d'entrée. */
const val OFFLINE_PLAYBACK_MESSAGE =
    "Ce contenu nécessite une connexion Internet. Les téléchargements restent lisibles hors ligne."

/** Message de session à revalider, distinct du précédent (cf. T4 §5.5). */
const val REAUTHENTICATION_MESSAGE =
    "Votre session doit être revalidée en ligne pour lire ce contenu."
