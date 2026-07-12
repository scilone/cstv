package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

class GetVodDetailsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(streamId: Int): VodDetails {
        return repository.getVodDetails(streamId)
    }
}
