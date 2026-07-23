package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import com.cstv.app.domain.repository.MediaRatingRepository
import javax.inject.Inject

class SetMediaRatingUseCase @Inject constructor(
    private val repository: MediaRatingRepository,
    private val recommendations: GetRecommendationsUseCase
) {
    suspend operator fun invoke(mediaId: Int, mediaType: RatedMediaType, value: MediaRatingValue?, seriesEpisodeIds: Set<Int> = emptySet()) {
        repository.setRating(mediaId, mediaType, value, seriesEpisodeIds)
        recommendations.invalidateCache()
    }
}
