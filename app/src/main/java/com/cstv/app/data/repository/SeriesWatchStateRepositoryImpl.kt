package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.SeriesWatchStateEntity
import com.cstv.app.domain.model.EpisodeRef
import com.cstv.app.domain.model.SeriesWatchState
import com.cstv.app.domain.repository.SeriesWatchStateRepository
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

private const val NEVER_NOTIFIED = -1

@Singleton
class SeriesWatchStateRepositoryImpl @Inject constructor(
    private val dao: SeriesWatchStateDao,
    private val favoritesDao: FavoritesDao,
    private val vodDao: VodDao,
    private val timeProvider: TimeProvider
) : SeriesWatchStateRepository {

    override suspend fun eligibleSeriesIds(profileId: Int): Set<Int> {
        val favorites = favoritesDao.getFavoritesByType("series", profileId).map { it.id }
        val watched = vodDao.getWatchedSeriesIds(profileId)
        return (favorites + watched).toSet()
    }

    override suspend fun getStates(profileId: Int): Map<Int, SeriesWatchState> =
        dao.getAllForProfile(profileId).associate { it.seriesId to it.toDomain() }

    override suspend fun upsert(state: SeriesWatchState) {
        dao.upsert(state.toEntity(timeProvider.nowMillis()))
    }

    private fun SeriesWatchStateEntity.toDomain() = SeriesWatchState(
        profileId = profileId,
        seriesId = seriesId,
        lastKnown = EpisodeRef(lastKnownSeason, lastKnownEpisode),
        lastNotified = if (lastNotifiedSeason == NEVER_NOTIFIED && lastNotifiedEpisode == NEVER_NOTIFIED) {
            null
        } else {
            EpisodeRef(lastNotifiedSeason, lastNotifiedEpisode)
        }
    )

    private fun SeriesWatchState.toEntity(now: Long) = SeriesWatchStateEntity(
        profileId = profileId,
        seriesId = seriesId,
        lastKnownSeason = lastKnown.season,
        lastKnownEpisode = lastKnown.episode,
        lastNotifiedSeason = lastNotified?.season ?: NEVER_NOTIFIED,
        lastNotifiedEpisode = lastNotified?.episode ?: NEVER_NOTIFIED,
        updatedAt = now
    )
}
