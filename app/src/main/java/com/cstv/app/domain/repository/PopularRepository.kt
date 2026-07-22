package com.cstv.app.domain.repository

import com.cstv.app.domain.model.PopularCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface PopularRepository {
    suspend fun getPopularMovies(): List<TrendingTitle>
    suspend fun getPopularSeries(): List<TrendingTitle>
    suspend fun getCachedMatchedMovies(lastVodCatalogSyncTime: Long): List<PopularCatalogItem>?
    suspend fun getCachedMatchedSeries(lastSeriesCatalogSyncTime: Long): List<PopularCatalogItem>?
    suspend fun saveMatchedMovies(items: List<PopularCatalogItem>)
    suspend fun saveMatchedSeries(items: List<PopularCatalogItem>)
}
