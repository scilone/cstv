package com.cstv.app.presentation.player.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Révision produit F40 du 2026-08-18 : quand le mode automatique doit descendre en qualité, ce
 * choix reste actif au zapping suivant sur la même chaîne — la meilleure qualité n'est plus
 * retentée à chaque ouverture — jusqu'à ce qu'une tentative explicite de cette meilleure qualité
 * confirme qu'elle fonctionne à nouveau (elle reste stable, [LiveTvViewModel] appelle
 * [confirmTopHealthy] dès qu'elle atteint READY sans repli). Portée process (mémoire seule, pas de
 * `DataStore`) : la qualité réseau est une condition volatile d'un instant donné, pas une
 * préférence utilisateur durable comme les versions F39.
 */
@Singleton
class LiveQualityDowngradeMemory @Inject constructor() {
    private data class Entry(val streamId: Int, val recordedAtMs: Long)

    private val downgrades = mutableMapOf<String, Entry>()

    /** Dernière qualité retenue pour `linkKey`, si elle est toujours dans sa fenêtre de rappel. */
    fun rememberedStreamId(linkKey: String, nowMs: Long): Int? {
        val entry = downgrades[linkKey] ?: return null
        return entry.streamId.takeIf { nowMs - entry.recordedAtMs < PROBE_COOLDOWN_MS }
    }

    /** `true` si le prochain zapping doit retenter la meilleure qualité malgré une mémoire existante. */
    fun shouldProbeTop(linkKey: String, nowMs: Long): Boolean = rememberedStreamId(linkKey, nowMs) == null

    /** Le mode automatique vient de descendre sous la meilleure candidate : on la retient. */
    fun recordDowngrade(linkKey: String, streamId: Int, nowMs: Long) {
        if (linkKey.isBlank()) return
        downgrades[linkKey] = Entry(streamId, nowMs)
    }

    /** La meilleure candidate vient de prouver sa stabilité (READY sans repli) : oublie le repli mémorisé. */
    fun confirmTopHealthy(linkKey: String) {
        downgrades.remove(linkKey)
    }

    private companion object {
        /** Au-delà, on retente la meilleure qualité même sans confirmation explicite. */
        const val PROBE_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
