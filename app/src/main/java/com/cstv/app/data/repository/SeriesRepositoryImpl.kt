package com.cstv.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.SeriesCategoryEntity
import com.cstv.app.data.local.dao.SeriesStreamListRow
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.withParsedTitle
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.RequestPriority
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.sync.CacheTtl
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.SeriesRepository
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un épisode « vu » se reconnaît à une ligne de position **présente** mais
 * remise à zéro : c'est ce que fait le lecteur quand la lecture atteint la fin
 * (`SeriesPlayerScreen.onTrackerDispose` → `SeriesViewModel.clearPosition`, qui
 * réécrit `positionMs = 0` plutôt que de supprimer la ligne). Un épisode jamais
 * lancé n'a, lui, aucune ligne.
 *
 * Fonction pure, sans dépendance Room au-delà du type d'entité, pour rester
 * testable en JVM.
 */
internal fun isEpisodeWatched(savedPosition: com.cstv.app.data.local.dao.PlaybackListRow?): Boolean =
    savedPosition != null && savedPosition.positionMs <= 0L

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val seriesDao: SeriesDao,
    private val vodDao: VodDao,
    private val credentialsManager: CredentialsManager,
    private val profileManager: com.cstv.app.data.local.storage.ProfileManager,
    private val requestGate: XtreamRequestGate,
    private val networkMonitor: NetworkMonitor,
    private val accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider
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
        profileManager: com.cstv.app.data.local.storage.ProfileManager,
        requestGate: XtreamRequestGate,
        networkMonitor: NetworkMonitor,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, seriesDao, vodDao, credentialsManager, profileManager, requestGate, networkMonitor, accountKeyProvider) {
        this.enrichmentDispatcher = dispatcher
    }

    /**
     * Déclenché uniquement après une récupération distante du catalogue (sync
     * planifié, rafraîchissement explicite), c'est-à-dire quand des séries
     * viennent d'entrer en base. Le brancher aussi sur les lectures servies
     * depuis le cache relançait un lot à chaque ouverture de l'onglet Séries ou
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
                        seriesDao.insertStreams(listOf(
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
        const val ALL_CATEGORIES = "all"
        /** F39 §8.7 : plafond défensif, protège d'une clé de liaison anormalement large. */
        private const val MAX_VERSIONS_PER_LINK_KEY = 20
        // Les durées de vie du cache vivent désormais dans CacheTtl : la
        // constante locale d'origine était déclarée ici mais jamais lue.
        private const val ENRICHMENT_BATCH_SIZE = 50
        private const val ENRICHMENT_REQUEST_SPACING_MS = 500L
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

    private fun SeriesCategoryEntity.toDomain() = SeriesCategory(categoryId, categoryName, parentId)

    private fun SeriesStreamEntity.toDomain() = SeriesStream(
        seriesId, name, cover, rating, added, categoryId, genre,
        releaseYear?.takeIf { it > 0 }, actors, director, searchText,
        cleanTitle, linkKey, languageTag, languageRaw, qualityTag, qualityRaw, versionLabel
    )

    // --- Lecture locale ---

    override fun observeSeriesCategories(): Flow<List<SeriesCategory>> =
        seriesDao.observeAllCategories()
            .distinctUntilChanged()
            .map { categories -> categories.map { it.toDomain() } }

    /** Voir VodRepositoryImpl : projection de liste, champs de recherche vides. */
    override fun observeSeriesStreams(categoryId: String): Flow<List<SeriesStream>> =
        (if (categoryId == ALL_CATEGORIES) {
            seriesDao.observeAllStreamListRows(com.cstv.app.data.local.dao.ALL_MODE_ROWS_PER_CATEGORY)
        } else {
            seriesDao.observeStreamListRowsByCategory(categoryId)
        })
            .distinctUntilChanged()
            .map { rows ->
                val startedAt = System.nanoTime()
                val mapped = rows.map { it.toDomain() }
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "Séries projection→domaine ${rows.size} lignes en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
                mapped
            }

    private fun SeriesStreamListRow.toDomain() = SeriesStream(
        seriesId = seriesId,
        name = name,
        cover = cover,
        rating = rating,
        added = added,
        categoryId = categoryId,
        genre = genre,
        releaseYear = releaseYear?.takeIf { it > 0 },
        languageTag = languageTag,
        qualityTag = qualityTag,
        versionLabel = versionLabel
    )

    override suspend fun getCachedSeriesCategories(): List<SeriesCategory> =
        seriesDao.getAllCategories().map { it.toDomain() }

    override suspend fun getCachedSeriesStreams(categoryId: String): List<SeriesStream> =
        (if (categoryId == ALL_CATEGORIES) seriesDao.getAllStreams() else seriesDao.getStreamsByCategory(categoryId))
            .map { it.toDomain() }

    // Voir VodRepositoryImpl.getCachedVodStreamsByYears : même requête paginée
    // par année, même déduplication.
    override suspend fun getCachedSeriesStreamsByYears(years: Set<Int>): List<SeriesStream> {
        if (years.isEmpty()) return getCachedSeriesStreams(ALL_CATEGORIES)

        val candidates = LinkedHashMap<Int, SeriesStreamEntity>()
        for (year in years) {
            var offset = 0
            while (true) {
                val page = seriesDao.getStreamsByReleaseYearPage(
                    year = year,
                    yearPattern = "%$year%",
                    limit = VodRepositoryImpl.YEAR_MATCH_PAGE_SIZE,
                    offset = offset
                )
                page.forEach { candidates[it.seriesId] = it }
                if (page.size < VodRepositoryImpl.YEAR_MATCH_PAGE_SIZE) break
                offset += VodRepositoryImpl.YEAR_MATCH_PAGE_SIZE
            }
        }
        return candidates.values.map { it.toDomain() }
    }

    // --- Écriture (synchronisation) ---

    override suspend fun syncSeriesCategories(): List<SeriesCategory> {
        val currentTime = System.currentTimeMillis()
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

        seriesDao.replaceAllCategories(entities)
        return entities.map { it.toDomain() }
    }

    override suspend fun syncSeriesStreams(categoryId: String): List<SeriesStream> {
        val currentTime = System.currentTimeMillis()
        val apiCategoryId = if (categoryId == ALL_CATEGORIES) null else categoryId

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteStreams = requestGate.acquire { apiService.getSeriesStreams(creds.username, creds.password, apiCategoryId) }

        // Preserve actors/director/genre enrichment (only ever fetched via getSeriesDetails,
        // never part of this bulk list response) so a routine cache refresh doesn't wipe it.
        val existingById = (if (categoryId == ALL_CATEGORIES) seriesDao.getAllStreams() else seriesDao.getStreamsByCategory(categoryId))
            .associateBy { it.seriesId }

        val rankCounter = CategoryRankCounter()
        val entities = remoteStreams.mapIndexedNotNull { index, dto ->
            val id = dto.seriesId
            val name = dto.name
            // In "all" mode there's no known category to fall back to; a stream without
            // a category_id would otherwise be tagged with the literal "all" and become
            // invisible in every section, so skip it instead.
            val itemCategoryId = dto.categoryId ?: categoryId.takeIf { it != ALL_CATEGORIES }
            if (id != null && name != null && itemCategoryId != null) {
                val existing = existingById[id]
                val parsedTitle = MediaTitleParser.parse(name, MediaTitleKind.SERIES, existing?.releaseYear, id)
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
                    categoryRank = rankCounter.next(itemCategoryId),
                    releaseYear = existing?.releaseYear,
                    plot = existing?.plot,
                    detailsCachedAt = existing?.detailsCachedAt
                ).withParsedTitle(parsedTitle)
            } else null
        }

        // Remplacement atomique : effacement et repeuplement du catalogue dans
        // la même transaction, et jamais sur une réponse vide.
        if (categoryId == ALL_CATEGORIES) {
            seriesDao.replaceAllStreams(entities)
        } else {
            seriesDao.replaceStreamsByCategory(categoryId, entities)
        }


        startBackgroundEnrichment()

        return entities.map { it.toDomain() }
    }

    override fun getSeriesStreamsPaged(categoryId: String): Flow<PagingData<SeriesStream>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 100
            ),
            pagingSourceFactory = {
                if (categoryId == ALL_CATEGORIES) {
                    seriesDao.getAllStreamsPaged()
                } else {
                    seriesDao.getStreamsByCategoryPaged(categoryId)
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getCategoryCounts(): Map<String, Int> {
        return seriesDao.getCategoryCounts().associate { it.categoryId to it.count }
    }

    override suspend fun hasCachedSeriesStreams(): Boolean = seriesDao.hasStreams()

    override suspend fun getReleaseYearBounds(): Pair<Int, Int>? {
        val min = seriesDao.getMinReleaseYear() ?: return null
        val max = seriesDao.getMaxReleaseYear() ?: return null
        return min to max
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
        val candidateEntities = LinkedHashMap<Int, com.cstv.app.data.local.entity.SeriesStreamEntity>()
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
            .map { SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId, it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText) }
    }

    /**
     * Fiche reconstruite depuis la base locale.
     *
     * Les saisons et épisodes ne sont présents que si la série a déjà été
     * ouverte en ligne : les persister par balayage exigerait un
     * `get_series_info` par série, soit des milliers de requêtes — le remède
     * serait pire que le mal que T4 traite. Sans eux, la fiche reste affichable
     * en mode dégradé, signalé par [SeriesDetails.isMetadataIncomplete].
     */
    private suspend fun cachedSeriesDetails(
        cached: SeriesStreamEntity?,
        seriesId: Int
    ): SeriesDetails? {
        if (cached == null) return null

        val seasonEntities = seriesDao.getSeasons(seriesId)
        val episodeEntities = seriesDao.getEpisodes(seriesId)
        val savedPositions = vodDao.getAllPlaybackPositions(profileManager.currentProfileId(), accountKeyProvider.current())
            .associateBy { it.providerId }

        val episodesMap = episodeEntities
            .groupBy { it.seasonNum }
            .mapValues { (_, entities) ->
                entities.sortedBy { it.episodeNum }.map { e ->
                    val savedPosition = savedPositions[e.episodeId]
                    SeriesEpisode(
                        id = e.episodeId,
                        episodeNum = e.episodeNum,
                        title = e.title,
                        containerExtension = e.containerExtension,
                        plot = e.plot ?: "Aucun résumé disponible.",
                        duration = e.duration ?: "00:00",
                        releaseDate = e.releaseDate ?: "",
                        resumePositionMs = savedPosition?.positionMs ?: 0L,
                        durationMs = savedPosition?.durationMs ?: 0L,
                        movieImage = e.movieImage,
                        lastAccessedAt = savedPosition?.lastAccessedAt ?: 0L,
                        seasonNum = e.seasonNum,
                        watched = isEpisodeWatched(savedPosition)
                    )
                }
            }

        return SeriesDetails(
            seriesId = seriesId,
            name = cached.name,
            cover = cached.cover,
            rating = formatRating(cached.rating),
            seasons = seasonEntities.map { SeriesSeason(it.seasonNumber, it.name, it.episodeCount, it.cover) },
            episodes = episodesMap,
            director = cached.director ?: "Inconnu",
            releaseDate = cached.releaseYear?.takeIf { it > 0 }?.toString() ?: "Inconnu",
            genre = cached.genre ?: "Inconnu",
            plot = cached.plot ?: "Aucun résumé disponible.",
            actors = cached.actors ?: "Inconnu",
            isMetadataIncomplete = cached.detailsCachedAt == null || episodeEntities.isEmpty()
        )
    }

    override suspend fun getSeriesDetails(seriesId: Int): SeriesDetails {
        val cachedSeries = seriesDao.getStreamById(seriesId)

        // Cache d'abord : une fiche déjà persistée et encore fraîche n'a aucune
        // raison de redemander get_series_info. Hors ligne, elle est servie quel
        // que soit son âge — c'est ce qui rend la fiche consultable sans réseau.
        if (cachedSeries?.detailsCachedAt != null) {
            val fresh = !CacheTtl.isExpired(
                cachedSeries.detailsCachedAt,
                CacheTtl.DETAILS_MILLIS,
                System.currentTimeMillis()
            )
            if (fresh || !networkMonitor.isCurrentlyOnline()) {
                cachedSeriesDetails(cachedSeries, seriesId)?.let { return it }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: return cachedSeriesDetails(cachedSeries, seriesId)
                ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        // Un panel qui refuse ou ne répond pas ne doit pas rendre la fiche
        // inaccessible : on retombe sur ce qui est déjà en base.
        val response = try {
            requestGate.acquire { apiService.getSeriesInfo(creds.username, creds.password, seriesId) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return cachedSeriesDetails(cachedSeries, seriesId) ?: throw e
        }
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

        // Une seule lecture pour toute la série : interroger la table épisode par
        // épisode faisait autant d'allers-retours Room que la série a d'épisodes.
        val savedPositions = vodDao.getAllPlaybackPositions(profileManager.currentProfileId(), accountKeyProvider.current())
            .associateBy { it.providerId }

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
                    
                    val savedPosition = savedPositions[idInt]

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
                        seasonNum = seasonNum,
                        watched = isEpisodeWatched(savedPosition)
                    )
                } else null
            }
            episodesMap[seasonNum] = episodesList.sortedBy { it.episodeNum }
        }

        // Fetch series cover and name from cached stream entity
        val seriesName = infoDto?.name ?: cachedSeries?.name ?: seasons.firstOrNull()?.name?.substringBefore(" Season") ?: "Série"
        val coverUrl = infoDto?.cover ?: cachedSeries?.cover ?: seasons.firstOrNull()?.cover

        val now = System.currentTimeMillis()

        // Sensationally enrich cached stream entity with actors, director, and genre details
        if (cachedSeries != null) {
            seriesDao.insertStreams(listOf(
                cachedSeries.copy(
                    actors = actors,
                    director = director,
                    genre = genre,
                    releaseYear = ReleaseYearParser.parseYear(releaseDate) ?: 0,
                    plot = plot,
                    detailsCachedAt = now
                ).withParsedTitle(
                    MediaTitleParser.parse(
                        cachedSeries.name,
                        MediaTitleKind.SERIES,
                        ReleaseYearParser.parseYear(releaseDate) ?: 0,
                        cachedSeries.seriesId
                    )
                )
            ))
        }

        // Persistance à la consultation : c'est la seule alimentation des tables
        // saisons/épisodes. Hors ligne, seules les séries déjà ouvertes ont donc
        // leur détail complet, les autres retombent sur la fiche dégradée.
        persistSeriesDetail(seriesId, seasons, episodesMap, now)

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

    private suspend fun persistSeriesDetail(
        seriesId: Int,
        seasons: List<SeriesSeason>,
        episodesMap: Map<Int, List<SeriesEpisode>>,
        cachedAt: Long
    ) {
        val seasonEntities = seasons.map { season ->
            com.cstv.app.data.local.entity.SeriesSeasonEntity(
                seriesId = seriesId,
                seasonNumber = season.seasonNumber,
                name = season.name,
                episodeCount = season.episodeCount,
                cover = season.cover,
                cachedAt = cachedAt
            )
        }

        val episodeEntities = episodesMap.flatMap { (seasonNum, episodes) ->
            episodes.mapIndexed { index, episode ->
                com.cstv.app.data.local.entity.SeriesEpisodeEntity(
                    episodeId = episode.id,
                    seriesId = seriesId,
                    seasonNum = seasonNum,
                    episodeNum = episode.episodeNum,
                    title = episode.title,
                    containerExtension = episode.containerExtension,
                    plot = episode.plot,
                    duration = episode.duration,
                    releaseDate = episode.releaseDate,
                    movieImage = episode.movieImage,
                    orderIndex = index,
                    cachedAt = cachedAt
                )
            }
        }

        seriesDao.replaceSeriesDetail(seriesId, seasonEntities, episodeEntities)
    }

    override suspend fun getStreamById(seriesId: Int): SeriesStream? =
        // Bug corrigé : voir VodRepositoryImpl.getStreamById — même constructeur partiel,
        // `linkKey` toujours "" côté appelant, sélecteur de versions jamais déclenché.
        seriesDao.getStreamById(seriesId)?.toDomain()

    override suspend fun getVersionsByLinkKey(linkKey: String, releaseYear: Int?): List<SeriesStream> {
        if (linkKey.isBlank()) return emptyList()
        // F39-R8 : interroge un rang de plus que le plafond pour distinguer une vraie troncature
        // (une 21e ligne existe) d'un groupe qui tombe pile sur MAX_VERSIONS_PER_LINK_KEY.
        val rows = seriesDao.getStreamsByLinkKey(linkKey, releaseYear, MAX_VERSIONS_PER_LINK_KEY + 1)
        if (rows.size > MAX_VERSIONS_PER_LINK_KEY) {
            com.cstv.app.domain.model.MediaVersionCapDiagnostics.recordSeriesTruncation()
        }
        return rows.map { it.toDomain() }
            .sortedWith(compareByDescending<SeriesStream> { com.cstv.app.domain.model.mediaQualityRank(it.qualityTag) }.thenBy { it.seriesId })
            .take(MAX_VERSIONS_PER_LINK_KEY)
    }

    override suspend fun getEpisodeBySeasonEpisode(seriesId: Int, seasonNum: Int, episodeNum: Int): com.cstv.app.domain.model.SeriesEpisode? {
        val e = seriesDao.getEpisodeBySeasonEpisode(seriesId, seasonNum, episodeNum) ?: return null
        return com.cstv.app.domain.model.SeriesEpisode(
            id = e.episodeId,
            episodeNum = e.episodeNum,
            title = e.title,
            containerExtension = e.containerExtension,
            plot = e.plot ?: "Aucun résumé disponible.",
            duration = e.duration ?: "00:00",
            releaseDate = e.releaseDate ?: "",
            movieImage = e.movieImage,
            seasonNum = e.seasonNum
        )
    }
}
