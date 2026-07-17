package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.LiveEpgNowNext
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveEpgNowNextUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(streamId: Int, forceRefresh: Boolean = false): LiveEpgNowNext {
        return repository.getLiveEpgNowNext(streamId, forceRefresh)
    }
}
