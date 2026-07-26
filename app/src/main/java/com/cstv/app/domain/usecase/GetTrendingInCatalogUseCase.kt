package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.TmdbCatalogMatcher
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
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val catalogFreshness: com.cstv.app.data.sync.CatalogFreshness
) {

    suspend operator fun invoke(): List<TrendingCatalogItem> = withContext(Dispatchers.Default) {
        com.cstv.app.di.IptvLog.d("TMDB", "🚀 GetTrendingInCatalogUseCase triggered.")

        // Invalidate cache if catalog was resynchronized after cache generation (Bug B-3)
        val lastVodSync = catalogFreshness.vodSyncedAt()
        val lastSeriesSync = catalogFreshness.seriesSyncedAt()
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
            // Le rafraîchissement forcé au lancement ne doit jamais faire disparaître
            // la ligne Tendances : si TMDB est injoignable, on retombe sur le cache
            // persistant, qui n'a pas été effacé.
            buildMatchedTrends().takeIf { it.isNotEmpty() }
                ?: trendingRepository
                    .getCachedMatchedTrendsGlobal(lastCatalogSyncTime, ignoreSessionRefresh = true)
                    .orEmpty()
                    .also { fallback ->
                        if (fallback.isNotEmpty()) {
                            com.cstv.app.di.IptvLog.w("TMDB", "💾 Rafraîchissement TMDB indisponible : repli sur le cache persistant (${fallback.size} éléments).")
                        }
                    }
        }

        if (matchedList.isEmpty()) {
            return@withContext emptyList()
        }

        // 3. Filter and resolve matched items on the fly by hidden categories of the ACTIVE profile
        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)

        val filteredResult = matchedList.mapNotNull { item ->
            filterItem(item, hiddenMovies, hiddenSeries)
        }

        // 4. Cap final filtered list at 10 items
        val finalResult = filteredResult.take(10)
        com.cstv.app.di.IptvLog.d("TMDB", "🏁 GetTrendingInCatalogUseCase returning ${finalResult.size} items to the UI.")
        finalResult
    }

    /**
     * Récupère les tendances TMDB et les met en correspondance avec le catalogue
     * local. Retourne une liste vide si TMDB est injoignable ou si aucun titre ne
     * correspond ; l'appelant décide alors du repli.
     */
    private suspend fun buildMatchedTrends(): List<TrendingCatalogItem> {
        // 2. Fetch fresh trends from API and match them if cache is expired/null
        val trendingList = trendingRepository.getTrending()
        if (trendingList.isEmpty()) {
            com.cstv.app.di.IptvLog.w("TMDB", "🌐 TMDB API returned empty list of trends. Reverting to hero card.")
            return emptyList()
        }

        // Log raw trends returned by TMDB API!
        com.cstv.app.di.IptvLog.d("TMDB", "🌐 TMDB raw trends [${trendingList.size} items]: " +
            trendingList.joinToString { "[${if (it.isMovie) "Movie" else "Series"}] '${it.title}' (${it.year})" }
        )

        // Ne charger que les années utiles (plus les titres non encore enrichis)
        // au lieu de tout le catalogue : l'appariement exige désormais l'année
        // exacte, tout le reste serait normalisé pour rien.
        val movieYears = trendingList.filter { it.isMovie }.mapNotNull { it.year }.toSet()
        val seriesYears = trendingList.filterNot { it.isMovie }.mapNotNull { it.year }.toSet()

        val allMovies = try {
            val list = vodRepository.getCachedVodStreamsByYears(movieYears)
            com.cstv.app.di.IptvLog.d("TMDB", "📦 Loaded ${list.size} movies from local database cache (années ${movieYears.sorted()}).")
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.cstv.app.di.IptvLog.e("TMDB", "📦 Failed to load movies from local database", e)
            emptyList()
        }

        val allSeries = try {
            val list = seriesRepository.getCachedSeriesStreamsByYears(seriesYears)
            com.cstv.app.di.IptvLog.d("TMDB", "📦 Loaded ${list.size} series from local database cache (années ${seriesYears.sorted()}).")
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.cstv.app.di.IptvLog.e("TMDB", "📦 Failed to load series from local database", e)
            emptyList()
        }

        com.cstv.app.di.IptvLog.d("TMDB", "⚡ Pre-normalizing IPTV titles...")
        val normalizedMovies = TmdbCatalogMatcher.prepareMovies(allMovies)
        val normalizedSeries = TmdbCatalogMatcher.prepareSeries(allSeries)
        com.cstv.app.di.IptvLog.d("TMDB", "⚡ Pre-normalization complete. Running similarity algorithms...")

        val fullMatchedResult = mutableListOf<TrendingCatalogItem>()
        // Xtream movie and series identifiers use separate namespaces, so a
        // shared set would incorrectly reject a series whose id matches an
        // already matched movie id (or vice versa).
        val seenMovieIds = mutableSetOf<Int>()
        val seenSeriesIds = mutableSetOf<Int>()

        for (trending in trendingList) {
            if (trending.isMovie) {
                val match = TmdbCatalogMatcher.findBestMatches(
                    tmdbTitle = trending.title,
                    tmdbYear = trending.year,
                    catalog = normalizedMovies,
                    excludedIds = seenMovieIds
                )
                if (match != null) {
                    match.candidates.forEach { seenMovieIds.add(it.streamId) }
                    com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match movie: '${trending.title}' (TMDB ${trending.year}) ↔ ${match.candidates.size} version(s) found (best score: ${match.score}, yearRank: ${match.yearRank.name})")
                    fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedMovies = match.candidates))
                } else {
                    com.cstv.app.di.IptvLog.d("TMDB", "❔ No match found in catalog for trending movie: '${trending.title}'")
                }
            } else {
                val match = TmdbCatalogMatcher.findBestMatches(
                    tmdbTitle = trending.title,
                    tmdbYear = trending.year,
                    catalog = normalizedSeries,
                    excludedIds = seenSeriesIds
                )
                if (match != null) {
                    match.candidates.forEach { seenSeriesIds.add(it.seriesId) }
                    com.cstv.app.di.IptvLog.d("TMDB", "🎯 Match series: '${trending.title}' (TMDB ${trending.year}) ↔ ${match.candidates.size} version(s) found (best score: ${match.score}, yearRank: ${match.yearRank.name})")
                    fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedSeriesList = match.candidates))
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

        return fullMatchedResult
    }

    /**
     * Revalide un élément (issu du cache ou d'un match frais) contre la base
     * locale et les catégories masquées du profil actif. Retourne `null` si
     * l'élément ne doit pas être affiché.
     */
    private suspend fun filterItem(
        item: TrendingCatalogItem,
        hiddenMovies: Set<String>,
        hiddenSeries: Set<String>
    ): TrendingCatalogItem? {
        return try {
            val movies = item.matchedMovies
            val seriesList = item.matchedSeriesList

            if (!movies.isNullOrEmpty()) {
                // Revalidate that candidates still exist in the database (Bug B-3)
                val existingMovies = movies.mapNotNull { vodRepository.getStreamById(it.streamId) }
                if (existingMovies.isEmpty()) {
                    com.cstv.app.di.IptvLog.d("TMDB", "🚫 Movie '${item.trendingTitle.title}' filtered out because ALL matched versions were deleted from database.")
                    return null
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
                    return null
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
