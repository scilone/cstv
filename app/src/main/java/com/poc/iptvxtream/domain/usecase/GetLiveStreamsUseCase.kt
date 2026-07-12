package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveStreamsUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<LiveStream> {
        return repository.getLiveStreams(categoryId, forceRefresh)
    }
}
