package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.SeriesDao
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.SeriesCategoryEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val apiService: XtreamApiService,
    private val seriesDao: SeriesDao,
    private val vodDao: VodDao,
    private val credentialsManager: CredentialsManager
) : SeriesRepository {

    companion object {
        private const val CACHE_EXPIRY_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
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

        val remoteCategories = apiService.getSeriesCategories(creds.username, creds.password)

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
                if (lastAllStreamsSyncAt != 0L && currentTime - lastAllStreamsSyncAt < CACHE_EXPIRY_MILLIS) {
                    val localStreams = seriesDao.getAllStreams()
                    return localStreams.map {
                        SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
                    }
                }
            } else {
                val localStreams = seriesDao.getStreamsByCategory(categoryId)
                if (localStreams.isNotEmpty()) {
                    val lastCachedAt = localStreams.first().cachedAt
                    if (currentTime - lastCachedAt < CACHE_EXPIRY_MILLIS) {
                        return localStreams.map {
                            SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
                        }
                    }
                }
            }
        }

        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val remoteStreams = apiService.getSeriesStreams(creds.username, creds.password, apiCategoryId)

        // Preserve actors/director/genre enrichment (only ever fetched via getSeriesDetails,
        // never part of this bulk list response) so a routine cache refresh doesn't wipe it.
        val existingById = (if (categoryId == "all") seriesDao.getAllStreams() else seriesDao.getStreamsByCategory(categoryId))
            .associateBy { it.seriesId }

        val entities = remoteStreams.mapNotNull { dto ->
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
                    genre = existing?.genre
                )
            } else null
        }

        if (categoryId == "all") {
            seriesDao.clearAllStreams()
        } else {
            seriesDao.clearStreamsByCategory(categoryId)
        }

        if (entities.isNotEmpty()) {
            seriesDao.insertStreams(entities)
        }

        if (categoryId == "all") {
            lastAllStreamsSyncAt = currentTime
        }

        return entities.map { 
            SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
        }
    }

    override suspend fun getSeriesDetails(seriesId: Int): SeriesDetails {
        val creds = credentialsManager.getCredentials()
            ?: throw InvalidCredentialsException("Utilisateur non connecté.")

        val response = apiService.getSeriesInfo(creds.username, creds.password, seriesId)
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
                    
                    val savedPosition = vodDao.getPlaybackPosition(idInt)

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
            seriesDao.insertStreams(listOf(
                cachedSeries.copy(
                    actors = actors,
                    director = director,
                    genre = genre
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
            positionMs = positionMs,
            durationMs = durationMs,
            lastAccessedAt = System.currentTimeMillis()
        )
        vodDao.savePlaybackPosition(entity)
    }

    override suspend fun getPlaybackPosition(episodeStreamId: Int): Pair<Long, Long>? {
        val entity = vodDao.getPlaybackPosition(episodeStreamId) ?: return null
        return Pair(entity.positionMs, entity.durationMs)
    }

    override suspend fun clearPlaybackPosition(episodeStreamId: Int) {
        vodDao.deletePlaybackPosition(episodeStreamId)
    }
}
