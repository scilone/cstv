package com.cstv.app.presentation.player.core

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Révision produit F40 du 2026-08-18 : quand le mode automatique doit descendre en qualité, ce
 * choix reste actif au zapping suivant sur la même chaîne — la meilleure qualité n'est plus
 * retentée à chaque ouverture — jusqu'à ce qu'une tentative explicite de cette meilleure qualité
 * confirme qu'elle fonctionne à nouveau (elle reste stable, [LiveTvViewModel] appelle
 * [confirmTopHealthy] dès qu'elle atteint READY sans repli), ou jusqu'à expiration de la fenêtre
 * de rappel (retour utilisateur du 2026-08-18 : au moins une journée).
 *
 * Persisté (`SharedPreferences`), pas seulement en mémoire process : l'app peut être tuée par le
 * système à tout moment sur Android TV, un simple singleton en mémoire perdait le repli au premier
 * redémarrage. `nowMs` doit être une horloge murale (`System.currentTimeMillis()`), jamais
 * `System.nanoTime()` (origine arbitraire par processus, invalide d'un redémarrage à l'autre).
 */
@Singleton
class LiveQualityDowngradeMemory @Inject constructor(context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Dernière qualité retenue pour `linkKey`, si elle est toujours dans sa fenêtre de rappel. */
    fun rememberedStreamId(linkKey: String, nowMs: Long): Int? {
        if (linkKey.isBlank()) return null
        val streamId = prefs.getInt(streamKey(linkKey), NO_STREAM_ID).takeIf { it != NO_STREAM_ID } ?: return null
        val recordedAtMs = prefs.getLong(timeKey(linkKey), 0L)
        if (nowMs - recordedAtMs >= RECALL_WINDOW_MS) {
            clear(linkKey)
            return null
        }
        return streamId
    }

    /** `true` si le prochain zapping doit retenter la meilleure qualité malgré une mémoire existante. */
    fun shouldProbeTop(linkKey: String, nowMs: Long): Boolean = rememberedStreamId(linkKey, nowMs) == null

    /** Le mode automatique vient de descendre sous la meilleure candidate : on la retient. */
    fun recordDowngrade(linkKey: String, streamId: Int, nowMs: Long) {
        if (linkKey.isBlank()) return
        prefs.edit().putInt(streamKey(linkKey), streamId).putLong(timeKey(linkKey), nowMs).apply()
    }

    /** La meilleure candidate vient de prouver sa stabilité (READY sans repli) : oublie le repli mémorisé. */
    fun confirmTopHealthy(linkKey: String) = clear(linkKey)

    private fun clear(linkKey: String) {
        prefs.edit().remove(streamKey(linkKey)).remove(timeKey(linkKey)).apply()
    }

    private fun streamKey(linkKey: String) = "$KEY_STREAM_PREFIX$linkKey"
    private fun timeKey(linkKey: String) = "$KEY_TIME_PREFIX$linkKey"

    private companion object {
        const val PREFS_NAME = "live_quality_downgrade_prefs"
        const val KEY_STREAM_PREFIX = "stream_"
        const val KEY_TIME_PREFIX = "time_"
        const val NO_STREAM_ID = -1
        /** Retour utilisateur du 2026-08-18 : au moins une journée sans confirmation. */
        const val RECALL_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
