package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.repository.SeriesRepository
import javax.inject.Inject

/** Séries associées (mêmes genres) à afficher en bas des détails d'une série. */
class GetRelatedSeriesUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(currentSeriesId: Int, genre: String?, limit: Int = 10): List<SeriesStream> =
        repository.getRelatedSeries(currentSeriesId, genre, limit)
}
