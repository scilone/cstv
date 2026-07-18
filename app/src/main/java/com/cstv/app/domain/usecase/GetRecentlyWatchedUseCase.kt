package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetRecentlyWatchedUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(): List<LiveStream> {
        return repository.getRecentlyWatched()
    }
}