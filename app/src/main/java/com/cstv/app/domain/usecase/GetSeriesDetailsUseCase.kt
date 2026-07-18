package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesDetailsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(seriesId: Int): SeriesDetails {
        return repository.getSeriesDetails(seriesId)
    }
}
