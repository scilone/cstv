package com.cstv.app.domain.repository

import com.cstv.app.domain.model.TrendingTitle

interface TrendingRepository {
    suspend fun getTrending(): List<TrendingTitle>
}
