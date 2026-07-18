package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.VodRepository
import javax.inject.Inject

class GetVodStreamsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<VodStream> {
        return repository.getVodStreams(categoryId, forceRefresh)
    }
}
