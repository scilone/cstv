package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveEpgUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(streamId: Int, forceRefresh: Boolean = false): LiveEpgProgram? {
        return repository.getLiveEpg(streamId, forceRefresh)
    }
}