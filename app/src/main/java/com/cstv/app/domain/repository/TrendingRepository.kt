package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface TrendingRepository {
    suspend fun getTrending(): List<TrendingTitle>
    /**
     * @param ignoreSessionRefresh `true` pour lire le cache même au premier
     * accès du lancement (repli quand TMDB est injoignable).
     * @param ignoreCatalogSync `true` pour servir le cache même si le catalogue
     * a été resynchronisé depuis sa génération. Réservé à l'affichage immédiat :
     * un appariement périmé se corrige au rafraîchissement d'arrière-plan, alors
     * qu'un écran vide, lui, ne se rattrape pas.
     */
    suspend fun getCachedMatchedTrendsGlobal(
        lastCatalogSyncTime: Long = 0L,
        ignoreSessionRefresh: Boolean = false,
        ignoreExpiration: Boolean = false,
        ignoreCatalogSync: Boolean = false
    ): List<TrendingCatalogItem>?
    suspend fun isCacheExpired(lastCatalogSyncTime: Long = 0L): Boolean
    suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>)
}
