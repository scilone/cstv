package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.repository.ViewingHistoryRepository
import javax.inject.Inject

class RemoveFromContinueWatchingUseCase @Inject constructor(
    private val repository: ViewingHistoryRepository,
    private val getRecommendationsUseCase: GetRecommendationsUseCase
) {
    suspend operator fun invoke(position: PlaybackPosition) {
        repository.removeFromContinueWatching(position)
        getRecommendationsUseCase.invalidateCache()
    }
}
