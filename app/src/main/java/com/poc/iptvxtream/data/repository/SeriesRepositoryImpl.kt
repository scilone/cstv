package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.SeriesDao
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.SeriesCategoryEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.remote.api.RequestPriority
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.api.XtreamRequestGate
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val seriesDao: SeriesDao,
    private val vodDao: VodDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.poc.iptvxtream.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate,
    private val settingsManager: SettingsManager
) : SeriesRepository {

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
        seriesDao: SeriesDao,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: com.poc.iptvxtream.data.local.storage.ProfileManager,
        requestGate: XtreamRequestGate,
        settingsManager: SettingsManager,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, seriesDao, vodDao, credentialsManager, profileManager, requestGate, settingsManager) {
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
     * Enrichit un lot d'au plus [limit] séries dont actors/director/genre
     * manquent encore. Retourne le nombre de séries traitées (succès ou échec
     * individuel confondus) : un lot plein signale qu'il reste probablement
     * du travail, un lot partiel/vide signale un catalogue à jour.
     */
    private suspend fun enrichBatch(creds: Credentials, limit: Int): Int {
        return try {
            val needingEnrichment = seriesDao.getStreamsNeedingEnrichment(limit)
            for (stream in needingEnrichment) {
                try {
                    val response = requestGate.acquire { apiService.getSeriesInfo(creds.username, creds.password, stream.seriesId) }
                    val infoDto = response.info
                    val director = infoDto?.director ?: "Inconnu"
                    val genre = infoDto?.genre ?: "Inconnu"
                    val actors = extractActors(infoDto?.actors, infoDto?.cast)
                    // Sentinelle 0 = "vérifié mais année inconnue" (mappée en null en domain).
                    val releaseYear = ReleaseYearParser.parseYear(
                        infoDto?.releaseDate ?: infoDto?.releaseDate2
                    ) ?: 0

                    val currentStream = seriesDao.getStreamById(stream.seriesId)
                    if (currentStream != null) {
                        seriesDao.insertStreamsWithFts(listOf(
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
                    // ignore individual failure
                }
            }
            needingEnrichment.size
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // handle errors gracefully
            0
        }
    }

    override suspend fun enrichPendingSeries(maxBatches: Int): Int {
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

    private fun formatRating(ratingStr: String?): String {
        if (ratingStr.isNullOrBlank()) return "0.0"
        val doubleVal = ratingStr.trim().toDoubleOrNull() ?: return "0.0"
        return String.format(java.util.Locale.US, "%.1f", doubleVal)
    }

    override suspend fun getSeriesCategories(forceRefresh: Boolean): List<SeriesCategory> {
        val currentTime = System.currentTimeMillis()

        if (!forceRefresh) {
            val localCategories = seriesDao.getAllCategories()
            if (localCategories.isNotEmpty()) {
                val lastCachedAt = localCategories.first().cachedAt
                if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                    return localCategories.map { 
                        SeriesCategory(it.categoryId, it.categoryName, it.parentId)
                    }
                }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteCategories = requestGate.acquire { apiService.getSeriesCategories(creds.username, creds.password) }

        val entities = remoteCategories.mapIndexedNotNull { index, dto ->
            val id = dto.categoryId
            val name = dto.categoryName
            if (id != null && name != null) {
                SeriesCategoryEntity(
                    categoryId = id,
                    categoryName = name,
                    parentId = dto.parentId ?: 0,
                    cachedAt = currentTime,
                    orderIndex = index
                )
            } else null
        }

        if (entities.isNotEmpty()) {
            seriesDao.clearCategories()
            seriesDao.insertCategories(entities)
        }

        return entities.map { 
            SeriesCategory(it.categoryId, it.categoryName, it.parentId)
        }
    }

    override suspend fun getSeriesStreams(categoryId: String, forceRefresh: Boolean): List<SeriesStream> {
        val currentTime = System.currentTimeMillis()
        val apiCategoryId = if (categoryId == "all") null else categoryId

        if (!forceRefresh) {
            if (categoryId == "all") {
                val lastAllStreamsSyncAt = settingsManager.getSeriesAllStreamsSyncedAt()
                if (lastAllStreamsSyncAt != 0L && currentTime - lastAllStreamsSyncAt < CACHE_EXPIRY_MILLIS) {
                    val localStreams = seriesDao.getAllStreams()
                    startBackgroundEnrichment()
                    return localStreams.map {
                        SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
                    }
                }
            } else {
                val localStreams = seriesDao.getStreamsByCategory(categoryId)
                if (localStreams.isNotEmpty()) {
                    val lastCachedAt = localStreams.first().cachedAt
                    if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                        startBackgroundEnrichment()
                        return localStreams.map {
                            SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
                        }
                    }
                }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteStreams = requestGate.acquire { apiService.getSeriesStreams(creds.username, creds.password, apiCategoryId) }

        // Preserve actors/director/genre enrichment (only ever fetched via getSeriesDetails,
        // never part of this bulk list response) so a routine cache refresh doesn't wipe it.
        val existingById = (if (categoryId == "all") seriesDao.getAllStreams() else seriesDao.getStreamsByCategory(categoryId))
            .associateBy { it.seriesId }

        val entities = remoteStreams.mapIndexedNotNull { index, dto ->
            val id = dto.seriesId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != "all" }
            if (id != null && name != null && itemCategoryId != null) {
                val existing = existingById[id]
                SeriesStreamEntity(
                    seriesId = id,
                    name = name,
                    cover = dto.cover,
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
            seriesDao.clearAllStreams()
            seriesDao.clearAllFts()
        } else {
            seriesDao.clearStreamsByCategory(categoryId)
            seriesDao.clearFtsByCategory(categoryId)
        }

        if (entities.isNotEmpty()) {
            seriesDao.insertStreamsWithFts(entities)
        }

        if (categoryId == "all") {
            settingsManager.setSeriesAllStreamsSyncedAt(currentTime)
        }

        startBackgroundEnrichment()

        return entities.map { 
            SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
        }
    }

    override suspend fun getCategoryCounts(): Map<String, Int> {
        return seriesDao.getCategoryCounts().associate { it.categoryId to it.count }
    }

    override suspend fun getRelatedSeries(
        currentSeriesId: Int,
        genre: String?,
        limit: Int,
        excludedCategoryIds: Set<String>
    ): List<SeriesStream> {
        val genres = GenreParser.parseGenres(genre)
        if (genres.isEmpty()) return emptyList()

        // Catégorie de la série courante (pondération de proximité dans le tri).
        val currentCategoryId = seriesDao.getStreamById(currentSeriesId)?.categoryId

        // Préfiltre SQL : union des candidats matchant au moins un genre (LIKE),
        // dédupliqués par seriesId, la série courante et les catégories masquées
        // exclues (avant le classement, pour que le top `limit` retourné soit
        // toujours composé de candidats visibles).
        val candidateEntities = LinkedHashMap<Int, com.poc.iptvxtream.data.local.entity.SeriesStreamEntity>()
        for (g in genres) {
            seriesDao.getStreamsByGenre("%$g%").forEach { e ->
                if (e.seriesId != currentSeriesId && e.categoryId !in excludedCategoryIds) candidateEntities[e.seriesId] = e
            }
        }
        if (candidateEntities.isEmpty()) return emptyList()

        val candidates = candidateEntities.values.map { e ->
            RelatedTitlesSelector.Candidate(
                item = e,
                genres = GenreParser.parseGenres(e.genre),
                rating = e.rating?.trim()?.toDoubleOrNull() ?: 0.0,
                added = e.added?.trim()?.toLongOrNull() ?: 0L,
                categoryId = e.categoryId
            )
        }

        return RelatedTitlesSelector.select(genres, currentCategoryId, candidates, limit)
            .map { SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId) }
    }

    override suspend fun getSeriesDetails(seriesId: Int): SeriesDetails {
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val response = requestGate.acquire { apiService.getSeriesInfo(creds.username, creds.password, seriesId) }
        val infoDto = response.info

        // Parse series metadata
        val director = infoDto?.director ?: "Inconnu"
        val releaseDate = infoDto?.releaseDate ?: infoDto?.releaseDate2 ?: "Inconnu"
        val genre = infoDto?.genre ?: "Inconnu"
        val plot = infoDto?.plot ?: "Aucun résumé disponible."
        val rating = infoDto?.rating ?: infoDto?.rating5 ?: "0"
        val roundedRating = formatRating(rating)
        val actors = extractActors(infoDto?.actors, infoDto?.cast)

        // 1. Map Seasons
        val seasons = response.seasons?.mapNotNull { dto ->
            val num = dto.seasonNumber
            val name = dto.name ?: "Saison ${num ?: 1}"
            if (num != null) {
                SeriesSeason(num, name, dto.episodeCount ?: 0, dto.cover)
            } else null
        } ?: emptyList()

        // 2. Map Episodes
        val episodesMap = mutableMapOf<Int, List<SeriesEpisode>>()
        
        response.episodes?.forEach { (seasonStr, dtoList) ->
            val seasonNum = seasonStr.toIntOrNull() ?: 1
            val episodesList = dtoList.mapNotNull { dto ->
                val idInt = dto.id?.toIntOrNull()
                val numInt = dto.episodeNum?.toIntOrNull() ?: 1
                val title = dto.title ?: "Épisode $numInt"
                val ext = dto.containerExtension ?: "mp4"
                
                if (idInt != null) {
                    val episodePlot = dto.info?.plot ?: "Aucun résumé disponible."
                    val duration = dto.info?.duration ?: "00:00"
                    val rDate = dto.info?.releaseDate ?: ""
                    val movieImage = dto.info?.movieImage ?: dto.movieImage
                    
                    val savedPosition = vodDao.getPlaybackPosition(idInt, profileManager.currentProfileId())

                    SeriesEpisode(
                        id = idInt,
                        episodeNum = numInt,
                        title = title,
                        containerExtension = ext,
                        plot = episodePlot,
                        duration = duration,
                        releaseDate = rDate,
                        resumePositionMs = savedPosition?.positionMs ?: 0L,
                        durationMs = savedPosition?.durationMs ?: 0L,
                        movieImage = movieImage,
                        lastAccessedAt = savedPosition?.lastAccessedAt ?: 0L,
                        seasonNum = seasonNum
                    )
                } else null
            }
            episodesMap[seasonNum] = episodesList.sortedBy { it.episodeNum }
        }

        // Fetch series cover and name from cached stream entity
        val cachedSeries = seriesDao.getStreamById(seriesId)
        val seriesName = infoDto?.name ?: cachedSeries?.name ?: seasons.firstOrNull()?.name?.substringBefore(" Season") ?: "Série"
        val coverUrl = infoDto?.cover ?: cachedSeries?.cover ?: seasons.firstOrNull()?.cover

        // Sensationally enrich cached stream entity with actors, director, and genre details
        if (cachedSeries != null) {
            seriesDao.insertStreamsWithFts(listOf(
                cachedSeries.copy(
                    actors = actors,
                    director = director,
                    genre = genre,
                    releaseYear = ReleaseYearParser.parseYear(releaseDate) ?: 0
                )
            ))
        }

        return SeriesDetails(
            seriesId = seriesId,
            name = seriesName,
            cover = coverUrl,
            rating = roundedRating,
            seasons = seasons.sortedBy { it.seasonNumber },
            episodes = episodesMap,
            director = director,
            releaseDate = releaseDate,
            genre = genre,
            plot = plot,
            actors = actors
        )
    }

    override suspend fun savePlaybackPosition(episodeStreamId: Int, positionMs: Long, durationMs: Long) {
        val entity = PlaybackPositionEntity(
            streamId = episodeStreamId,
            profileId = profileManager.currentProfileId(),
            positionMs = positionMs,
            durationMs = durationMs,
            lastAccessedAt = System.currentTimeMillis()
        )
        vodDao.savePlaybackPosition(entity)
    }

    override suspend fun getPlaybackPosition(episodeStreamId: Int): Pair<Long, Long>? {
        val entity = vodDao.getPlaybackPosition(episodeStreamId, profileManager.currentProfileId()) ?: return null
        return Pair(entity.positionMs, entity.durationMs)
    }

    override suspend fun clearPlaybackPosition(episodeStreamId: Int) {
        vodDao.deletePlaybackPosition(episodeStreamId, profileManager.currentProfileId())
    }
}
