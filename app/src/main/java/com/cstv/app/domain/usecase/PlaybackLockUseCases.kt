package com.cstv.app.domain.usecase

import com.cstv.app.data.playback.PlaybackLockManager
import com.cstv.app.domain.model.PlaybackLockResult
import javax.inject.Inject

interface PlaybackLockRequester {
    suspend fun request(contentId: String?, takeover: Boolean = false): PlaybackLockRequestResult
}

interface PlaybackLockReleaser { fun release() }

class RequestPlaybackLockUseCase @Inject constructor(
    private val manager: PlaybackLockManager,
    private val isContentDownloadedUseCase: IsContentDownloadedUseCase,
) : PlaybackLockRequester {
    override suspend fun request(contentId: String?, takeover: Boolean): PlaybackLockRequestResult {
        if (isContentDownloadedUseCase(contentId)) return PlaybackLockRequestResult.NotRequired
        return when (val result = manager.acquire(takeover)) {
            is PlaybackLockResult.Acquired, PlaybackLockResult.Unavailable -> PlaybackLockRequestResult.Allowed
            is PlaybackLockResult.Held -> PlaybackLockRequestResult.Held(result.holder)
            is PlaybackLockResult.Revoked -> PlaybackLockRequestResult.Allowed
        }
    }

    suspend operator fun invoke(contentId: String?, takeover: Boolean = false) = request(contentId, takeover)
}

sealed interface PlaybackLockRequestResult {
    data object NotRequired : PlaybackLockRequestResult
    data object Allowed : PlaybackLockRequestResult
    data class Held(val holder: com.cstv.app.domain.model.PlaybackLockHolder) : PlaybackLockRequestResult
}

class ReleasePlaybackLockUseCase @Inject constructor(private val manager: PlaybackLockManager) : PlaybackLockReleaser {
    override fun release() = manager.release()
    operator fun invoke() = release()
}
