package com.cstv.app.data.remote.api

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priorise les requêtes réseau écran (navigation utilisateur) sur le trafic
 * d'arrière-plan (sync planifié/forcé, enrichissement du casting) qui partage
 * le même compte Xtream, souvent limité à une poignée de connexions
 * concurrentes.
 *
 * Le plafonnement dur (1 requête à la fois) reste assuré par
 * `OkHttpClient.Dispatcher` (voir AppModule) — ce gate ajoute juste une
 * priorité par-dessus : le travail d'arrière-plan cède activement la main
 * tant qu'une requête écran est en cours, au lieu d'entrer en file FIFO
 * neutre avec elle.
 */
@Singleton
class XtreamRequestGate @Inject constructor() {

    private val activeForegroundCount = AtomicInteger(0)

    suspend fun <T> acquire(block: suspend () -> T): T {
        return if (RequestPriority.currentLevel() == RequestPriority.Level.BACKGROUND) {
            awaitForegroundClear()
            block()
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
        while (activeForegroundCount.get() > 0) {
            delay(YIELD_CHECK_INTERVAL_MS)
        }
    }

    companion object {
        private const val YIELD_CHECK_INTERVAL_MS = 100L
    }
}
