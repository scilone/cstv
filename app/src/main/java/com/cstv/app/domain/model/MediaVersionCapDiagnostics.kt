package com.cstv.app.domain.model

import com.cstv.app.di.IptvLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * F39 §8.7, correction F39-R8 : diagnostic agrégé du plafond défensif de 20
 * versions par `linkKey`. Un `linkKey` est dérivé du titre normalisé (T21) —
 * l'exposer en clair dans les logs répétés à chaque troncature n'est pas le
 * « diagnostic agrégé » attendu par la review. Ce compteur ne journalise
 * qu'un total, jamais l'identité de la clé tronquée.
 */
object MediaVersionCapDiagnostics {
    private val vodTruncations = AtomicInteger(0)
    private val seriesTruncations = AtomicInteger(0)

    fun recordVodTruncation() {
        val total = vodTruncations.incrementAndGet()
        IptvLog.w("F39", "plafond de versions VOD atteint (§8.7) — total agrégé depuis le démarrage : $total")
    }

    fun recordSeriesTruncation() {
        val total = seriesTruncations.incrementAndGet()
        IptvLog.w("F39", "plafond de versions série atteint (§8.7) — total agrégé depuis le démarrage : $total")
    }
}
