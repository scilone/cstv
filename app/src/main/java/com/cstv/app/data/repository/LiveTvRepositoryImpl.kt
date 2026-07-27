package com.cstv.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.google.gson.JsonElement
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.entity.LiveCategoryEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.EpgCacheEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.sync.CacheTtl
import com.cstv.app.domain.model.InvalidCredentialsException
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveEpgNowNext
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val liveTvDao: LiveTvDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.cstv.app.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate,
    private val networkMonitor: NetworkMonitor
) : LiveTvRepository {

    private fun LiveCategoryEntity.toDomain() = LiveCategory(categoryId, categoryName, parentId)

    private fun LiveStreamEntity.toDomain() =
        LiveStream(streamId, name, streamIcon, epgChannelId, num, categoryId)

    // --- Lecture locale ---

    override fun observeLiveCategories(): Flow<List<LiveCategory>> =
        liveTvDao.observeAllCategories()
            .distinctUntilChanged()
            .map { categories -> categories.map { it.toDomain() } }

    override fun observeLiveStreams(categoryId: String): Flow<List<LiveStream>> =
        (if (categoryId == ALL_CATEGORIES) liveTvDao.observeAllStreams() else liveTvDao.observeStreamsByCategory(categoryId))
            .distinctUntilChanged()
            .map { streams -> streams.map { it.toDomain() } }

    override suspend fun getCachedLiveCategories(): List<LiveCategory> =
        liveTvDao.getAllCategories().map { it.toDomain() }

    override suspend fun getCachedLiveStreams(categoryId: String): List<LiveStream> =
        (if (categoryId == ALL_CATEGORIES) liveTvDao.getAllStreams() else liveTvDao.getStreamsByCategory(categoryId))
            .map { it.toDomain() }

    // --- Écriture (synchronisation) ---

    override suspend fun syncLiveCategories(): List<LiveCategory> {
        val currentTime = System.currentTimeMillis()
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté ou session expirée.")

        val remoteCategories = requestGate.acquire { apiService.getLiveCategories(creds.username, creds.password) }

        // Mapping défensif : un panel peut renvoyer un id ou un nom absent.
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

        liveTvDao.replaceAllCategories(entities)
        return entities.map { it.toDomain() }
    }

    override suspend fun syncLiveStreams(categoryId: String): List<LiveStream> {
        val currentTime = System.currentTimeMillis()
        val apiCategoryId = if (categoryId == ALL_CATEGORIES) null else categoryId

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté ou session expirée.")

        val remoteStreams = requestGate.acquire { apiService.getLiveStreams(creds.username, creds.password, apiCategoryId) }

        val entities = remoteStreams.mapNotNull { dto ->
            val id = dto.streamId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != ALL_CATEGORIES }
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

        // Remplacement atomique : effacement et repeuplement du catalogue dans
        // la même transaction. Une annulation entre les deux laissait auparavant
        // le catalogue vide.
        if (categoryId == ALL_CATEGORIES) {
            liveTvDao.replaceAllStreams(entities)
        } else {
            liveTvDao.replaceStreamsByCategory(categoryId, entities)
        }


        return entities.map { it.toDomain() }
    }

    override fun getLiveStreamsPaged(categoryId: String): Flow<PagingData<LiveStream>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 100
            ),
            pagingSourceFactory = {
                if (categoryId == ALL_CATEGORIES) {
                    liveTvDao.getAllStreamsPaged()
                } else {
                    liveTvDao.getStreamsByCategoryPaged(categoryId)
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getCategoryCounts(): Map<String, Int> {
        return liveTvDao.getCategoryCounts().associate { it.categoryId to it.count }
    }

    override suspend fun saveRecentlyWatched(stream: LiveStream) {
        val entity = com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity(
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

    // --- EPG ---

    override suspend fun getEpgCachedAt(streamId: Int): Long? = liveTvDao.getEpgCachedAt(streamId)

    override suspend fun purgeExpiredEpg() {
        liveTvDao.purgeExpiredEpg((System.currentTimeMillis() - CacheTtl.EPG_RETENTION_MILLIS) / 1000L)
    }

    override suspend fun getLiveEpg(streamId: Int, forceRefresh: Boolean): LiveEpgProgram? {
        val currentTime = System.currentTimeMillis()
        val nowSec = currentTime / 1000L

        val cachedNow = liveTvDao.getEpgNow(streamId, nowSec)
        if (!forceRefresh && cachedNow != null && isEpgUsable(cachedNow.cachedAt, currentTime)) {
            return cachedNow.toDomain()
        }

        val creds = credentialsManager.getCredentials() ?: return cachedNow?.toDomain()

        return try {
            val programs = fetchEpgWindow(creds.username, creds.password, streamId, currentTime)
            if (programs.isEmpty()) {
                cachedNow?.toDomain()
            } else {
                programs.firstOrNull { nowSec in it.startTimestamp..it.endTimestamp }?.toDomain()
                    ?: programs.first().toDomain()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Repli explicite : hors ligne ou panel en erreur, l'EPG local reste
            // consultable quel que soit son âge.
            cachedNow?.toDomain()
        }
    }

    override suspend fun getLiveEpgNowNext(streamId: Int, forceRefresh: Boolean): LiveEpgNowNext {
        val currentTime = System.currentTimeMillis()
        val nowSec = currentTime / 1000L

        val cachedWindow = liveTvDao.getEpgWindow(streamId)
        val cachedResult = cachedWindow.toNowNext(nowSec)
        val cachedAt = cachedWindow.maxOfOrNull { it.cachedAt } ?: 0L
        if (!forceRefresh && cachedResult.current != null && isEpgUsable(cachedAt, currentTime)) {
            return cachedResult
        }

        val creds = credentialsManager.getCredentials() ?: return cachedResult

        return try {
            val programs = fetchEpgWindow(creds.username, creds.password, streamId, currentTime)
            if (programs.isEmpty()) cachedResult else programs.toNowNext(nowSec)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cachedResult
        }
    }

    /**
     * Un EPG en cache est réutilisable s'il est frais, ou dès lors que
     * l'appareil est hors ligne : mieux vaut un programme daté qu'aucun.
     */
    private fun isEpgUsable(cachedAt: Long, now: Long): Boolean =
        !networkMonitor.isCurrentlyOnline() || !CacheTtl.isExpired(cachedAt, CacheTtl.EPG_MILLIS, now)

    /**
     * Récupère et persiste la fenêtre complète de `get_short_epg`. L'ancien
     * cache ne retenait qu'un programme par chaîne, ce qui interdisait tout
     * « en cours + suivant » hors ligne.
     */
    private suspend fun fetchEpgWindow(
        username: String,
        password: String,
        streamId: Int,
        currentTime: Long
    ): List<EpgCacheEntity> {
        val response = requestGate.acquire { apiService.getShortEpg(username, password, streamId) }
        val listings = response.epgListings ?: return emptyList()

        val entities = listings.mapNotNull { dto ->
            val start = parseJsonTimestamp(dto.startTimestamp)
            val end = parseJsonTimestamp(dto.endTimestamp)
            // Sans borne de début exploitable, la ligne ne peut ni être clé
            // primaire ni servir à situer un programme dans le temps.
            if (start <= 0L) return@mapNotNull null
            EpgCacheEntity(
                streamId = streamId,
                startTimestamp = start,
                endTimestamp = end,
                title = decodeBase64OrReturnRaw(dto.title).ifBlank { "Aucun titre" },
                description = decodeBase64OrReturnRaw(dto.description),
                cachedAt = currentTime
            )
        }.sortedBy { it.startTimestamp }

        liveTvDao.replaceEpgForStream(streamId, entities)
        return entities
    }

    private fun EpgCacheEntity.toDomain() = LiveEpgProgram(
        title = title,
        description = description,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp
    )

    private fun List<EpgCacheEntity>.toNowNext(nowSec: Long): LiveEpgNowNext {
        if (isEmpty()) return LiveEpgNowNext(null, null)
        val sorted = sortedBy { it.startTimestamp }
        val current = sorted.find { nowSec in it.startTimestamp..it.endTimestamp }
        val next = if (current != null) {
            sorted.firstOrNull { it.startTimestamp > current.startTimestamp }
        } else {
            sorted.firstOrNull { it.startTimestamp >= nowSec }
        }
        return LiveEpgNowNext(current = current?.toDomain(), next = next?.toDomain())
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

    companion object {
        const val ALL_CATEGORIES = "all"
    }
}
