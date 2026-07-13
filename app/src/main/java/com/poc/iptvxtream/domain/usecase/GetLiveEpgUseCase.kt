package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveEpgUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(streamId: Int, forceRefresh: Boolean = false): LiveEpgProgram? {
        return repository.getLiveEpg(streamId, forceRefresh)
    }
}