package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.VodCategoryEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.api.RequestPriority
import com.poc.iptvxtream.data.remote.api.XtreamRequestGate
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.InvalidCredentialsException
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.VodRepository
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val vodDao: VodDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.poc.iptvxtream.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate
) : VodRepository {

    private var enrichmentDispatcher: CoroutineDispatcher = Dispatchers.IO
    // Toute coroutine lancée dans ce scope hérite de la priorité "arrière-plan"
    // (voir RequestPriority) : le trickle d'enrichissement interactif cède
    // toujours le pas à la navigation utilisateur.
    private val repositoryScope by lazy {
        CoroutineScope(SupervisorJob() + enrichmentDispatcher + RequestPriority.background)
    }
    private var enrichmentJob: Job? = null

    // Constructor for testing
    constructor(
        apiService: XtreamApiService,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: com.poc.iptvxtream.data.local.storage.ProfileManager,
        requestGate: XtreamRequestGate,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, vodDao, credentialsManager, profileManager, requestGate) {
        this.enrichmentDispatcher = dispatcher
    }

    private fun startBackgroundEnrichment() {
        if (enrichmentJob?.isActive == true) return
        enrichmentJob = repositoryScope.launch {
            val creds = credentialsManager.getCredentials() ?: return@launch
            enrichBatch(creds, ENRICHMENT_BATCH_SIZE)
        }
    }

    /**
     * Enrichit un lot d'au plus [limit] films dont actors/director/genre
     * manquent encore. Retourne le nombre de films traités (succès ou échec
     * individuel confondus) : un lot plein signale qu'il reste probablement
     * du travail, un lot partiel/vide signale un catalogue à jour.
     */
    private suspend fun enrichBatch(creds: Credentials, limit: Int): Int {
        return try {
            val needingEnrichment = vodDao.getStreamsNeedingEnrichment(limit)
            for (stream in needingEnrichment) {
                try {
                    val response = requestGate.acquire { apiService.getVodInfo(creds.username, creds.password, stream.streamId) }
                    val infoDto = response.info
                    val director = infoDto?.director ?: "Inconnu"
                    val actors = extractActors(infoDto?.actors, infoDto?.cast)
                    val genre = infoDto?.genre ?: "Inconnu"

                    val currentStream = vodDao.getStreamById(stream.streamId)
                    if (currentStream != null) {
                        vodDao.insertStreams(listOf(
                            currentStream.copy(
                                actors = actors,
                                director = director,
                                genre = genre
                            )
                        ))
                    }
                    delay(200)
                } catch (e: Exception) {
                    // ignore individual stream fetch failure and continue
                }
            }
            needingEnrichment.size
        } catch (e: Exception) {
            // handle overall errors gracefully
            0
        }
    }

    override suspend fun enrichPendingMovies(maxBatches: Int): Int {
        val creds = credentialsManager.getCredentials() ?: return 0
        var total = 0
        repeat(maxBatches) {
            val processed = enrichBatch(creds, ENRICHMENT_BATCH_SIZE)
            total += processed
            if (processed < ENRICHMENT_BATCH_SIZE) return total
        }
        return total
    }

    companion object {
        private const val CACHE_EXPIRY_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        private const val ENRICHMENT_BATCH_SIZE = 50
    }

    // Tracks whether a full ("all") bulk fetch has been done, so a partial
    // per-category cache is never mistaken for the complete "Tout" cache.
    private var lastAllStreamsSyncAt: Long = 0L

    private fun extractActors(actorsElement: JsonElement?, castElement: JsonElement?): String {
        val actorList = mutableListOf<String>()

        fun parseElement(element: JsonElement?) {
            if (element == null || element.isJsonNull) return
            
            if (element.isJsonPrimitive) {
                val str = element.asString
                if (!str.isNullOrBlank() && str != "Inconnu") {
                    str.split(",").forEach { actor ->
                        val cleanActor = actor.trim()
                        if (cleanActor.isNotBlank() && cleanActor != "Inconnu") {
                            actorList.add(cleanActor)
                        }
                    }
                }
            } else if (element.isJsonArray) {
                val array = element.asJsonArray
                for (i in 0 until array.size()) {
                    val item = array.get(i)
                    if (item.isJsonPrimitive) {
                        val str = item.asString
                        if (!str.isNullOrBlank() && str != "Inconnu") {
                            str.split(",").forEach { actor ->
                                val cleanActor = actor.trim()
                                if (cleanActor.isNotBlank() && cleanActor != "Inconnu") {
                                    actorList.add(cleanActor)
                                }
                            }
                        }
                    }
                }
            }
        }

        parseElement(actorsElement)
        parseElement(castElement)

        val deduplicated = actorList.distinct()
        return if (deduplicated.isEmpty()) "Inconnu" else deduplicated.joinToString(", ")
    }

    private fun formatDuration(durationElement: JsonElement?): String? {
        if (durationElement == null || durationElement.isJsonNull) return null
        
        if (durationElement.isJsonPrimitive) {
            val primitive = durationElement.asJsonPrimitive
            if (primitive.isString) {
                val str = primitive.asString.trim()
                if (str.contains(":")) {
                    val parts = str.split(":")
                    val totalMinutes = when (parts.size) {
                        3 -> { // "hh:mm:ss"
                            val h = parts[0].toIntOrNull() ?: 0
                            val m = parts[1].toIntOrNull() ?: 0
                            h * 60 + m
                        }
                        2 -> { // "mm:ss"
                            val m = parts[0].toIntOrNull() ?: 0
                            m
                        }
                        else -> 0
                    }
                    if (totalMinutes > 0) {
                        val hours = totalMinutes / 60
                        val minutes = totalMinutes % 60
                        return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
                    }
                }
            }

            // Otherwise parse as integer (seconds or minutes)
            val seconds = if (primitive.isNumber) {
                primitive.asInt
            } else {
                primitive.asString.toIntOrNull()
            } ?: return null

            if (seconds <= 0) return null

            val totalMinutes = if (seconds > 300) {
                seconds / 60
            } else {
                seconds
            }

            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            return if (hours > 0) {
                "${hours}h ${minutes}min"
            } else {
                "${minutes}min"
            }
        }
        return null
    }

    private fun formatRating(ratingStr: String?): String {
        if (ratingStr.isNullOrBlank()) return "0.0"
        val doubleVal = ratingStr.trim().toDoubleOrNull() ?: return "0.0"
        return String.format(java.util.Locale.US, "%.1f", doubleVal)
    }

    override suspend fun getVodCategories(forceRefresh: Boolean): List<VodCategory> {
        val currentTime = System.currentTimeMillis()

        if (!forceRefresh) {
            val localCategories = vodDao.getAllCategories()
            if (localCategories.isNotEmpty()) {
                val lastCachedAt = localCategories.first().cachedAt
                if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                    return localCategories.map { 
                        VodCategory(it.categoryId, it.categoryName, it.parentId)
                    }
                }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteCategories = requestGate.acquire { apiService.getVodCategories(creds.username, creds.password) }

        val entities = remoteCategories.mapIndexedNotNull { index, dto ->
            val id = dto.categoryId
            val name = dto.categoryName
            if (id != null && name != null) {
                VodCategoryEntity(
                    categoryId = id,
                    categoryName = name,
                    parentId = dto.parentId ?: 0,
                    cachedAt = currentTime,
                    orderIndex = index
                )
            } else null
        }

        if (entities.isNotEmpty()) {
            vodDao.clearCategories()
            vodDao.insertCategories(entities)
        }

        return entities.map { 
            VodCategory(it.categoryId, it.categoryName, it.parentId)
        }
    }

    override suspend fun getVodStreams(categoryId: String, forceRefresh: Boolean): List<VodStream> {
        val currentTime = System.currentTimeMillis()
        val apiCategoryId = if (categoryId == "all") null else categoryId

        if (!forceRefresh) {
            if (categoryId == "all") {
                if (lastAllStreamsSyncAt != 0L && currentTime - lastAllStreamsSyncAt < CACHE_EXPIRY_MILLIS) {
                    val localStreams = vodDao.getAllStreams()
                    startBackgroundEnrichment()
                    return localStreams.map {
                        VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId)
                    }
                }
            } else {
                val localStreams = vodDao.getStreamsByCategory(categoryId)
                if (localStreams.isNotEmpty()) {
                    val lastCachedAt = localStreams.first().cachedAt
                    if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                        startBackgroundEnrichment()
                        return localStreams.map {
                            VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId)
                        }
                    }
                }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteStreams = requestGate.acquire { apiService.getVodStreams(creds.username, creds.password, apiCategoryId) }

        // Preserve actors/director/genre enrichment (only ever fetched via getVodDetails,
        // never part of this bulk list response) so a routine cache refresh doesn't wipe it.
        val existingById = (if (categoryId == "all") vodDao.getAllStreams() else vodDao.getStreamsByCategory(categoryId))
            .associateBy { it.streamId }

        val entities = remoteStreams.mapNotNull { dto ->
            val id = dto.streamId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != "all" }
            if (id != null && name != null && itemCategoryId != null) {
                val existing = existingById[id]
                VodStreamEntity(
                    streamId = id,
                    name = name,
                    streamIcon = dto.streamIcon,
                    rating = dto.rating,
                    added = dto.added,
                    categoryId = itemCategoryId,
                    cachedAt = currentTime,
                    actors = existing?.actors,
                    director = existing?.director,
                    genre = existing?.genre
                )
            } else null
        }

        if (categoryId == "all") {
            vodDao.clearAllStreams()
        } else {
            vodDao.clearStreamsByCategory(categoryId)
        }

        if (entities.isNotEmpty()) {
            vodDao.insertStreams(entities)
        }

        if (categoryId == "all") {
            lastAllStreamsSyncAt = currentTime
        }

        startBackgroundEnrichment()

        return entities.map { 
            VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId)
        }
    }

    override suspend fun getVodDetails(streamId: Int): VodDetails {
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val response = requestGate.acquire { apiService.getVodInfo(creds.username, creds.password, streamId) }
        val infoDto = response.info
        val movieDataDto = response.movieData

        // Fallback or parse fields defensively
        val name = infoDto?.name ?: "Film sans titre"
        val director = infoDto?.director ?: "Inconnu"
        val actors = extractActors(infoDto?.actors, infoDto?.cast)
        val releaseDate = infoDto?.releaseDate ?: "Inconnu"
        val genre = infoDto?.genre ?: "Inconnu"
        val plot = infoDto?.plot ?: "Aucun résumé disponible."
        val rating = formatRating(infoDto?.rating ?: infoDto?.rating5)
        val cover = infoDto?.coverBig ?: infoDto?.movieImage
        val extension = movieDataDto?.containerExtension ?: "mp4"
        val duration = formatDuration(infoDto?.duration)

        // Fetch resume position from local DB if exists
        val savedPosition = vodDao.getPlaybackPosition(streamId, profileManager.currentProfileId())

        // Sensationally enrich cached stream entity with actors, director, and genre details
        val cachedStream = vodDao.getStreamById(streamId)
        if (cachedStream != null) {
            vodDao.insertStreams(listOf(
                cachedStream.copy(
                    actors = actors,
                    director = director,
                    genre = genre
                )
            ))
        }

        return VodDetails(
            streamId = streamId,
            name = name,
            director = director,
            actors = actors,
            releaseDate = releaseDate,
            genre = genre,
            plot = plot,
            rating = rating,
            coverBig = cover,
            containerExtension = extension,
            resumePositionMs = savedPosition?.positionMs ?: 0L,
            durationMs = savedPosition?.durationMs ?: 0L,
            duration = duration
        )
    }

    override suspend fun savePlaybackPosition(
        streamId: Int,
        positionMs: Long,
        durationMs: Long,
        title: String?,
        coverUrl: String?,
        type: String?,
        containerExtension: String?,
        seriesId: Int?,
        episodeNum: Int?,
        seasonNum: Int?,
        plot: String?,
        duration: String?,
        releaseDate: String?
    ) {
        val profileId = profileManager.currentProfileId()
        val existing = vodDao.getPlaybackPosition(streamId, profileId)

        val finalTitle = if (title.isNullOrBlank()) existing?.title else title
        val finalCoverUrl = if (coverUrl.isNullOrBlank()) existing?.coverUrl else coverUrl
        val finalType = if (type.isNullOrBlank()) existing?.type else type
        val finalContainerExtension = if (containerExtension.isNullOrBlank()) existing?.containerExtension else containerExtension
        val finalSeriesId = seriesId ?: existing?.seriesId
        val finalEpisodeNum = episodeNum ?: existing?.episodeNum
        val finalSeasonNum = seasonNum ?: existing?.seasonNum
        val finalPlot = if (plot.isNullOrBlank()) existing?.plot else plot
        val finalDuration = if (duration.isNullOrBlank()) existing?.duration else duration
        val finalReleaseDate = if (releaseDate.isNullOrBlank()) existing?.releaseDate else releaseDate

        val entity = PlaybackPositionEntity(
            streamId = streamId,
            profileId = profileId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastAccessedAt = System.currentTimeMillis(),
            title = finalTitle,
            coverUrl = finalCoverUrl,
            type = finalType,
            containerExtension = finalContainerExtension,
            seriesId = finalSeriesId,
            episodeNum = finalEpisodeNum,
            seasonNum = finalSeasonNum,
            plot = finalPlot,
            duration = finalDuration,
            releaseDate = finalReleaseDate
        )
        vodDao.savePlaybackPosition(entity)
    }

    override suspend fun getPlaybackPosition(streamId: Int): Pair<Long, Long>? {
        val entity = vodDao.getPlaybackPosition(streamId, profileManager.currentProfileId()) ?: return null
        return Pair(entity.positionMs, entity.durationMs)
    }

    override suspend fun clearPlaybackPosition(streamId: Int) {
        vodDao.deletePlaybackPosition(streamId, profileManager.currentProfileId())
    }

    override suspend fun getAllPlaybackPositions(): List<PlaybackPosition> {
        return vodDao.getAllPlaybackPositions(profileManager.currentProfileId()).map { entity ->
            PlaybackPosition(
                streamId = entity.streamId,
                positionMs = entity.positionMs,
                durationMs = entity.durationMs,
                lastAccessedAt = entity.lastAccessedAt,
                title = entity.title,
                coverUrl = entity.coverUrl,
                type = entity.type,
                containerExtension = entity.containerExtension,
                seriesId = entity.seriesId,
                episodeNum = entity.episodeNum,
                seasonNum = entity.seasonNum,
                plot = entity.plot,
                duration = entity.duration,
                releaseDate = entity.releaseDate
            )
        }
    }
}
