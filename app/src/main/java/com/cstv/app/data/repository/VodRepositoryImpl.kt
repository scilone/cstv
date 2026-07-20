package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.InvalidCredentialsException
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.ReleaseYearParser
import com.cstv.app.domain.repository.VodRepository
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val vodDao: VodDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.cstv.app.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate,
    private val settingsManager: SettingsManager
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
        profileManager: com.cstv.app.data.local.storage.ProfileManager,
        requestGate: XtreamRequestGate,
        settingsManager: SettingsManager,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, vodDao, credentialsManager, profileManager, requestGate, settingsManager) {
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
                    // Sentinelle 0 = "vérifié mais année inconnue" : évite de re-fetcher
                    // indéfiniment les films sans date de sortie (mappée en null en domain).
                    val releaseYear = ReleaseYearParser.parseYear(infoDto?.releaseDate) ?: 0

                    val currentStream = vodDao.getStreamById(stream.streamId)
                    if (currentStream != null) {
                        vodDao.insertStreamsWithFts(listOf(
                            currentStream.copy(
                                actors = actors,
                                director = director,
                                genre = genre,
                                releaseYear = releaseYear
                            )
                        ))
                    }
                    delay(200)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // ignore individual stream fetch failure and continue
                }
            }
            needingEnrichment.size
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
                return localCategories.map { 
                    VodCategory(it.categoryId, it.categoryName, it.parentId)
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
                val localStreams = vodDao.getAllStreams()
                if (localStreams.isNotEmpty()) {
                    startBackgroundEnrichment()
                    return localStreams.map {
                        VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 })
                    }
                }
            } else {
                val localStreams = vodDao.getStreamsByCategory(categoryId)
                if (localStreams.isNotEmpty()) {
                    startBackgroundEnrichment()
                    return localStreams.map {
                        VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 })
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

        val entities = remoteStreams.mapIndexedNotNull { index, dto ->
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
                    genre = existing?.genre,
                    orderIndex = index,
                    releaseYear = existing?.releaseYear
                )
            } else null
        }

        if (categoryId == "all") {
            vodDao.clearAllStreams()
            vodDao.clearAllFts()
        } else {
            vodDao.clearStreamsByCategory(categoryId)
            vodDao.clearFtsByCategory(categoryId)
        }

        if (entities.isNotEmpty()) {
            vodDao.insertStreamsWithFts(entities)
        }

        if (categoryId == "all") {
            settingsManager.setVodAllStreamsSyncedAt(currentTime)
        }

        startBackgroundEnrichment()

        return entities.map { 
            VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 })
        }
    }

    override suspend fun getCategoryCounts(): Map<String, Int> {
        return vodDao.getCategoryCounts().associate { it.categoryId to it.count }
    }

    override suspend fun getReleaseYearBounds(): Pair<Int, Int>? {
        val min = vodDao.getMinReleaseYear() ?: return null
        val max = vodDao.getMaxReleaseYear() ?: return null
        return min to max
    }

    override suspend fun getRelatedMovies(
        currentStreamId: Int,
        genre: String?,
        limit: Int,
        excludedCategoryIds: Set<String>
    ): List<VodStream> {
        val genres = com.cstv.app.domain.model.GenreParser.parseGenres(genre)
        if (genres.isEmpty()) return emptyList()

        // Catégorie du film courant (pondération de proximité dans le tri).
        val currentCategoryId = vodDao.getStreamById(currentStreamId)?.categoryId

        // Préfiltre SQL : union des candidats matchant au moins un genre (LIKE),
        // dédupliqués par streamId, le film courant et les catégories masquées
        // exclus (avant le classement, pour que le top `limit` retourné soit
        // toujours composé de candidats visibles).
        val candidateEntities = LinkedHashMap<Int, VodStreamEntity>()
        for (g in genres) {
            vodDao.getStreamsByGenre("%$g%").forEach { e ->
                if (e.streamId != currentStreamId && e.categoryId !in excludedCategoryIds) candidateEntities[e.streamId] = e
            }
        }
        if (candidateEntities.isEmpty()) return emptyList()

        val candidates = candidateEntities.values.map { e ->
            com.cstv.app.domain.model.RelatedTitlesSelector.Candidate(
                item = e,
                genres = com.cstv.app.domain.model.GenreParser.parseGenres(e.genre),
                rating = e.rating?.trim()?.toDoubleOrNull() ?: 0.0,
                added = e.added?.trim()?.toLongOrNull() ?: 0L,
                categoryId = e.categoryId
            )
        }

        return com.cstv.app.domain.model.RelatedTitlesSelector.select(genres, currentCategoryId, candidates, limit)
            .map { VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 }) }
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
            vodDao.insertStreamsWithFts(listOf(
                cachedStream.copy(
                    actors = actors,
                    director = director,
                    genre = genre,
                    releaseYear = ReleaseYearParser.parseYear(releaseDate) ?: 0
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeAllPlaybackPositions(): Flow<List<PlaybackPosition>> {
        return profileManager.activeProfileId.flatMapLatest { profileId ->
            vodDao.observeAllPlaybackPositions(profileId)
        }.map { entities ->
            entities.map { entity ->
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

    override suspend fun getStreamById(streamId: Int): VodStream? {
        val entity = vodDao.getStreamById(streamId) ?: return null
        return VodStream(
            streamId = entity.streamId,
            name = entity.name,
            streamIcon = entity.streamIcon,
            rating = entity.rating,
            added = entity.added,
            categoryId = entity.categoryId,
            genre = entity.genre,
            releaseYear = entity.releaseYear?.takeIf { it > 0 }
        )
    }
}
