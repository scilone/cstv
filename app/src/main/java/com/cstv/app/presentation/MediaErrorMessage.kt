package com.cstv.app.presentation

import retrofit2.HttpException

/**
 * "HTTP 403 Forbidden" ne dit rien à l'utilisateur, et surtout pas ce qu'il peut
 * faire. Sur un panel Xtream ce code signale presque toujours un quota de
 * requêtes atteint, pas un droit manquant : l'appel repasse quelques secondes
 * plus tard (le client réessaie déjà, cf. XtreamThrottleInterceptor).
 */
fun mediaLoadErrorMessage(error: Throwable, fallback: String): String {
    val code = (error as? HttpException)?.code()
    return when (code) {
        403, 429 -> "Le serveur a refusé la requête (trop de requêtes en cours). Réessaie dans quelques secondes."
        401 -> "Session expirée. Reconnecte-toi pour continuer."
        in 500..599 -> "Le serveur du fournisseur est indisponible pour le moment."
        else -> error.message ?: fallback
    }
}
