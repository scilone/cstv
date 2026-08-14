package com.cstv.app.domain.usecase

import com.cstv.app.data.playback.PlaybackLockManager
import javax.inject.Inject

class ResetPlaybackLockUseCase @Inject constructor(private val manager: PlaybackLockManager) {
    operator fun invoke() = manager.reset()
}
