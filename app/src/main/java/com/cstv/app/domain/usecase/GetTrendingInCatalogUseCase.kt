package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.ApproximateTitleMatcher
import com.cstv.app.domain.model.TitleNormalizer
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.data.local.storage.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject

class GetTrendingInCatalogUseCase @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val settingsManager: SettingsManager
) {

    suspend operator fun invoke(): List<TrendingCatalogItem> = withContext(Dispatchers.Default) {
        com.cstv.app.di.IptvLog.d("TMDB", "🚀 GetTrendingInCatalogUseCase triggered.")

        // Invalidate cache if catalog was resynchronized after cache generation (Bug B-3)
        val lastVodSync = settingsManager.getVodAllStreamsSyncedAt()
        val lastSeriesSync = settingsManager.getSeriesAllStreamsSyncedAt()
        val lastCatalogSyncTime = maxOf(lastVodSync, lastSeriesSync)

        // 1. Check persistent global device cache
        val cachedGlobal = trendingRepository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime)
        val matchedList = if (cachedGlobal != null) {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global matched cache HIT. Loaded ${cachedGlobal.size} items: " +
                cachedGlobal.joinToString { "'${it.trendingTitle.title}' ↔ '${it.matchedMovie?.name ?: it.matchedSeries?.name ?: "No Stream"}'" }
            )
            cachedGlobal
        } else {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global matched cache MISS. Fetching raw trends and matching...")
            
            // 2. Fetch fresh trends from API and match them if cache is expired/null
            val trendingList = trendingRepository.getTrending()
            if (trendingList.isEmpty()) {
                com.cstv.app.di.IptvLog.w("TMDB", "🌐 TMDB API returned empty list of trends. Reverting to hero card.")
                return@withContext emptyList()
            }

            // Log raw trends returned by TMDB API!
            com.cstv.app.di.IptvLog.d("TMDB", "🌐 TMDB raw trends [${trendingList.size} items]: " + 
                trendingList.joinToString { "[${if (it.isMovie) "Movie" else "Series"}] '${it.title}' (${it.year})" }
            )

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

            com.cstv.app.di.IptvLog.d("TMDB", "⚡ Pre-normalizing IPTV titles...")
            // 20x Performance optimization: pre-normalize IPTV titles once before the loops
            val normalizedMovies = allMovies.map { it to TitleNormalizer.normalize(it.name ?: "") }
            val normalizedSeries = allSeries.map { it to TitleNormalizer.normalize(it.name ?: "") }
            com.cstv.app.di.IptvLog.d("TMDB", "⚡ Pre-normalization complete. Running similarity algorithms...")

            val fullMatchedResult = mutableListOf<TrendingCatalogItem>()
            val seenMatchedIds = mutableSetOf<Int>() // Prevent matching the same movie or series twice

            for (trending in trendingList) {
                val normalizedTrending = TitleNormalizer.normalize(trending.title)

                if (trending.isMovie) {
                    val candidates = mutableListOf<com.cstv.app.domain.model.VodStream>()
                    var bestScore = 0.0
                    
                    for ((movie, normalizedName) in normalizedMovies) {
                        if (movie.streamId in seenMatchedIds) continue
                        val score = ApproximateTitleMatcher.computeSimilarityNormalized(normalizedTrending, normalizedName)
                        if (score >= 0.8) {
                            if (score > bestScore) {
                                bestScore = score
                                candidates.clear()
                                candidates.add(movie)
                            } else if (score == bestScore) {
                                candidates.add(movie)
                            }
                        }
                    }
                    
                    if (candidates.isNotEmpty()) {
                        candidates.forEach { seenMatchedIds.add(it.streamId) }
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match movie: '${trending.title}' ↔ ${candidates.size} version(s) found (best score: $bestScore)")
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedMovies = candidates))
                    } else {
                        com.cstv.app.di.IptvLog.d("TMDB", "❔ No match found in catalog for trending movie: '${trending.title}'")
                    }
                } else {
                    val candidates = mutableListOf<com.cstv.app.domain.model.SeriesStream>()
                    var bestScore = 0.0
                    
                    for ((series, normalizedName) in normalizedSeries) {
                        if (series.seriesId in seenMatchedIds) continue
                        val score = ApproximateTitleMatcher.computeSimilarityNormalized(normalizedTrending, normalizedName)
                        if (score >= 0.8) {
                            if (score > bestScore) {
                                bestScore = score
                                candidates.clear()
                                candidates.add(series)
                            } else if (score == bestScore) {
                                candidates.add(series)
                            }
                        }
                    }
                    
                    if (candidates.isNotEmpty()) {
                        candidates.forEach { seenMatchedIds.add(it.seriesId) }
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match series: '${trending.title}' ↔ ${candidates.size} version(s) found (best score: $bestScore)")
                        fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedSeriesList = candidates))
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

        // 3. Filter and resolve matched items on the fly by hidden categories of the ACTIVE profile
        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)

        val filteredResult = matchedList.mapNotNull { item ->
            try {
                val movies = item.matchedMovies
                val seriesList = item.matchedSeriesList

                if (!movies.isNullOrEmpty()) {
                    // Revalidate that candidates still exist in the database (Bug B-3)
                    val existingMovies = movies.mapNotNull { vodRepository.getStreamById(it.streamId) }
                    if (existingMovies.isEmpty()) {
                        com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.trendingTitle.title}' filtered out because ALL matched versions were deleted from database.")
                        return@mapNotNull null
                    }
                    val allowedMovies = existingMovies.filter { it.categoryId !in hiddenMovies }
                    if (allowedMovies.isNotEmpty()) {
                        val selected = allowedMovies.first()
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Selected allowed movie version for '${item.trendingTitle.title}': '${selected.name}' (Category: '${selected.categoryId}')")
                        item.copy(matchedMovie = selected, matchedMovies = allowedMovies)
                    } else {
                        com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.trendingTitle.title}' filtered out because ALL matched versions belong to hidden categories.")
                        null // All matched movie versions are hidden
                    }
                } else if (!seriesList.isNullOrEmpty()) {
                    // Revalidate that candidates still exist in the database (Bug B-3)
                    val existingSeries = seriesList.mapNotNull { seriesRepository.getStreamById(it.seriesId) }
                    if (existingSeries.isEmpty()) {
                        com.cstv.app.di.IptvLog.d("TMDB", "🚫 Series '${item.trendingTitle.title}' filtered out because ALL matched versions were deleted from database.")
                        return@mapNotNull null
                    }
                    val allowedSeries = existingSeries.filter { it.categoryId !in hiddenSeries }
                    if (allowedSeries.isNotEmpty()) {
                        val selected = allowedSeries.first()
                        com.cstv.app.di.IptvLog.d("TMDB", "🎯 Selected allowed series version for '${item.trendingTitle.title}': '${selected.name}' (Category: '${selected.categoryId}')")
                        item.copy(matchedSeries = selected, matchedSeriesList = allowedSeries)
                    } else {
                        com.cstv.app.di.IptvLog.d("TMDB", "🚫 Series '${item.trendingTitle.title}' filtered out because ALL matched versions belong to hidden categories.")
                        null // All matched series versions are hidden
                    }
                } else {
                    // Backward compatibility: support old v1.47.25 cache structure where lists are null but singular elements are present
                    if (item.matchedMovie != null) {
                        val existingMovie = vodRepository.getStreamById(item.matchedMovie.streamId)
                        if (existingMovie != null) {
                            val isHidden = existingMovie.categoryId in hiddenMovies
                            if (!isHidden) {
                                com.cstv.app.di.IptvLog.d("TMDB", "🎯 Selected allowed movie version (Legacy Cache) for '${item.trendingTitle.title}': '${existingMovie.name}'")
                                item.copy(matchedMovie = existingMovie, matchedMovies = listOf(existingMovie))
                            } else {
                                com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.trendingTitle.title}' (Legacy Cache) filtered out because it is in a hidden category.")
                                null
                            }
                        } else {
                            com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.trendingTitle.title}' (Legacy Cache) filtered out because it was deleted from database.")
                            null
                        }
                    } else if (item.matchedSeries != null) {
                        val existingSeries = seriesRepository.getStreamById(item.matchedSeries.seriesId)
                        if (existingSeries != null) {
                            val isHidden = existingSeries.categoryId in hiddenSeries
                            if (!isHidden) {
                                com.cstv.app.di.IptvLog.d("TMDB", "🎯 Selected allowed series version (Legacy Cache) for '${item.trendingTitle.title}': '${existingSeries.name}'")
                                item.copy(matchedSeries = existingSeries, matchedSeriesList = listOf(existingSeries))
                            } else {
                                com.cstv.app.di.IptvLog.d("TMDB", "🚫 Series '${item.trendingTitle.title}' (Legacy Cache) filtered out because it is in a hidden category.")
                                null
                            }
                        } else {
                            com.cstv.app.di.IptvLog.d("TMDB", "🚫 Series '${item.trendingTitle.title}' (Legacy Cache) filtered out because it was deleted from database.")
                            null
                        }
                    } else {
                        // Skip items that have absolutely no matched streams
                        null
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.cstv.app.di.IptvLog.e("TMDB", "⚠️ Exception while filtering item: ${e.message}", e)
                null
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
