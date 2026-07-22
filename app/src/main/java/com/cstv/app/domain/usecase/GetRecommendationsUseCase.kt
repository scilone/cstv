package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.RecommendationEngine
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.data.local.storage.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetRecommendationsUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val profileManager: ProfileManager
) {
    data class RecommendationResult(
        val movies: List<VodStream>,
        val series: List<SeriesStream>
    )

    private val mutex = Mutex()
    private var cachedResult: RecommendationResult? = null
    private var cachedProfileId: Int = -1
    private var cacheTimestamp: Long = 0L
    private val TTL_MILLIS = 24L * 60 * 60 * 1000L

    suspend operator fun invoke(
        currentTimeMs: Long = System.currentTimeMillis()
    ): RecommendationResult = withContext(Dispatchers.Default) {
        val currentProfileId = profileManager.currentProfileId()

        mutex.withLock {
            // Serve cache if valid
            if (cachedResult != null && 
                cachedProfileId == currentProfileId && 
                (currentTimeMs - cacheTimestamp) < TTL_MILLIS) {
                com.cstv.app.di.IptvLog.d("RECO", "Serving recommendations from cache for profile $currentProfileId")
                return@withLock cachedResult!!
            }

            com.cstv.app.di.IptvLog.d("RECO", "Calculating recommendations for profile $currentProfileId...")
            
            // 1. Get user history
            val allHistory = try {
                vodRepository.getAllPlaybackPositions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val movieHistoryIds = allHistory.filter { it.type == "movie" }.map { it.streamId.toString() }.toSet()
            val seriesHistoryIds = allHistory.filter { it.type == "series" && it.seriesId != null }.map { it.seriesId.toString() }.toSet()

            // Calculate distinct watched items count
            val totalWatchedCount = movieHistoryIds.size + seriesHistoryIds.size

            // Cold start protection: require at least 3 distinct items
            if (totalWatchedCount < 3) {
                com.cstv.app.di.IptvLog.d("RECO", "Cold start: Not enough history ($totalWatchedCount < 3) for profile $currentProfileId. Returning empty.")
                val emptyResult = RecommendationResult(emptyList(), emptyList())
                updateCache(currentProfileId, currentTimeMs, emptyResult)
                return@withLock emptyResult
            }

            // 2. Fetch full catalog
            val allMovies = try {
                vodRepository.getVodStreams("all", forceRefresh = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val allSeries = try {
                seriesRepository.getSeriesStreams("all", forceRefresh = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            // 3. Exclude hidden categories
            val hiddenMovieCategories = getHiddenCategories(CategoryType.VOD)
            val hiddenSeriesCategories = getHiddenCategories(CategoryType.SERIES)

            val allowedMovies = allMovies.filter { it.categoryId !in hiddenMovieCategories }
            val allowedSeries = allSeries.filter { it.categoryId !in hiddenSeriesCategories }

            // 4. Map history IDs to actual streams to build the profile taste
            val watchedItems = mutableListOf<RecommendationEngine.RecommendableItem>()
            
            for (movie in allMovies) {
                if (movieHistoryIds.contains(movie.streamId.toString())) {
                    watchedItems.add(RecommendationEngine.RecommendableVod(movie))
                }
            }
            for (series in allSeries) {
                if (seriesHistoryIds.contains(series.seriesId.toString())) {
                    watchedItems.add(RecommendationEngine.RecommendableSeries(series))
                }
            }

            // 5. Calculate taste profile
            val profileTaste = RecommendationEngine.buildProfileTaste(watchedItems)
            com.cstv.app.di.IptvLog.d("RECO", "Profile Taste built. Top Genres: ${profileTaste.genreWeights.entries.sortedByDescending { it.value }.take(3)}")

            // 6. Score and get top 100 for each type
            val recommendableMovies = allowedMovies.map { RecommendationEngine.RecommendableVod(it) }
            val recommendableSeries = allowedSeries.map { RecommendationEngine.RecommendableSeries(it) }

            val recommendedMovies = RecommendationEngine.getTopRecommendations(
                candidates = recommendableMovies,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = movieHistoryIds
            ).map { it.stream }

            val recommendedSeries = RecommendationEngine.getTopRecommendations(
                candidates = recommendableSeries,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = seriesHistoryIds
            ).map { it.series }

            val result = RecommendationResult(recommendedMovies, recommendedSeries)
            
            // 7. Update cache
            updateCache(currentProfileId, currentTimeMs, result)
            
            com.cstv.app.di.IptvLog.d("RECO", "Recommendations generated: ${result.movies.size} movies, ${result.series.size} series.")
            result
        }
    }

    private fun updateCache(profileId: Int, timeMs: Long, result: RecommendationResult) {
        cachedProfileId = profileId
        cacheTimestamp = timeMs
        cachedResult = result
    }

    fun invalidateCache() {
        cachedProfileId = -1
        cachedResult = null
    }

    // Visible for existing tests.
    internal fun clearCache() = invalidateCache()

    private suspend fun getHiddenCategories(type: CategoryType): Set<String> {
        return try {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptySet()
        }
    }
}
