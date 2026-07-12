package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesStreamsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(categoryId: String, forceRefresh: Boolean = false): List<SeriesStream> {
        return repository.getSeriesStreams(categoryId, forceRefresh)
    }
}
