package com.cstv.app.domain.repository

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import kotlinx.coroutines.flow.Flow

interface VodRepository {

    // --- Lecture : strictement locale, jamais de réseau, jamais d'exception réseau ---
    fun observeVodCategories(): Flow<List<VodCategory>>
    fun observeVodStreams(categoryId: String): Flow<List<VodStream>>
    fun getVodStreamsPaged(categoryId: String): Flow<androidx.paging.PagingData<VodStream>>

    /** Lecture ponctuelle du cache local (aucun appel réseau). */
    suspend fun getCachedVodCategories(): List<VodCategory>
    suspend fun getCachedVodStreams(categoryId: String): List<VodStream>

    /**
     * Films dont l'année de sortie est l'une de [years], plus ceux dont l'année
     * n'est pas encore enrichie (l'appariement TMDB sait la lire dans le titre).
     * Évite de charger et normaliser tout le catalogue pour une poignée
     * d'années. [years] vide renvoie tout le catalogue.
     */
    suspend fun getCachedVodStreamsByYears(years: Set<Int>): List<VodStream>

    // --- Écriture : réseau → Room, jamais consommée directement par l'UI ---
    suspend fun syncVodCategories(): List<VodCategory>
    suspend fun syncVodStreams(categoryId: String = "all"): List<VodStream>
    suspend fun getVodDetails(streamId: Int): VodDetails
    /** T20: title/cover/etc. are no longer stored here — they are resolved from the catalogue at
     *  display time (`VodDao.getAllPlaybackPositions` projection). [kind] is
     *  [com.cstv.app.domain.model.MediaKind.storageValue] ("movie" or "episode") — this single
     *  entry point is shared by `SavePlaybackPositionUseCase` for both VOD and series playback. */
    suspend fun savePlaybackPosition(kind: String, providerId: Int, positionMs: Long, durationMs: Long)
    suspend fun getPlaybackPosition(streamId: Int): Pair<Long, Long>?
    suspend fun clearPlaybackPosition(streamId: Int)
    suspend fun getAllPlaybackPositions(): List<PlaybackPosition>
    // Phase 41 : version réactive, utilisée par la Home pour "Continuer à
    // regarder" sans re-fetch manuel à chaque navigation.
    fun observeAllPlaybackPositions(): Flow<List<PlaybackPosition>>

    /** Nombre de films par categoryId, basé sur le cache local (sélecteur de catégorie). */
    suspend fun getCategoryCounts(): Map<String, Int>

    /** True when the local VOD catalog has at least one stream. */
    suspend fun hasCachedVodStreams(): Boolean

    /**
     * Bornes (année min, année max) des films dont l'année de sortie est
     * connue en cache, ou `null` si aucun film enrichi n'en a une (catalogue
     * pas encore synchronisé/enrichi). Alimente le filtre "année de sortie"
     * de la Recherche avancée.
     */
    suspend fun getReleaseYearBounds(): Pair<Int, Int>?

    /**
     * Enrichit (acteurs/réalisateur/genre) les films qui en manquent encore,
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
     * @return le nombre de films traités.
     */
    suspend fun enrichPendingMovies(maxBatches: Int = 3): Int

    /**
     * Films « associés » à [currentStreamId] : partageant au moins un genre
     * (parsé depuis [genre]), triés par nombre de genres communs décroissant
     * puis par score (note + date d'ajout). Vide si aucun genre exploitable ou
     * aucun candidat en cache. Limité à [limit] résultats.
     *
     * [excludedCategoryIds] (catégories masquées du profil actif) est appliqué
     * **avant** le classement, pas en post-filtrage : sinon un sur-fetch fixe
     * suivi d'un filtre a posteriori peut retourner moins de [limit] résultats
     * alors que des candidats visibles existent au-delà du sur-fetch.
     */
    suspend fun getRelatedMovies(
        currentStreamId: Int,
        genre: String?,
        limit: Int = 10,
        excludedCategoryIds: Set<String> = emptySet()
    ): List<VodStream>

    /** Récupère un film du cache local par son identifiant unique, ou null s'il n'existe plus. */
    suspend fun getStreamById(streamId: Int): VodStream?
}
