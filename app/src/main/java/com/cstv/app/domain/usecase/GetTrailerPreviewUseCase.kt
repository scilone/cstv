package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.repository.TrailerRepository
import javax.inject.Inject

class GetTrailerPreviewUseCase @Inject constructor(
    private val trailerRepository: TrailerRepository
) {
    suspend operator fun invoke(media: TrailerMedia): TrailerPreview? =
        trailerRepository.getTrailerPreview(media)
}
