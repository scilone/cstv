package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.AdvancedSearchFilter
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.model.GenreParser
import com.poc.iptvxtream.domain.model.SearchMediaType
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AdvancedCatalogSearchUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    // Le filtrage parcourt tout le catalogue (potentiellement plusieurs milliers
    // d'items) avec des regex GenreParser par item, et il est rappelé à chaque
    // changement de filtre. On l'exécute hors du thread Main pour éviter le jank.
    suspend operator fun invoke(
        query: String?,
        filter: AdvancedSearchFilter
    ): SearchResult = withContext(Dispatchers.Default) {
        val showVod = filter.mediaType == null || filter.mediaType == SearchMediaType.FILM
        val showSeries = filter.mediaType == null || filter.mediaType == SearchMediaType.SERIE

        val rawVod = if (showVod) {
            try {
                vodRepository.getVodStreams("all", forceRefresh = false)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val rawSeries = if (showSeries) {
            try {
                seriesRepository.getSeriesStreams("all", forceRefresh = false)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val hiddenVod = getHiddenCategoryIds(CategoryType.VOD)
        val hiddenSeries = getHiddenCategoryIds(CategoryType.SERIES)

        // Filter out hidden categories
        var vodFiltered = rawVod.filter { it.categoryId !in hiddenVod }
        var seriesFiltered = rawSeries.filter { it.categoryId !in hiddenSeries }

        // Apply optional text query
        if (!query.isNullOrBlank()) {
            vodFiltered = vodFiltered.filter { it.name.contains(query, ignoreCase = true) }
            seriesFiltered = seriesFiltered.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Apply categoryId (only if a type is chosen and not null and not "all")
        if (filter.mediaType != null && !filter.categoryId.isNullOrBlank() && filter.categoryId != "all") {
            when (filter.mediaType) {
                SearchMediaType.FILM -> {
                    vodFiltered = vodFiltered.filter { it.categoryId == filter.categoryId }
                }
                SearchMediaType.SERIE -> {
                    seriesFiltered = seriesFiltered.filter { it.categoryId == filter.categoryId }
                }
            }
        }

        // Apply minRating
        if (filter.minRating != null) {
            val minRatingDouble = filter.minRating.toDouble()
            vodFiltered = vodFiltered.filter {
                val r = it.rating?.trim()?.toDoubleOrNull() ?: 0.0
                r >= minRatingDouble
            }
            seriesFiltered = seriesFiltered.filter {
                val r = it.rating?.trim()?.toDoubleOrNull() ?: 0.0
                r >= minRatingDouble
            }
        }

        // Apply yearRange — UNIQUEMENT si l'utilisateur a resserré la plage.
        // La plage par défaut (1980..2025) = "toutes les années" : ne doit pas
        // exclure les items non enrichis (releaseYear null) ni hors bornes,
        // sinon la navigation par défaut et le compteur total seraient faussés.
        val yr = filter.yearRange
        val yearActive = yr != null &&
            (yr.first > AdvancedSearchFilter.DEFAULT_MIN_YEAR || yr.last < AdvancedSearchFilter.DEFAULT_MAX_YEAR)
        if (yearActive) {
            vodFiltered = vodFiltered.filter { it.releaseYear != null && it.releaseYear in yr!! }
            seriesFiltered = seriesFiltered.filter { it.releaseYear != null && it.releaseYear in yr!! }
        }

        // Apply genres (OR logic)
        if (filter.genres.isNotEmpty()) {
            vodFiltered = vodFiltered.filter { stream ->
                filter.genres.any { selectedGenre -> GenreParser.matches(stream.genre, selectedGenre) }
            }
            seriesFiltered = seriesFiltered.filter { stream ->
                filter.genres.any { selectedGenre -> GenreParser.matches(stream.genre, selectedGenre) }
            }
        }

        SearchResult(
            liveResults = emptyList(),
            vodResults = vodFiltered,
            seriesResults = seriesFiltered
        )
    }

    private suspend fun getHiddenCategoryIds(type: CategoryType): Set<String> {
        return try {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet()
        }
    }
}
