package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.entity.LiveCategoryEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.domain.model.InvalidCredentialsException
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val liveTvDao: LiveTvDao,
    private val credentialsManager: CredentialsManager
) : LiveTvRepository {

    companion object {
        // Cache expiry: 24 hours
        private const val CACHE_EXPIRY_MILLIS = 24 * 60 * 60 * 1000L
    }

    override suspend fun getLiveCategories(forceRefresh: Boolean): List<LiveCategory> {
        val currentTime = System.currentTimeMillis()
        
        if (!forceRefresh) {
            val localCategories = liveTvDao.getAllCategories()
            if (localCategories.isNotEmpty()) {
                val lastCachedAt = localCategories.first().cachedAt
                if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                    return localCategories.map { 
                        LiveCategory(it.categoryId, it.categoryName, it.parentId)
                    }
                }
            }
        }

        // Fetch from Network
        val creds = credentialsManager.getCredentials() 
            ?: throw InvalidCredentialsException("Utilisateur non connecté ou session expirée.")

        val remoteCategories = apiService.getLiveCategories(creds.username, creds.password)
        
        // Defensive Mapping & Storage
        val entities = remoteCategories.mapIndexedNotNull { index, dto ->
            val id = dto.categoryId
            val name = dto.categoryName
            if (id != null && name != null) {
                LiveCategoryEntity(
                    categoryId = id,
                    categoryName = name,
                    parentId = dto.parentId ?: 0,
                    cachedAt = currentTime,
                    orderIndex = index
                )
            } else null
        }

        if (entities.isNotEmpty()) {
            liveTvDao.clearCategories()
            liveTvDao.insertCategories(entities)
        }

        return entities.map { 
            LiveCategory(it.categoryId, it.categoryName, it.parentId)
        }
    }

    override suspend fun getLiveStreams(categoryId: String, forceRefresh: Boolean): List<LiveStream> {
        val currentTime = System.currentTimeMillis()

        if (!forceRefresh) {
            val localStreams = liveTvDao.getStreamsByCategory(categoryId)
            if (localStreams.isNotEmpty()) {
                val lastCachedAt = localStreams.first().cachedAt
                if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                    return localStreams.map { 
                        LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
                    }
                }
            }
        }

        // Fetch from Network
        val creds = credentialsManager.getCredentials() 
            ?: throw InvalidCredentialsException("Utilisateur non connecté ou session expirée.")

        val remoteStreams = apiService.getLiveStreams(creds.username, creds.password, categoryId)

        // Defensive Mapping & Storage
        val entities = remoteStreams.mapNotNull { dto ->
            val id = dto.streamId
            val name = dto.name
            if (id != null && name != null) {
                LiveStreamEntity(
                    streamId = id,
                    name = name,
                    streamIcon = dto.streamIcon,
                    epgChannelId = dto.epgChannelId,
                    num = dto.num ?: 0,
                    categoryId = categoryId,
                    cachedAt = currentTime
                )
            } else null
        }

        // Insert into cache
        liveTvDao.clearStreamsByCategory(categoryId)
        if (entities.isNotEmpty()) {
            liveTvDao.insertStreams(entities)
        }

        return entities.map { 
            LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
        }
    }

    override suspend fun saveRecentlyWatched(stream: LiveStream) {
        val entity = com.poc.iptvxtream.data.local.entity.RecentlyWatchedLiveEntity(
            streamId = stream.streamId,
            name = stream.name,
            streamIcon = stream.streamIcon,
            categoryId = stream.categoryId,
            num = stream.num,
            watchedAt = System.currentTimeMillis()
        )
        liveTvDao.insertRecentlyWatched(entity)
    }

    override suspend fun getRecentlyWatched(): List<LiveStream> {
        val entities = liveTvDao.getRecentlyWatched(limit = 10)
        return entities.map { 
            LiveStream(
                streamId = it.streamId,
                name = it.name,
                streamIcon = it.streamIcon,
                epgChannelId = null,
                num = it.num ?: 0,
                categoryId = it.categoryId ?: "0"
            )
        }
    }
}
