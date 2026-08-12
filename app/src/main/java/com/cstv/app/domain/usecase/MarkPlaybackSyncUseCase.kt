package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import javax.inject.Inject

/** Explicit player-event trigger; it intentionally does not run on position ticks. */
class MarkPlaybackSyncUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val cloudSync: CloudSyncManager
) {
    suspend operator fun invoke() {
        cloudSync.markDirty(profiles.currentProfileId(), SyncNamespace.PLAYBACK)
    }
}
