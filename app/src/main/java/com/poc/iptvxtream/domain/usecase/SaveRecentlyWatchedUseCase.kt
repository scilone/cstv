package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class SaveRecentlyWatchedUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(stream: LiveStream) {
        repository.saveRecentlyWatched(stream)
    }
}