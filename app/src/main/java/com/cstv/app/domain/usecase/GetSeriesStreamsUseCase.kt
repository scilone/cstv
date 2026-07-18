package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesStreamsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<SeriesStream> {
        return repository.getSeriesStreams(categoryId, forceRefresh)
    }
}
