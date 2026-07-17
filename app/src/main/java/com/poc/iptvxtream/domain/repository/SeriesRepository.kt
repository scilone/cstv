package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesStream

interface SeriesRepository {
    suspend fun getSeriesCategories(forceRefresh: Boolean): List<SeriesCategory>
    suspend fun getSeriesStreams(categoryId: String, forceRefresh: Boolean): List<SeriesStream>
    suspend fun getSeriesDetails(seriesId: Int): SeriesDetails
    suspend fun savePlaybackPosition(episodeStreamId: Int, positionMs: Long, durationMs: Long)
    suspend fun getPlaybackPosition(episodeStreamId: Int): Pair<Long, Long>?
    suspend fun clearPlaybackPosition(episodeStreamId: Int)

    /**
     * Enrichit (acteurs/réalisateur/genre) les séries qui en manquent encore,
     * par lots successifs jusqu'à [maxBatches] ou jusqu'à ce que le catalogue
     * soit à jour. Contrairement au trickle paresseux déclenché par une simple
     * consultation de liste, cet appel attend la fin du travail (utilisé par
     * le rafraîchissement forcé/planifié, Phase 22).
     *
     * maxBatches reste volontairement bas : de nombreux panels Xtream limitent
     * le nombre de connexions concurrentes par compte (voir UserInfo.
     * maxConnections). Un plafond trop élevé peut monopoliser cette connexion
     * plusieurs minutes et faire paraître les écrans de liste bloqués en
     * chargement pendant le sync.
     * @return le nombre de séries traitées.
     */
    suspend fun enrichPendingSeries(maxBatches: Int = 3): Int

    /** Nombre de séries par categoryId, basé sur le cache local (sélecteur de catégorie). */
    suspend fun getCategoryCounts(): Map<String, Int>

    /**
     * Séries « associées » à [currentSeriesId] : partageant au moins un genre
     * (parsé depuis [genre]), triées par nombre de genres communs décroissant
     * puis par score (note + date d'ajout). Vide si aucun genre exploitable ou
     * aucun candidat en cache. Limité à [limit] résultats.
     */
    suspend fun getRelatedSeries(currentSeriesId: Int, genre: String?, limit: Int = 10): List<SeriesStream>
}
