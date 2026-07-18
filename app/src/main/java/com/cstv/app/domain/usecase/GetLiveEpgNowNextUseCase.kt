package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveEpgNowNext
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveEpgNowNextUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(streamId: Int, forceRefresh: Boolean = false): LiveEpgNowNext {
        return repository.getLiveEpgNowNext(streamId, forceRefresh)
    }
}
