package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

enum class SyncCacheResult {
    SUCCESS,
    SKIPPED_NO_CREDENTIALS,
    FAILED
}

class SyncCacheUseCase @Inject constructor(
    private val credentialsManager: CredentialsManager,
    private val liveTvRepository: LiveTvRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository
) {
    suspend operator fun invoke(): SyncCacheResult {
        if (credentialsManager.getCredentials() == null) {
            return SyncCacheResult.SKIPPED_NO_CREDENTIALS
        }

        return try {
            liveTvRepository.getLiveCategories(forceRefresh = true)
            liveTvRepository.getLiveStreams(categoryId = "all", forceRefresh = true)

            vodRepository.getVodCategories(forceRefresh = true)
            vodRepository.getVodStreams(categoryId = "all", forceRefresh = true)

            seriesRepository.getSeriesCategories(forceRefresh = true)
            seriesRepository.getSeriesStreams(categoryId = "all", forceRefresh = true)

            SyncCacheResult.SUCCESS
        } catch (e: Exception) {
            SyncCacheResult.FAILED
        }
    }
}
