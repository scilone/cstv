package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.ViewingHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentlyWatchedUseCase @Inject constructor(
    private val repository: ViewingHistoryRepository
) {
    operator fun invoke(): Flow<List<LiveStream>> = repository.observeRecentlyWatched()
}
