package com.cstv.app.data.util

import android.view.Choreographer
import com.cstv.app.di.IptvLog

/**
 * Diagnostic « animations très lentes » (retour utilisateur du 2026-08-18),
 * distinct de [MainThreadWatchdog] qui ne voit que les blocages complets
 * (≥700ms). Ici on mesure l'écart entre deux callbacks `Choreographer`
 * consécutifs : à 60Hz, un frame prend ~16ms — un écart nettement supérieur
 * signale un frame dropé (jank), même sans blocage total du thread principal.
 *
 * Coalescé pour éviter le spam : n'écrit qu'un log toutes les [LOG_COOLDOWN_MS]
 * au minimum, avec le pire écart observé sur la fenêtre écoulée.
 */
object FrameJankWatchdog {

    private const val TAG = "JANK"
    private const val JANK_THRESHOLD_MS = 100L
    private const val LOG_COOLDOWN_MS = 3_000L

    @Volatile
    private var started = false

    private var lastFrameTimeNanos = 0L
    private var worstJankMs = 0L
    private var jankCount = 0
    private var lastLoggedAt = 0L

    fun start() {
        if (started) return
        started = true
        Choreographer.getInstance().postFrameCallback(::onFrame)
    }

    private fun onFrame(frameTimeNanos: Long) {
        if (lastFrameTimeNanos != 0L) {
            val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000
            if (deltaMs >= JANK_THRESHOLD_MS) {
                jankCount++
                if (deltaMs > worstJankMs) worstJankMs = deltaMs
                val now = System.currentTimeMillis()
                if (now - lastLoggedAt >= LOG_COOLDOWN_MS) {
                    IptvLog.w(
                        TAG,
                        "$jankCount frame(s) lente(s) (>${JANK_THRESHOLD_MS}ms) sur la fenêtre écoulée, pire = ${worstJankMs}ms"
                    )
                    lastLoggedAt = now
                    jankCount = 0
                    worstJankMs = 0
                }
            }
        }
        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(::onFrame)
    }
}
