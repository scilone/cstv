package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.repository.VodRepository
import javax.inject.Inject

class GetVodDetailsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(streamId: Int): VodDetails {
        return repository.getVodDetails(streamId)
    }
}
