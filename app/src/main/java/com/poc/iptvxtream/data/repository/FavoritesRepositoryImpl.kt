package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {

    override suspend fun getFavorites(): List<FavoriteItem> {
        return favoritesDao.getAllFavorites().map { 
            FavoriteItem(it.id, it.type, it.name, it.cover, it.categoryId)
        }
    }

    override suspend fun isFavorite(id: Int, type: String): Boolean {
        return favoritesDao.isFavorite(id, type)
    }

    override suspend fun addFavorite(favorite: FavoriteItem) {
        val entity = FavoriteEntity(
            id = favorite.id,
            type = favorite.type,
            name = favorite.name,
            cover = favorite.cover,
            categoryId = favorite.categoryId,
            addedAt = System.currentTimeMillis()
        )
        favoritesDao.addFavorite(entity)
    }

    override suspend fun removeFavorite(id: Int, type: String) {
        favoritesDao.removeFavorite(id, type)
    }

    override suspend fun searchUnified(query: String): SearchResult {
        if (query.trim().isBlank()) return SearchResult()

        val sqlQuery = "%${query.trim()}%"
        
        val liveEntities = favoritesDao.searchLiveStreams(sqlQuery)
        val vodEntities = favoritesDao.searchVodStreams(sqlQuery)
        val seriesEntities = favoritesDao.searchSeriesStreams(sqlQuery)

        return SearchResult(
            liveResults = liveEntities.map { 
                LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
            },
            vodResults = vodEntities.map { 
                VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId)
            },
            seriesResults = seriesEntities.map { 
                SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
            }
        )
    }
}
