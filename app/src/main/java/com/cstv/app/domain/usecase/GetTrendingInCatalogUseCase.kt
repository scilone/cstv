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
        com.cstv.app.di.IptvLog.d("TMDB", "🚀 GetTrendingInCatalogUseCase triggered.")

        // 1. Check persistent global device cache
        val cachedGlobal = trendingRepository.getCachedMatchedTrendsGlobal()
        val matchedList = if (cachedGlobal != null) {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global matched cache HIT. Loaded ${cachedGlobal.size} items.")
            cachedGlobal
        } else {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global matched cache MISS. Fetching raw trends and matching...")
            
            // 2. Fetch fresh trends from API and match them if cache is expired/null
            val trendingList = trendingRepository.getTrending()
            if (trendingList.isEmpty()) {
                com.cstv.app.di.IptvLog.w("TMDB", "🌐 TMDB API returned empty list of trends. Reverting to hero card.")
                return@withContext emptyList()
            }

            // Fetch ALL local catalog items (without filtering hidden categories yet)
            val allMovies = try {
                val list = vodRepository.getVodStreams("all", false)
                com.cstv.app.di.IptvLog.d("TMDB", "📦 Loaded ${list.size} movies from local database cache.")
                list
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.cstv.app.di.IptvLog.e("TMDB", "📦 Failed to load movies from local database", e)
                emptyList()
            }

            val allSeries = try {
                val list = seriesRepository.getSeriesStreams("all", false)
                com.cstv.app.di.IptvLog.d("TMDB", "📦 Loaded ${list.size} series from local database cache.")
                list
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.cstv.app.di.IptvLog.e("TMDB", "📦 Failed to load series from local database", e)
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
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match movie: '${trending.title}' ↔ '${bestMovie.name}' (score: $bestScore)")
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedMovie = bestMovie))
                    } else {
                        com.cstv.app.di.IptvLog.d("TMDB", "❔ No match found in catalog for trending movie: '${trending.title}'")
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
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match series: '${trending.title}' ↔ '${bestSeries.name}' (score: $bestScore)")
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedSeries = bestSeries))
                    } else {
                        com.cstv.app.di.IptvLog.d("TMDB", "❔ No match found in catalog for trending series: '${trending.title}'")
                    }
                }
            }

            // Save full matched results globally before returning
            if (fullMatchedResult.isNotEmpty()) {
                trendingRepository.saveMatchedTrendsGlobal(fullMatchedResult)
            } else {
                com.cstv.app.di.IptvLog.w("TMDB", "⚠️ No trends matches found in the entire local database catalog!")
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
                val isHidden = item.matchedMovie.categoryId in hiddenMovies
                if (isHidden) {
                    com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.matchedMovie.name}' filtered out because its category '${item.matchedMovie.categoryId}' is hidden.")
                }
                !isHidden
            } else if (item.matchedSeries != null) {
                val isHidden = item.matchedSeries.categoryId in hiddenSeries
                if (isHidden) {
                    com.cstv.app.di.IptvLog.d("TMDB", "🚫 Series '${item.matchedSeries.name}' filtered out because its category '${item.matchedSeries.categoryId}' is hidden.")
                }
                !isHidden
            } else {
                true
            }
        }

        // 4. Cap final filtered list at 10 items
        val finalResult = filteredResult.take(10)
        com.cstv.app.di.IptvLog.d("TMDB", "🏁 GetTrendingInCatalogUseCase returning ${finalResult.size} items to the UI.")
        finalResult
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
