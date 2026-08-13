package com.cstv.app.data.repository

import androidx.room.withTransaction
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.entity.MediaRatingEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.MediaRating
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import com.cstv.app.domain.repository.MediaRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace

@Singleton
class MediaRatingRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val mediaRatingDao: MediaRatingDao,
    private val favoritesDao: FavoritesDao,
    private val vodDao: VodDao,
    private val profileManager: ProfileManager,
    private val mediaRefDao: MediaRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val sync: CloudSyncManager? = null
) : MediaRatingRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRating(mediaId: Int, mediaType: RatedMediaType): Flow<MediaRatingValue?> =
        profileManager.activeProfileId.flatMapLatest { profileId ->
            mediaRatingDao.observeRating(profileId, accountKeyProvider.current(), mediaType.storageValue, mediaId)
        }.map { value -> value?.toRatingValue() }

    override suspend fun getAllRatings(): List<MediaRating> =
        mediaRatingDao.getAllForProfile(profileManager.currentProfileId(), accountKeyProvider.current()).mapNotNull { row ->
            val type = RatedMediaType.entries.firstOrNull { it.storageValue == row.kind }
            val value = row.value.toRatingValue()
            if (type != null && value != null) MediaRating(row.providerId, type, value) else null
        }

    override suspend fun setRating(mediaId: Int, mediaType: RatedMediaType, value: MediaRatingValue?, seriesEpisodeIds: Set<Int>) {
        val profileId = profileManager.currentProfileId()
        val accountKey = accountKeyProvider.current()
        database.withTransaction {
            if (value == null) {
                mediaRatingDao.delete(profileId, accountKey, mediaType.storageValue, mediaId)
                return@withTransaction
            }
            val mediaUid = mediaRefDao.resolve(accountKey, mediaType.storageValue, mediaId)
            mediaRatingDao.upsert(MediaRatingEntity(profileId, mediaUid, value.storageValue))
            if (value == MediaRatingValue.DISLIKE) {
                favoritesDao.removeFavorite(profileId, accountKey, mediaType.storageValue, mediaId)
                if (mediaType == RatedMediaType.MOVIE) {
                    vodDao.deletePlaybackPosition(profileId, accountKey, mediaType.storageValue, mediaId)
                } else {
                    vodDao.deletePlaybackPositionsBySeriesId(profileId, accountKey, mediaId)
                    if (seriesEpisodeIds.isNotEmpty()) vodDao.deletePlaybackPositionsByStreamIds(profileId, accountKey, seriesEpisodeIds)
                }
            }
        }
        mediaRefDao.purgeUnreferenced()
        sync?.markDirty(profileId, SyncNamespace.RATINGS)
        if (value == MediaRatingValue.DISLIKE) {
            sync?.markDirty(profileId, SyncNamespace.FAVORITES)
            sync?.markDirty(profileId, SyncNamespace.PLAYBACK)
        }
    }

    private fun Int.toRatingValue(): MediaRatingValue? = MediaRatingValue.entries.firstOrNull { it.storageValue == this }
}
