package com.cstv.app.data.remote

/**
 * T29 débit : lecture de l'en-tête HTTP `Retry-After` renvoyé par le backend CSTV sur un 429.
 *
 * Le backend n'émet que la forme `delta-seconds` (entier). La forme HTTP-date de la RFC 9110 est
 * volontairement rejetée (`null`) plutôt que devinée : l'appelant retombe alors sur un délai de
 * cadence court, ce qui est préférable à un délai calculé sur une horloge d'appareil désynchronisée.
 *
 * Secondes en entrée, **millisecondes** en sortie — la conversion est faite ici, une seule fois, pour
 * qu'aucun appelant n'ait à s'en souvenir. Bornée à [MAX_RETRY_AFTER_MILLIS] : un en-tête aberrant ne
 * doit jamais endormir la file d'hydratation pour des heures.
 */
object RetryAfterHeader {

    internal const val MAX_RETRY_AFTER_MILLIS = 60L * 60 * 1000

    fun parseMillis(rawHeader: String?): Long? {
        val seconds = rawHeader?.trim()?.toLongOrNull() ?: return null
        if (seconds < 0) return null
        return (seconds * 1000L).coerceAtMost(MAX_RETRY_AFTER_MILLIS)
    }
}
