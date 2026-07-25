package com.cstv.app.data.remote.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priorise les requêtes réseau écran (navigation utilisateur) sur le trafic
 * d'arrière-plan (sync planifié/forcé, enrichissement du casting) qui partage
 * le même compte Xtream, souvent limité à une poignée de connexions
 * concurrentes.
 *
 * Deux garanties ici :
 * - le travail d'arrière-plan cède activement la main tant qu'une requête écran
 *   est en cours, au lieu d'entrer en file FIFO neutre avec elle ;
 * - il ne part **jamais** plus d'une requête d'arrière-plan à la fois, tous
 *   catalogues confondus. Chaque repository boucle séquentiellement de son
 *   côté, mais les enrichissements films et séries tournaient en parallèle, et
 *   en parallèle du sync : sur un compte limité à une connexion, ça suffit à
 *   faire refuser des requêtes.
 */
@Singleton
class XtreamRequestGate @Inject constructor() {

    private val activeForegroundCount = AtomicInteger(0)
    private val throttledUntilMs = AtomicLong(0L)
    private val backgroundLane = Mutex()

    // Seam de test : la classe reste injectable sans paramètre.
    internal var nowMs: () -> Long = { System.nanoTime() / 1_000_000 }

    /**
     * Le panel vient de refuser une requête pour cause de quota (403/429).
     * Le trafic d'arrière-plan s'arrête le temps que la fenêtre de comptage du
     * panel se vide : sans ça, le trickle d'enrichissement continue de la
     * remplir et c'est la requête écran suivante qui se fait refuser.
     */
    fun notifyThrottled() {
        throttledUntilMs.set(nowMs() + THROTTLE_COOLDOWN_MS)
    }

    fun isThrottled(): Boolean = nowMs() < throttledUntilMs.get()

    suspend fun <T> acquire(block: suspend () -> T): T {
        return if (RequestPriority.currentLevel() == RequestPriority.Level.BACKGROUND) {
            backgroundLane.withLock {
                // Re-testé sous le verrou : une requête écran a pu démarrer
                // pendant l'attente du tour.
                awaitForegroundClear()
                block()
            }
        } else {
            activeForegroundCount.incrementAndGet()
            try {
                block()
            } finally {
                activeForegroundCount.decrementAndGet()
            }
        }
    }

    private suspend fun awaitForegroundClear() {
        while (activeForegroundCount.get() > 0 || isThrottled()) {
            delay(YIELD_CHECK_INTERVAL_MS)
        }
    }

    companion object {
        private const val YIELD_CHECK_INTERVAL_MS = 100L
        private const val THROTTLE_COOLDOWN_MS = 30_000L
    }
}
