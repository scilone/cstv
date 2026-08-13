package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.VodRepository
import javax.inject.Inject

/** T20: title/cover/etc. are no longer stored with the resume position -- they are resolved from
 *  the catalogue at display time. [kind] is [com.cstv.app.domain.model.MediaKind.storageValue]
 *  ("movie" or "episode"); shared by both `VodViewModel` and `SeriesViewModel`. */
class SavePlaybackPositionUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(kind: String, providerId: Int, positionMs: Long, durationMs: Long) {
        repository.savePlaybackPosition(kind, providerId, positionMs, durationMs)
    }
}
