package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVodStreamsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    operator fun invoke(categoryId: String): Flow<List<VodStream>> =
        repository.observeVodStreams(categoryId)

    suspend fun once(categoryId: String): List<VodStream> =
        repository.getCachedVodStreams(categoryId)
}
