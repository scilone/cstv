package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.repository.TrailerRepository
import javax.inject.Inject

class InvalidateTrailerPreviewUseCase @Inject constructor(private val repository: TrailerRepository) {
    suspend operator fun invoke(media: TrailerMedia) = repository.invalidate(media)
}
