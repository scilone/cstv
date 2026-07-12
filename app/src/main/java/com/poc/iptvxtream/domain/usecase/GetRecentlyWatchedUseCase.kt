package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetRecentlyWatchedUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(): List<LiveStream> {
        return repository.getRecentlyWatched()
    }
}