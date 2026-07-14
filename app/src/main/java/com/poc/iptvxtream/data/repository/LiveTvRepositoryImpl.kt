package com.poc.iptvxtream.data.repository

import com.google.gson.JsonElement
import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.entity.LiveCategoryEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.EpgCacheEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.api.XtreamRequestGate
import com.poc.iptvxtream.domain.model.InvalidCredentialsException
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val liveTvDao: LiveTvDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.poc.iptvxtream.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate,
    private val settingsManager: SettingsManager
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

        val remoteCategories = requestGate.acquire { apiService.getLiveCategories(creds.username, creds.password) }
        
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
        val apiCategoryId = if (categoryId == "all") null else categoryId

        if (!forceRefresh) {
            if (categoryId == "all") {
                val lastAllStreamsSyncAt = settingsManager.getLiveAllStreamsSyncedAt()
                if (lastAllStreamsSyncAt != 0L && currentTime - lastAllStreamsSyncAt < CACHE_EXPIRY_MILLIS) {
                    val localStreams = liveTvDao.getAllStreams()
                    return localStreams.map {
                        LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
                    }
                }
            } else {
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
        }

        // Fetch from Network
        val creds = credentialsManager.getCredentials() 
            ?: throw InvalidCredentialsException("Utilisateur non connecté ou session expirée.")

        val remoteStreams = requestGate.acquire { apiService.getLiveStreams(creds.username, creds.password, apiCategoryId) }

        // Defensive Mapping & Storage
        val entities = remoteStreams.mapNotNull { dto ->
            val id = dto.streamId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != "all" }
            if (id != null && name != null && itemCategoryId != null) {
                LiveStreamEntity(
                    streamId = id,
                    name = name,
                    streamIcon = dto.streamIcon,
                    epgChannelId = dto.epgChannelId,
                    num = dto.num ?: 0,
                    categoryId = itemCategoryId,
                    cachedAt = currentTime
                )
            } else null
        }

        // Insert into cache
        if (categoryId == "all") {
            liveTvDao.clearAllStreams()
            liveTvDao.clearAllFts()
        } else {
            liveTvDao.clearStreamsByCategory(categoryId)
            liveTvDao.clearFtsByCategory(categoryId)
        }

        if (entities.isNotEmpty()) {
            liveTvDao.insertStreamsWithFts(entities)
        }

        if (categoryId == "all") {
            settingsManager.setLiveAllStreamsSyncedAt(currentTime)
        }

        return entities.map { 
            LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
        }
    }

    override suspend fun saveRecentlyWatched(stream: LiveStream) {
        val entity = com.poc.iptvxtream.data.local.entity.RecentlyWatchedLiveEntity(
            streamId = stream.streamId,
            profileId = profileManager.currentProfileId(),
            name = stream.name,
            streamIcon = stream.streamIcon,
            categoryId = stream.categoryId,
            num = stream.num,
            watchedAt = System.currentTimeMillis()
        )
        liveTvDao.insertRecentlyWatched(entity)
    }

    override suspend fun getRecentlyWatched(): List<LiveStream> {
        val entities = liveTvDao.getRecentlyWatched(profileManager.currentProfileId(), limit = 10)
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

    override suspend fun getLiveEpg(streamId: Int, forceRefresh: Boolean): LiveEpgProgram? {
        val currentTime = System.currentTimeMillis()
        val cacheExpiry = 5 * 60 * 1000L // 5 minutes

        if (!forceRefresh) {
            val cached = liveTvDao.getEpgCache(streamId)
            if (cached != null && currentTime - cached.cachedAt < cacheExpiry) {
                val nowSec = currentTime / 1000L
                if (nowSec < cached.endTimestamp) {
                    return LiveEpgProgram(
                        title = cached.title,
                        description = cached.description,
                        startTimestamp = cached.startTimestamp,
                        endTimestamp = cached.endTimestamp
                    )
                }
            }
        }

        val creds = credentialsManager.getCredentials() ?: return null

        return try {
            val response = requestGate.acquire { apiService.getShortEpg(creds.username, creds.password, streamId) }
            val listings = response.epgListings
            if (!listings.isNullOrEmpty()) {
                val nowSec = System.currentTimeMillis() / 1000L
                
                val activeListing = listings.find { dto ->
                    val start = parseJsonTimestamp(dto.startTimestamp)
                    val end = parseJsonTimestamp(dto.endTimestamp)
                    nowSec in start..end
                } ?: listings.firstOrNull()

                if (activeListing != null) {
                    val decodedTitle = decodeBase64OrReturnRaw(activeListing.title)
                    val decodedDesc = decodeBase64OrReturnRaw(activeListing.description)
                    val startSec = parseJsonTimestamp(activeListing.startTimestamp)
                    val endSec = parseJsonTimestamp(activeListing.endTimestamp)

                    val program = LiveEpgProgram(
                        title = if (decodedTitle.isBlank()) "Aucun titre" else decodedTitle,
                        description = decodedDesc,
                        startTimestamp = startSec,
                        endTimestamp = endSec
                    )

                    liveTvDao.insertEpgCache(
                        EpgCacheEntity(
                            streamId = streamId,
                            title = program.title,
                            description = program.description,
                            startTimestamp = program.startTimestamp,
                            endTimestamp = program.endTimestamp,
                            cachedAt = currentTime
                        )
                    )

                    program
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonTimestamp(element: JsonElement?): Long {
        if (element == null || element.isJsonNull) return 0L
        return when {
            element.isJsonPrimitive -> {
                val p = element.asJsonPrimitive
                if (p.isNumber) {
                    p.asLong
                } else {
                    p.asString.toLongOrNull() ?: 0L
                }
            }
            else -> 0L
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun decodeBase64OrReturnRaw(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()
        return try {
            // java.util path needs API 26+ and is also what runs in JVM unit tests;
            // the MIME decoder tolerates line breaks like android.util's DEFAULT does.
            // Fallback covers older devices (NoClassDefFoundError) and strict-decode failures.
            val bytes = try {
                java.util.Base64.getMimeDecoder().decode(trimmed)
            } catch (e: Throwable) {
                android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            }
            val decodedStr = String(bytes, Charsets.UTF_8)
            if (decodedStr.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
                trimmed
            } else {
                decodedStr
            }
        } catch (e: Exception) {
            trimmed
        }
    }
}
