package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesDetailsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(seriesId: Int): SeriesDetails {
        return repository.getSeriesDetails(seriesId)
    }
}
