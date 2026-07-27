package com.cstv.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.sync.CacheTtl
import com.cstv.app.domain.network.NetworkMonitor
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val networkMonitor: NetworkMonitor
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
        networkMonitor: NetworkMonitor,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, vodDao, credentialsManager, profileManager, requestGate, networkMonitor) {
        this.enrichmentDispatcher = dispatcher
    }

    /**
     * Déclenché uniquement après une récupération distante du catalogue (sync
     * planifié, rafraîchissement explicite), c'est-à-dire quand des films
     * viennent d'entrer en base. Le brancher aussi sur les lectures servies
     * depuis le cache relançait un lot à chaque ouverture de l'onglet Films ou
     * changement de catégorie, soit un trafic permanent vers le panel.
     */
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
                        vodDao.insertStreams(listOf(
                            currentStream.copy(
                                actors = actors,
                                director = director,
                                genre = genre,
                                releaseYear = releaseYear
                            )
                        ))
                    }
                    // Les deux catalogues enrichissent en parallèle : à 200 ms le
                    // compte tirait jusqu'à 10 req/s et se faisait rejeter (403).
                    delay(ENRICHMENT_REQUEST_SPACING_MS)
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
        const val ALL_CATEGORIES = "all"
        // Les durées de vie du cache vivent désormais dans CacheTtl : la
        // constante locale d'origine était déclarée ici mais jamais lue.
        private const val ENRICHMENT_BATCH_SIZE = 50
        private const val ENRICHMENT_REQUEST_SPACING_MS = 500L
        private const val DEFAULT_CONTAINER_EXTENSION = "mp4"
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

    private fun VodCategoryEntity.toDomain() = VodCategory(categoryId, categoryName, parentId)

    private fun VodStreamEntity.toDomain() = VodStream(
        streamId, name, streamIcon, rating, added, categoryId, genre,
        releaseYear?.takeIf { it > 0 }, actors, director, searchText
    )

    override fun observeVodCategories(): Flow<List<VodCategory>> =
        vodDao.observeAllCategories()
            .distinctUntilChanged()
            .map { categories -> categories.map { it.toDomain() } }

    override fun observeVodStreams(categoryId: String): Flow<List<VodStream>> =
        (if (categoryId == ALL_CATEGORIES) vodDao.observeAllStreams() else vodDao.observeStreamsByCategory(categoryId))
            .distinctUntilChanged()
            .map { streams -> streams.map { it.toDomain() } }

    override suspend fun getCachedVodCategories(): List<VodCategory> =
        vodDao.getAllCategories().map { it.toDomain() }

    override suspend fun getCachedVodStreams(categoryId: String): List<VodStream> =
        (if (categoryId == ALL_CATEGORIES) vodDao.getAllStreams() else vodDao.getStreamsByCategory(categoryId))
            .map { it.toDomain() }

    override suspend fun getCachedVodStreamsByYears(years: Set<Int>): List<VodStream> =
        if (years.isEmpty()) getCachedVodStreams(ALL_CATEGORIES)
        else vodDao.getStreamsByReleaseYears(years.toList()).map { it.toDomain() }

    override suspend fun syncVodCategories(): List<VodCategory> {
        val currentTime = System.currentTimeMillis()
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")
        val entities = requestGate.acquire { apiService.getVodCategories(creds.username, creds.password) }
            .mapIndexedNotNull { index, dto ->
                val id = dto.categoryId
                val name = dto.categoryName
                if (id != null && name != null) VodCategoryEntity(id, name, dto.parentId ?: 0, currentTime, index) else null
            }
        vodDao.replaceAllCategories(entities)
        return entities.map { it.toDomain() }
    }

    override suspend fun syncVodStreams(categoryId: String): List<VodStream> {
        val currentTime = System.currentTimeMillis()
        val apiCategoryId = if (categoryId == ALL_CATEGORIES) null else categoryId
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteStreams = requestGate.acquire { apiService.getVodStreams(creds.username, creds.password, apiCategoryId) }

        // Preserve actors/director/genre enrichment (only ever fetched via getVodDetails,
        // never part of this bulk list response) so a routine cache refresh doesn't wipe it.
        val existingById = (if (categoryId == ALL_CATEGORIES) vodDao.getAllStreams() else vodDao.getStreamsByCategory(categoryId))
            .associateBy { it.streamId }

        val entities = remoteStreams.mapIndexedNotNull { index, dto ->
            val id = dto.streamId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != ALL_CATEGORIES }
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
                    releaseYear = existing?.releaseYear,
                    plot = existing?.plot,
                    duration = existing?.duration,
                    containerExtension = existing?.containerExtension,
                    detailsCachedAt = existing?.detailsCachedAt
                )
            } else null
        }

        // Remplacement atomique : effacement et repeuplement du catalogue dans
        // la même transaction, et jamais sur une réponse vide.
        if (categoryId == ALL_CATEGORIES) {
            vodDao.replaceAllStreams(entities)
        } else {
            vodDao.replaceStreamsByCategory(categoryId, entities)
        }


        startBackgroundEnrichment()

        return entities.map { it.toDomain() }
    }

    override fun getVodStreamsPaged(categoryId: String): Flow<PagingData<VodStream>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 100
            ),
            pagingSourceFactory = {
                if (categoryId == ALL_CATEGORIES) {
                    vodDao.getAllStreamsPaged()
                } else {
                    vodDao.getStreamsByCategoryPaged(categoryId)
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                entity.toDomain()
            }
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
            .map { VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText) }
    }

    /**
     * Fiche de repli quand le panel refuse les métadonnées : tout ce qui est
     * connu localement. [VodDetails.isMetadataIncomplete] permet à l'écran
     * de le signaler plutôt que de faire passer les trous pour des vraies valeurs.
     */
    private suspend fun cachedVodDetails(
        cached: com.cstv.app.data.local.entity.VodStreamEntity,
        streamId: Int
    ): VodDetails {
        val savedPosition = vodDao.getPlaybackPosition(streamId, profileManager.currentProfileId())
        return VodDetails(
            streamId = streamId,
            name = cached.name,
            director = cached.director ?: "Inconnu",
            actors = cached.actors ?: "Inconnu",
            releaseDate = cached.releaseYear?.takeIf { it > 0 }?.toString() ?: "Inconnu",
            genre = cached.genre ?: "Inconnu",
            plot = cached.plot ?: "Aucun résumé disponible.",
            rating = formatRating(cached.rating),
            coverBig = cached.streamIcon,
            containerExtension = cached.containerExtension ?: DEFAULT_CONTAINER_EXTENSION,
            resumePositionMs = savedPosition?.positionMs ?: 0L,
            durationMs = savedPosition?.durationMs ?: 0L,
            duration = cached.duration,
            isMetadataIncomplete = cached.detailsCachedAt == null
        )
    }

    override suspend fun getVodDetails(streamId: Int): VodDetails {
        val cachedStream = vodDao.getStreamById(streamId)

        // Cache d'abord : une fiche déjà enrichie et encore fraîche n'a aucune
        // raison de redemander get_vod_info, principal contributeur au
        // bannissement. Hors ligne, elle est servie quel que soit son âge.
        if (cachedStream?.detailsCachedAt != null) {
            val fresh = !CacheTtl.isExpired(
                cachedStream.detailsCachedAt,
                CacheTtl.DETAILS_MILLIS,
                System.currentTimeMillis()
            )
            if (fresh || !networkMonitor.isCurrentlyOnline()) {
                return cachedVodDetails(cachedStream, streamId)
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: return cachedStream?.let { cachedVodDetails(it, streamId) }
                ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        // Certains panels refusent `get_vod_info` sur des titres précis (403
        // reproductible) alors que le flux reste lisible. La fiche est reconstruite
        // depuis le catalogue déjà en base plutôt que de rester inaccessible :
        // c'est la même matière que celle affichée dans la liste.
        val response = try {
            requestGate.acquire { apiService.getVodInfo(creds.username, creds.password, streamId) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return cachedStream?.let { cached -> cachedVodDetails(cached, streamId) }
                ?: throw e
        }
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
        val extension = movieDataDto?.containerExtension ?: DEFAULT_CONTAINER_EXTENSION
        val duration = formatDuration(infoDto?.duration)

        // Fetch resume position from local DB if exists
        val savedPosition = vodDao.getPlaybackPosition(streamId, profileManager.currentProfileId())

        // Sensationally enrich cached stream entity with actors, director, and genre details
        if (cachedStream != null) {
            vodDao.insertStreams(listOf(
                cachedStream.copy(
                    actors = actors,
                    director = director,
                    genre = genre,
                    releaseYear = ReleaseYearParser.parseYear(releaseDate) ?: 0,
                    plot = plot,
                    duration = duration,
                    containerExtension = extension,
                    detailsCachedAt = System.currentTimeMillis()
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
            releaseYear = entity.releaseYear?.takeIf { it > 0 },
            actors = entity.actors,
            director = entity.director,
            searchText = entity.searchText
        )
    }
}
