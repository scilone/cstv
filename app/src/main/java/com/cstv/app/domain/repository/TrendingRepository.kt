package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle

interface TrendingRepository {
    suspend fun getTrending(): List<TrendingTitle>
    suspend fun getCachedMatchedTrends(profileId: Int): List<TrendingCatalogItem>?
    suspend fun saveMatchedTrends(profileId: Int, items: List<TrendingCatalogItem>)
}
