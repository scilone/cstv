package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.repository.ViewingHistoryRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ViewingHistoryRepositoryImpl @Inject constructor(
    private val vodDao: VodDao,
    private val liveTvDao: LiveTvDao,
    private val profileManager: ProfileManager,
    private val mediaRefDao: MediaRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val sync: CloudSyncManager? = null
) : ViewingHistoryRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRecentlyWatched(limit: Int): Flow<List<LiveStream>> =
        profileManager.activeProfileId.flatMapLatest { profileId ->
            liveTvDao.observeRecentlyWatched(profileId, accountKeyProvider.current(), limit)
        }.map { rows ->
            rows.map {
                LiveStream(
                    streamId = it.providerId,
                    name = it.name,
                    streamIcon = it.streamIcon,
                    epgChannelId = null,
                    num = it.num ?: 0,
                    categoryId = it.categoryId ?: "0"
                )
            }
        }

    override suspend fun removeFromContinueWatching(position: PlaybackPosition) {
        // streamId + kind + active profile is deliberately the entire deletion scope.
        val kind = position.type ?: return
        val profileId = profileManager.currentProfileId()
        vodDao.deletePlaybackPosition(profileId, accountKeyProvider.current(), kind, position.streamId)
        mediaRefDao.purgeUnreferenced()
        sync?.markDirty(profileId, SyncNamespace.PLAYBACK)
    }

    override suspend fun removeRecentlyWatched(streamId: Int) {
        val profileId = profileManager.currentProfileId()
        liveTvDao.deleteRecentlyWatched(profileId, accountKeyProvider.current(), streamId)
        mediaRefDao.purgeUnreferenced()
        sync?.markDirty(profileId, SyncNamespace.RECENTLY_WATCHED_LIVE)
    }
}
