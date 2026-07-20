package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface TrendingRepository {
    suspend fun getTrending(): List<TrendingTitle>
    suspend fun getCachedMatchedTrendsGlobal(lastCatalogSyncTime: Long = 0L): List<TrendingCatalogItem>?
    suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>)
}
