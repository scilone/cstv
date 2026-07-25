package com.cstv.app.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Les panels Xtream limitent le débit par compte et répondent 403 (parfois 429)
 * au-delà du quota, y compris sur `player_api.php`. Le trickle d'enrichissement
 * du casting suffit à remplir cette fenêtre : la requête écran qui tombe au
 * mauvais moment est refusée alors que le même média s'ouvre sans problème
 * quelques secondes plus tard.
 *
 * Deux réponses ici : réessayer l'appel, et prévenir le [XtreamRequestGate] pour
 * que le trafic d'arrière-plan cesse de nourrir le compteur du panel.
 *
 * Toutes les requêtes Xtream sont des GET, le rejeu est donc sans effet de bord.
 */
class XtreamThrottleInterceptor(
    private val gate: XtreamRequestGate,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while (response.code in THROTTLED_CODES && attempt < RETRY_DELAYS_MS.size) {
            gate.notifyThrottled()
            response.close()
            sleep(RETRY_DELAYS_MS[attempt])
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    companion object {
        private val THROTTLED_CODES = setOf(403, 429)
        private val RETRY_DELAYS_MS = longArrayOf(600L, 1_800L)
    }
}
