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
    suspend fun saveMatchedMovies(items: List<PopularCatalogItem>)
    suspend fun saveMatchedSeries(items: List<PopularCatalogItem>)
}
