package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val profileManager: ProfileManager,
    private val sync: CloudSyncManager? = null
) : FavoritesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<FavoriteItem>> {
        return profileManager.activeProfileId.flatMapLatest { profileId ->
            favoritesDao.observeFavorites(profileId)
        }.map { list -> list.map { FavoriteItem(it.id, it.type, it.name, it.cover, it.categoryId) } }
    }

    override suspend fun isFavorite(id: Int, type: String): Boolean {
        return favoritesDao.isFavorite(id, type, profileManager.currentProfileId())
    }

    override suspend fun addFavorite(favorite: FavoriteItem) {
        val entity = FavoriteEntity(
            id = favorite.id,
            type = favorite.type,
            name = favorite.name,
            cover = favorite.cover,
            categoryId = favorite.categoryId,
            addedAt = System.currentTimeMillis(),
            profileId = profileManager.currentProfileId()
        )
        favoritesDao.addFavorite(entity)
        sync?.markDirty(entity.profileId, SyncNamespace.FAVORITES)
    }

    override suspend fun removeFavorite(id: Int, type: String) {
        val profileId = profileManager.currentProfileId()
        favoritesDao.removeFavorite(id, type, profileId)
        sync?.markDirty(profileId, SyncNamespace.FAVORITES)
    }

    override suspend fun searchUnified(query: String): SearchResult {
        val searchQuery = LocalSearchQuery.parse(query)
        if (searchQuery.isEmpty) return SearchResult()

        val liveEntities = favoritesDao.searchLiveStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }
        val vodEntities = favoritesDao.searchVodStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }
        val seriesEntities = favoritesDao.searchSeriesStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }

        return SearchResult(
            liveResults = liveEntities.map {
                LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
            },
            vodResults = vodEntities.map {
                VodStream(
                    it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId,
                    it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText
                )
            },
            seriesResults = seriesEntities.map {
                SeriesStream(
                    it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId,
                    it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText
                )
            }
        )
    }

}
