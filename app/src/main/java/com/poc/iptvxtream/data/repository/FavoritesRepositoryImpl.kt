package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {

    override suspend fun getFavorites(): List<FavoriteItem> {
        return favoritesDao.getAllFavorites().map { 
            FavoriteItem(it.id, it.type, it.name, it.cover, it.categoryId)
        }
    }

    override suspend fun isFavorite(id: Int, type: String): Boolean {
        return favoritesDao.isFavorite(id, type)
    }

    override suspend fun addFavorite(favorite: FavoriteItem) {
        val entity = FavoriteEntity(
            id = favorite.id,
            type = favorite.type,
            name = favorite.name,
            cover = favorite.cover,
            categoryId = favorite.categoryId,
            addedAt = System.currentTimeMillis()
        )
        favoritesDao.addFavorite(entity)
    }

    override suspend fun removeFavorite(id: Int, type: String) {
        favoritesDao.removeFavorite(id, type)
    }

    override suspend fun searchUnified(query: String): SearchResult {
        if (query.trim().isBlank()) return SearchResult()

        val sqlQuery = "%${query.trim()}%"
        
        val liveEntities = favoritesDao.searchLiveStreams(sqlQuery)
        val vodEntities = favoritesDao.searchVodStreams(sqlQuery)
        val seriesEntities = favoritesDao.searchSeriesStreams(sqlQuery)

        return SearchResult(
            liveResults = liveEntities.map { 
                LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
            },
            vodResults = vodEntities.map { 
                VodStream(it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId)
            },
            seriesResults = seriesEntities.map { 
                SeriesStream(it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId)
            }
        )
    }

    override suspend fun getSearchSuggestions(query: String, limit: Int): List<SearchSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val sqlQuery = "%$trimmed%"
        
        val liveEntities = favoritesDao.suggestLiveStreams(sqlQuery, limit)
        val vodEntities = favoritesDao.suggestVodStreams(sqlQuery, limit)
        val seriesEntities = favoritesDao.suggestSeriesStreams(sqlQuery, limit)

        val suggestions = mutableListOf<SearchSuggestion>()

        for (entity in liveEntities) {
            val item = LiveStream(entity.streamId, entity.name, entity.streamIcon, entity.epgChannelId, entity.num, entity.categoryId)
            suggestions.add(SearchSuggestion.Item(
                id = entity.streamId,
                name = entity.name,
                type = "live",
                iconOrCover = entity.streamIcon,
                categoryId = entity.categoryId,
                underlyingItem = item
            ))
        }

        val termSuggestions = mutableSetOf<String>()
        for (entity in vodEntities) {
            val item = VodStream(entity.streamId, entity.name, entity.streamIcon, entity.rating, entity.added, entity.categoryId)
            
            suggestions.add(SearchSuggestion.Item(
                id = entity.streamId,
                name = entity.name,
                type = "movie",
                iconOrCover = entity.streamIcon,
                categoryId = entity.categoryId,
                underlyingItem = item
            ))

            val director = entity.director
            if (!director.isNullOrBlank() && director != "Inconnu" && director.contains(trimmed, ignoreCase = true)) {
                termSuggestions.add(director.trim())
            }

            val actors = entity.actors
            if (!actors.isNullOrBlank() && actors != "Inconnu") {
                actors.split(",").forEach { actor ->
                    val cleanActor = actor.trim()
                    if (cleanActor.contains(trimmed, ignoreCase = true)) {
                        termSuggestions.add(cleanActor)
                    }
                }
            }
        }

        for (entity in seriesEntities) {
            val item = SeriesStream(entity.seriesId, entity.name, entity.cover, entity.rating, entity.added, entity.categoryId)
            
            suggestions.add(SearchSuggestion.Item(
                id = entity.seriesId,
                name = entity.name,
                type = "series",
                iconOrCover = entity.cover,
                categoryId = entity.categoryId,
                underlyingItem = item
            ))

            val director = entity.director
            if (!director.isNullOrBlank() && director != "Inconnu" && director.contains(trimmed, ignoreCase = true)) {
                termSuggestions.add(director.trim())
            }

            val actors = entity.actors
            if (!actors.isNullOrBlank() && actors != "Inconnu") {
                actors.split(",").forEach { actor ->
                    val cleanActor = actor.trim()
                    if (cleanActor.contains(trimmed, ignoreCase = true)) {
                        termSuggestions.add(cleanActor)
                    }
                }
            }
        }

        val terms = termSuggestions.map { SearchSuggestion.Term(it) }

        val sortedItems = suggestions.distinctBy { 
            if (it is SearchSuggestion.Item) "${it.type}_${it.id}" else "" 
        }.sortedWith(compareBy<SearchSuggestion> {
            if (it is SearchSuggestion.Item) {
                if (it.name.startsWith(trimmed, ignoreCase = true)) 0 else 1
            } else 2
        }.thenBy {
            if (it is SearchSuggestion.Item) it.name else ""
        })

        // Réserver au moins la moitié des emplacements aux items : sinon une
        // requête matchant beaucoup d'acteurs/réalisateurs remplirait toute la
        // limite de termes et masquerait les fiches précises (chaînes/films/séries),
        // que l'utilisateur doit pouvoir sélectionner pour aller à leur détail.
        val maxTerms = limit / 2
        val cappedTerms = terms.take(maxTerms)
        val remaining = limit - cappedTerms.size
        val cappedItems = sortedItems.take(remaining)

        return cappedTerms + cappedItems
    }
}
