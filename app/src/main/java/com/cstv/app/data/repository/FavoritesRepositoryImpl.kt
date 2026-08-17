package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val mediaRefDao: MediaRefDao,
    private val profileManager: ProfileManager,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val sync: CloudSyncManager? = null
) : FavoritesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<FavoriteItem>> {
        return profileManager.activeProfileId.flatMapLatest { profileId ->
            favoritesDao.observeFavorites(profileId, accountKeyProvider.current())
        }.map { list -> list.map { FavoriteItem(it.providerId, it.kind, it.name, it.cover, it.categoryId, it.languageTag, it.qualityTag, it.versionLabel) } }
    }

    override suspend fun isFavorite(id: Int, type: String): Boolean {
        return favoritesDao.isFavorite(profileManager.currentProfileId(), accountKeyProvider.current(), type, id)
    }

    override suspend fun addFavorite(favorite: FavoriteItem) {
        val profileId = profileManager.currentProfileId()
        val accountKey = accountKeyProvider.current()
        val mediaUid = mediaRefDao.resolve(accountKey, favorite.type, favorite.id)
        favoritesDao.addFavorite(FavoriteEntity(profileId = profileId, mediaUid = mediaUid, addedAt = System.currentTimeMillis()))
        sync?.markDirty(profileId, SyncNamespace.FAVORITES)
    }

    override suspend fun removeFavorite(id: Int, type: String) {
        val profileId = profileManager.currentProfileId()
        val accountKey = accountKeyProvider.current()
        favoritesDao.removeFavorite(profileId, accountKey, type, id)
        mediaRefDao.purgeUnreferenced()
        sync?.markDirty(profileId, SyncNamespace.FAVORITES)
    }

    override suspend fun searchUnified(query: String): SearchResult {
        val searchQuery = LocalSearchQuery.parse(query)
        if (searchQuery.isEmpty) return SearchResult()

        val liveEntities = favoritesDao.searchLiveStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }
        val vodEntities = favoritesDao.searchVodStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }
        val seriesEntities = favoritesDao.searchSeriesStreams(searchQuery.likePattern)
            .filter { searchQuery.matches(it.searchText) }

        return SearchResult(
            liveResults = liveEntities.map {
                LiveStream(it.streamId, it.name, it.streamIcon, it.epgChannelId, it.num, it.categoryId)
            },
            vodResults = vodEntities.map {
                // Bug corrigé : constructeur partiel omettant linkKey/tags T21 — la recherche
                // ne montrait jamais les badges de version, et un item ouvert depuis la recherche
                // n'aurait rejoint aucune version tant que la fiche ne le rechargeait pas.
                VodStream(
                    it.streamId, it.name, it.streamIcon, it.rating, it.added, it.categoryId,
                    it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText,
                    it.cleanTitle, it.linkKey, it.languageTag, it.languageRaw, it.qualityTag, it.qualityRaw, it.versionLabel
                )
            },
            seriesResults = seriesEntities.map {
                SeriesStream(
                    it.seriesId, it.name, it.cover, it.rating, it.added, it.categoryId,
                    it.genre, it.releaseYear?.takeIf { y -> y > 0 }, it.actors, it.director, it.searchText,
                    it.cleanTitle, it.linkKey, it.languageTag, it.languageRaw, it.qualityTag, it.qualityRaw, it.versionLabel
                )
            }
        )
    }

}
