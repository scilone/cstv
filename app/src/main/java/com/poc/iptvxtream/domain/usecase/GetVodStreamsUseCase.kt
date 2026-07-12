package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

class GetVodStreamsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<VodStream> {
        return repository.getVodStreams(categoryId, forceRefresh)
    }
}
