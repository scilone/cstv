package com.cstv.app.data.repository

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Force un rafraîchissement TMDB au premier accès de chaque cache après le
 * démarrage du process (tendances, films populaires, séries populaires).
 *
 * Le cache persistant n'est jamais effacé : il reste disponible en repli si
 * TMDB est injoignable au lancement (mode avion, DNS KO, quota atteint), sinon
 * la Home perdrait ses lignes TMDB hors ligne alors qu'elles s'affichaient
 * avant.
 */
@Singleton
class CatalogSessionRefreshGate @Inject constructor() {

    private val consumed = ConcurrentHashMap<String, Boolean>()

    /**
     * Retourne `true` une seule fois par clé et par lancement d'application.
     * Atomique : deux coroutines concurrentes ne peuvent pas consommer la même
     * clé toutes les deux.
     */
    fun consumeFirstAccess(key: String): Boolean = consumed.putIfAbsent(key, true) == null
}
