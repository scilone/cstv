package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveStreamsUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<LiveStream> {
        return repository.getLiveStreams(categoryId, forceRefresh)
    }
}
