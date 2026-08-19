package com.cstv.app.domain.repository

import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesStream
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {

    // --- Lecture : strictement locale, jamais de réseau, jamais d'exception réseau ---
    fun observeSeriesCategories(): Flow<List<SeriesCategory>>
    fun observeSeriesStreams(categoryId: String): Flow<List<SeriesStream>>
    fun getSeriesStreamsPaged(categoryId: String): Flow<androidx.paging.PagingData<SeriesStream>>

    /** Lecture ponctuelle du cache local (aucun appel réseau). */
    suspend fun getCachedSeriesCategories(): List<SeriesCategory>
    suspend fun getCachedSeriesStreams(categoryId: String): List<SeriesStream>

    /** Voir [VodRepository.getCachedVodStreamsByYears]. */
    suspend fun getCachedSeriesStreamsByYears(years: Set<Int>): List<SeriesStream>

    // --- Écriture : réseau → Room, jamais consommée directement par l'UI ---
    suspend fun syncSeriesCategories(): List<SeriesCategory>
    suspend fun syncSeriesStreams(categoryId: String = "all"): List<SeriesStream>

    /**
     * Fiche série. Cache d'abord, réseau ensuite si en ligne, persistance des
     * saisons et épisodes, repli sur une fiche dégradée
     * ([SeriesDetails.isMetadataIncomplete]) si le panel échoue.
     */
    suspend fun getSeriesDetails(seriesId: Int): SeriesDetails
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

    /** True when the local Series catalog has at least one stream. */
    suspend fun hasCachedSeriesStreams(): Boolean

    /**
     * Bornes (année min, année max) des séries dont l'année de sortie est
     * connue en cache, ou `null` si aucune série enrichie n'en a une.
     * Alimente le filtre "année de sortie" de la Recherche avancée.
     */
    suspend fun getReleaseYearBounds(): Pair<Int, Int>?

    /**
     * Séries « associées » à [currentSeriesId] : partageant au moins un genre
     * (parsé depuis [genre]), triées par nombre de genres communs décroissant
     * puis par score (note + date d'ajout). Vide si aucun genre exploitable ou
     * aucun candidat en cache. Limité à [limit] résultats.
     *
     * [excludedCategoryIds] (catégories masquées du profil actif) est appliqué
     * **avant** le classement, pas en post-filtrage : sinon un sur-fetch fixe
     * suivi d'un filtre a posteriori peut retourner moins de [limit] résultats
     * alors que des candidats visibles existent au-delà du sur-fetch.
     */
    suspend fun getRelatedSeries(
        currentSeriesId: Int,
        genre: String?,
        limit: Int = 10,
        excludedCategoryIds: Set<String> = emptySet()
    ): List<SeriesStream>

    /** Récupère une série du cache local par son identifiant unique, ou null s'elle n'existe plus. */
    suspend fun getStreamById(seriesId: Int): SeriesStream?

    /**
     * F44 : identifiant de la série mère d'un épisode, ou `null` si l'épisode
     * est absent du cache local. Utilisé par la garde parentale — la
     * classification d'un épisode est celle de sa série entière (décision F44
     * étape 1, pas de granularité par saison/épisode).
     */
    suspend fun getSeriesIdForEpisode(episodeId: Int): Int? = null

    /** F39 §8.2 : voir [com.cstv.app.domain.repository.VodRepository.getVersionsByLinkKey]. */
    suspend fun getVersionsByLinkKey(linkKey: String, releaseYear: Int?): List<SeriesStream> = emptyList()

    /**
     * F39 §8.2 point 2 : épisode d'une série pour le couple saison/épisode
     * donné, ou `null` si absent (série incomplète en cache) — jamais
     * d'appel réseau, consommé par [com.cstv.app.domain.model.SeriesVersionResolver].
     */
    suspend fun getEpisodeBySeasonEpisode(seriesId: Int, seasonNum: Int, episodeNum: Int): SeriesEpisode? = null

    // --- Recommendations (T25) ---
    suspend fun getRecommendableSeriesItems(): List<com.cstv.app.domain.model.RecommendationEngine.RecommendableItem>
    suspend fun getStreamsByIds(seriesIds: List<Int>): List<SeriesStream>
}
