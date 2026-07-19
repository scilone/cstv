package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.ApproximateTitleMatcher
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject

class GetTrendingInCatalogUseCase @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {

    suspend operator fun invoke(): List<TrendingCatalogItem> = withContext(Dispatchers.Default) {
        val trendingList = trendingRepository.getTrending()
        if (trendingList.isEmpty()) {
            return@withContext emptyList()
        }

        // Fetch hidden category IDs for movie & series
        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)

        // Fetch all active local catalog items
        val allMovies = try {
            vodRepository.getVodStreams("all", false)
                .filter { it.categoryId !in hiddenMovies }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }

        val allSeries = try {
            seriesRepository.getSeriesStreams("all", false)
                .filter { it.categoryId !in hiddenSeries }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }

        val result = mutableListOf<TrendingCatalogItem>()
        val seenMatchedIds = mutableSetOf<Int>() // Prevent matching the same movie or series twice

        for (trending in trendingList) {
            if (trending.isMovie) {
                // Find best matching movie in catalog
                var bestMovie: com.cstv.app.domain.model.VodStream? = null
                var bestScore = 0.0
                
                for (movie in allMovies) {
                    if (movie.streamId in seenMatchedIds) continue
                    val score = ApproximateTitleMatcher.computeSimilarity(trending.title, movie.name ?: "")
                    if (score >= 0.8 && score > bestScore) {
                        bestScore = score
                        bestMovie = movie
                    }
                }
                
                if (bestMovie != null) {
                    seenMatchedIds.add(bestMovie.streamId)
                    result.add(TrendingCatalogItem(trendingTitle = trending, matchedMovie = bestMovie))
                }
            } else {
                // Find best matching series in catalog
                var bestSeries: com.cstv.app.domain.model.SeriesStream? = null
                var bestScore = 0.0
                
                for (series in allSeries) {
                    if (series.seriesId in seenMatchedIds) continue
                    val score = ApproximateTitleMatcher.computeSimilarity(trending.title, series.name ?: "")
                    if (score >= 0.8 && score > bestScore) {
                        bestScore = score
                        bestSeries = series
                    }
                }
                
                if (bestSeries != null) {
                    seenMatchedIds.add(bestSeries.seriesId)
                    result.add(TrendingCatalogItem(trendingTitle = trending, matchedSeries = bestSeries))
                }
            }

            if (result.size >= 10) break
        }

        result
    }

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
