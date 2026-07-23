package com.cstv.app.data.repository

import androidx.room.withTransaction
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.entity.MediaRatingEntity
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

@Singleton
class MediaRatingRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val mediaRatingDao: MediaRatingDao,
    private val favoritesDao: FavoritesDao,
    private val vodDao: VodDao,
    private val profileManager: ProfileManager
) : MediaRatingRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRating(mediaId: Int, mediaType: RatedMediaType): Flow<MediaRatingValue?> =
        profileManager.activeProfileId.flatMapLatest { profileId ->
            mediaRatingDao.observeRating(profileId, mediaType.storageValue, mediaId)
        }.map { entity -> entity?.value?.toRatingValue() }

    override suspend fun getAllRatings(): List<MediaRating> =
        mediaRatingDao.getAllForProfile(profileManager.currentProfileId()).mapNotNull { entity ->
            val type = RatedMediaType.entries.firstOrNull { it.storageValue == entity.mediaType }
            val value = entity.value.toRatingValue()
            if (type != null && value != null) MediaRating(entity.mediaId, type, value) else null
        }

    override suspend fun setRating(mediaId: Int, mediaType: RatedMediaType, value: MediaRatingValue?, seriesEpisodeIds: Set<Int>) {
        val profileId = profileManager.currentProfileId()
        database.withTransaction {
            if (value == null) {
                mediaRatingDao.delete(profileId, mediaType.storageValue, mediaId)
                return@withTransaction
            }
            mediaRatingDao.upsert(MediaRatingEntity(profileId, mediaType.storageValue, mediaId, value.storageValue))
            if (value == MediaRatingValue.DISLIKE) {
                favoritesDao.removeFavorite(mediaId, mediaType.storageValue, profileId)
                if (mediaType == RatedMediaType.MOVIE) {
                    vodDao.deletePlaybackPosition(mediaId, profileId)
                } else {
                    vodDao.deletePlaybackPositionsBySeriesId(mediaId, profileId)
                    if (seriesEpisodeIds.isNotEmpty()) vodDao.deletePlaybackPositionsByStreamIds(seriesEpisodeIds, profileId)
                }
            }
        }
    }

    private fun Int.toRatingValue(): MediaRatingValue? = MediaRatingValue.entries.firstOrNull { it.storageValue == this }
}
