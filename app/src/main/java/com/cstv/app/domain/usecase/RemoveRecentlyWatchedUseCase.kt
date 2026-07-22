package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.ViewingHistoryRepository
import javax.inject.Inject

class RemoveRecentlyWatchedUseCase @Inject constructor(
    private val repository: ViewingHistoryRepository
) {
    suspend operator fun invoke(streamId: Int) = repository.removeRecentlyWatched(streamId)
}
