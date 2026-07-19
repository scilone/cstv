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
        // 1. Check persistent global device cache
        val cachedGlobal = trendingRepository.getCachedMatchedTrendsGlobal()
        val matchedList = if (cachedGlobal != null) {
            cachedGlobal
        } else {
            // 2. Fetch fresh trends from API and match them if cache is expired/null
            val trendingList = trendingRepository.getTrending()
            if (trendingList.isEmpty()) {
                return@withContext emptyList()
            }

            // Fetch ALL local catalog items (without filtering hidden categories yet)
            val allMovies = try {
                vodRepository.getVodStreams("all", false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val allSeries = try {
                seriesRepository.getSeriesStreams("all", false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val fullMatchedResult = mutableListOf<TrendingCatalogItem>()
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
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedMovie = bestMovie))
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
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedSeries = bestSeries))
                    }
                }
            }

            // Save full matched results globally before returning
            if (fullMatchedResult.isNotEmpty()) {
                trendingRepository.saveMatchedTrendsGlobal(fullMatchedResult)
            }

            fullMatchedResult
        }

        if (matchedList.isEmpty()) {
            return@withContext emptyList()
        }

        // 3. Filter matched items on the fly by hidden categories of the ACTIVE profile
        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)

        val filteredResult = matchedList.filter { item ->
            if (item.matchedMovie != null) {
                item.matchedMovie.categoryId !in hiddenMovies
            } else if (item.matchedSeries != null) {
                item.matchedSeries.categoryId !in hiddenSeries
            } else {
                true
            }
        }

        // 4. Cap final filtered list at 10 items
        filteredResult.take(10)
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
