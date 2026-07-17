package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val profileManager: ProfileManager
) : FavoritesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<FavoriteItem>> {
        return profileManager.activeProfileId.flatMapLatest { profileId ->
            favoritesDao.observeFavorites(profileId)
        }.map { list -> list.map { FavoriteItem(it.id, it.type, it.name, it.cover, it.categoryId) } }
    }

    override suspend fun isFavorite(id: Int, type: String): Boolean {
        return favoritesDao.isFavorite(id, type, profileManager.currentProfileId())
    }

    override suspend fun addFavorite(favorite: FavoriteItem) {
        val entity = FavoriteEntity(
            id = favorite.id,
            type = favorite.type,
            name = favorite.name,
            cover = favorite.cover,
            categoryId = favorite.categoryId,
            addedAt = System.currentTimeMillis(),
            profileId = profileManager.currentProfileId()
        )
        favoritesDao.addFavorite(entity)
    }

    override suspend fun removeFavorite(id: Int, type: String) {
        favoritesDao.removeFavorite(id, type, profileManager.currentProfileId())
    }

    override suspend fun searchUnified(query: String): SearchResult {
        val matchQuery = buildFtsMatchQuery(query)
        if (matchQuery.isBlank()) return SearchResult()

        val liveEntities = favoritesDao.searchLiveStreams(matchQuery)
        val vodEntities = favoritesDao.searchVodStreams(matchQuery)
        val seriesEntities = favoritesDao.searchSeriesStreams(matchQuery)

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

    // Transforme la saisie libre en requête FTS4 : chaque mot devient un
    // préfixe recherché ("bat man" -> "bat"* "man"*, ET implicite), pour
    // matcher "Batman Begins" dès les premières lettres de chaque mot.
    // Les guillemets internes sont échappés pour éviter toute erreur de
    // syntaxe MATCH (FTS4 n'exécute pas de code, mais une requête malformée
    // lèverait une SQLiteException).
    private fun buildFtsMatchQuery(raw: String): String {
        return raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                "\"${token.replace("\"", "\"\"")}\"*"
            }
    }
}
