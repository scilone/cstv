package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSeriesStreamsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(categoryId: String): Flow<List<SeriesStream>> =
        repository.observeSeriesStreams(categoryId)

    suspend fun once(categoryId: String): List<SeriesStream> =
        repository.getCachedSeriesStreams(categoryId)
}
