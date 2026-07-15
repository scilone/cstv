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

            // Rafraîchit aussi le casting (acteurs/réalisateur/genre) par lots
            // successifs, attendus (contrairement au trickle paresseux déclenché
            // par une simple consultation de liste) — sinon un sync programmé ne
            // ferait progresser l'enrichissement d'un seul lot de 50 par appel.
            vodRepository.enrichPendingMovies()
            seriesRepository.enrichPendingSeries()

            SyncCacheResult.SUCCESS
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncCacheResult.FAILED
        }
    }
}
