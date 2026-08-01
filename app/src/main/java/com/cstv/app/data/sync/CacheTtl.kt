package com.cstv.app.data.sync

import com.cstv.app.data.local.storage.SyncFrequency

/**
 * Durées de vie du cache, regroupées ici plutôt que dispersées en
 * `companion object` par repository, pour rester révisables d'un seul endroit.
 *
 * La constante `CACHE_EXPIRY_MILLIS` déclarée dans les trois repositories
 * n'était jamais lue : le cache n'expirait donc jamais de lui-même. Ces valeurs
 * sont celles qui deviennent effectives.
 */
object CacheTtl {

    /** Catégories et flux : aligné sur la fréquence par défaut du worker (DAILY). */
    const val CATALOG_MILLIS = 24 * 60 * 60 * 1000L

    /**
     * Détail film / série. Métadonnées quasi immuables : un TTL court
     * multiplierait les `get_vod_info`, principal contributeur au bannissement.
     */
    const val DETAILS_MILLIS = 7 * 24 * 60 * 60 * 1000L

    /**
     * EPG en ligne. 5 minutes était trop agressif pour un programme qui dure
     * une heure. Hors ligne, la donnée est servie quel que soit son âge.
     */
    const val EPG_MILLIS = 15 * 60 * 1000L

    /** Rétention EPG : au-delà, un programme terminé n'a plus d'usage. */
    const val EPG_RETENTION_MILLIS = 6 * 60 * 60 * 1000L

    /**
     * Périmé = jamais synchronisé, ou synchronisé il y a plus que [ttlMillis].
     * Le point de bascule est strict : à exactement TTL, la donnée est encore
     * fraîche.
     */
    fun isExpired(lastSuccessAt: Long, ttlMillis: Long, now: Long): Boolean {
        if (lastSuccessAt <= 0L) return true
        return now - lastSuccessAt > ttlMillis
    }

    /**
     * TTL du catalogue selon la fréquence choisie par l'utilisateur (T7).
     * `null` pour [SyncFrequency.DISABLED] : aucune synchronisation ne doit se
     * déclencher du seul fait de l'âge du cache — seule l'absence totale de
     * catalogue (voir [isExpired] avec `lastSuccessAt <= 0`) reste un besoin
     * de synchronisation dans ce cas.
     */
    fun catalogMillisFor(frequency: SyncFrequency): Long? = when (frequency) {
        SyncFrequency.DAILY -> CATALOG_MILLIS
        SyncFrequency.WEEKLY -> 7 * CATALOG_MILLIS
        SyncFrequency.MONTHLY -> 30 * CATALOG_MILLIS
        SyncFrequency.DISABLED -> null
    }
}
