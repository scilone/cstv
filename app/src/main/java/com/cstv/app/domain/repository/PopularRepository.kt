package com.cstv.app.domain.repository

import com.cstv.app.domain.model.PopularCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface PopularRepository {
    suspend fun getPopularMovies(): List<TrendingTitle>
    suspend fun getPopularSeries(): List<TrendingTitle>
    /**
     * @param ignoreSessionRefresh `true` pour lire le cache même au premier
     * accès du lancement (repli quand TMDB est injoignable).
     */
    suspend fun getCachedMatchedMovies(
        lastVodCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean = false
    ): List<PopularCatalogItem>?

    suspend fun getCachedMatchedSeries(
        lastSeriesCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean = false
    ): List<PopularCatalogItem>?

    /**
     * T8 : lecture du cache pour affichage immédiat, quel que soit son âge —
     * seule l'absence de cache ou son incohérence avec le catalogue actuel
     * (`lastVodCatalogSyncTime`) le rend indisponible.
     */
    suspend fun getCachedMatchedMoviesIgnoringAge(lastVodCatalogSyncTime: Long): List<PopularCatalogItem>?
    suspend fun getCachedMatchedSeriesIgnoringAge(lastSeriesCatalogSyncTime: Long): List<PopularCatalogItem>?

    /**
     * T8-R1 : état de fraîcheur nominal (> 24h ou catalogue changé depuis) du
     * cache, indépendamment de son contenu — sert uniquement à décider si une
     * actualisation silencieuse est légitime, jamais à décider s'il est
     * affichable (voir [getCachedMatchedMoviesIgnoringAge]).
     */
    suspend fun isMoviesCacheExpired(lastVodCatalogSyncTime: Long): Boolean
    suspend fun isSeriesCacheExpired(lastSeriesCatalogSyncTime: Long): Boolean

    suspend fun saveMatchedMovies(items: List<PopularCatalogItem>)
    suspend fun saveMatchedSeries(items: List<PopularCatalogItem>)
}
