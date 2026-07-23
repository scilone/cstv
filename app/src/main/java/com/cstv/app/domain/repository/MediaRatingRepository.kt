package com.cstv.app.domain.repository

import com.cstv.app.domain.model.MediaRating
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import kotlinx.coroutines.flow.Flow

interface MediaRatingRepository {
    fun observeRating(mediaId: Int, mediaType: RatedMediaType): Flow<MediaRatingValue?>
    suspend fun getAllRatings(): List<MediaRating>
    suspend fun setRating(
        mediaId: Int,
        mediaType: RatedMediaType,
        value: MediaRatingValue?,
        seriesEpisodeIds: Set<Int> = emptySet()
    )
}
