package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.LiveTvRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLiveStreamsUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    operator fun invoke(categoryId: String): Flow<List<LiveStream>> =
        repository.observeLiveStreams(categoryId)

    suspend fun once(categoryId: String): List<LiveStream> =
        repository.getCachedLiveStreams(categoryId)
}
