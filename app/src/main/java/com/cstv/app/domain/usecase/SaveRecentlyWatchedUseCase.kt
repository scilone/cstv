package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject

class SaveRecentlyWatchedUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(stream: LiveStream) {
        repository.saveRecentlyWatched(stream)
    }
}