package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface TrendingRepository {
    suspend fun getTrending(): List<TrendingTitle>
    /**
     * @param ignoreSessionRefresh `true` pour lire le cache même au premier
     * accès du lancement (repli quand TMDB est injoignable).
     */
    suspend fun getCachedMatchedTrendsGlobal(
        lastCatalogSyncTime: Long = 0L,
        ignoreSessionRefresh: Boolean = false
    ): List<TrendingCatalogItem>?
    suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>)
}
