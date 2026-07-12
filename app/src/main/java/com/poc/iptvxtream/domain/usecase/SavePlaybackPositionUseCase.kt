package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

class SavePlaybackPositionUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(
        streamId: Int,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        coverUrl: String? = null,
        type: String? = null,
        containerExtension: String? = null,
        seriesId: Int? = null,
        episodeNum: Int? = null,
        seasonNum: Int? = null,
        plot: String? = null,
        duration: String? = null,
        releaseDate: String? = null
    ) {
        repository.savePlaybackPosition(
            streamId = streamId,
            positionMs = positionMs,
            durationMs = durationMs,
            title = title,
            coverUrl = coverUrl,
            type = type,
            containerExtension = containerExtension,
            seriesId = seriesId,
            episodeNum = episodeNum,
            seasonNum = seasonNum,
            plot = plot,
            duration = duration,
            releaseDate = releaseDate
        )
    }
}
