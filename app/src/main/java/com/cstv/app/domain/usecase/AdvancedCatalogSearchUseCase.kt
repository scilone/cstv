package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.GenreParser
import com.cstv.app.domain.model.SearchMediaType
import com.cstv.app.domain.model.SearchResult
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
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
            vodFiltered = vodFiltered.filter { stream -> stream.matchesTextQuery(query) }
            seriesFiltered = seriesFiltered.filter { stream -> stream.matchesTextQuery(query) }
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

        // Apply yearRange — null = pas de filtre (voir AdvancedSearchFilter).
        // Les items non enrichis (releaseYear null) ne sont exclus QUE si un
        // filtre année est explicitement actif.
        filter.yearRange?.let { yr ->
            vodFiltered = vodFiltered.filter { it.releaseYear != null && it.releaseYear in yr }
            seriesFiltered = seriesFiltered.filter { it.releaseYear != null && it.releaseYear in yr }
        }

        // Apply genres (AND logic : l'item doit contenir TOUS les genres sélectionnés)
        if (filter.genres.isNotEmpty()) {
            vodFiltered = vodFiltered.filter { stream ->
                filter.genres.all { selectedGenre -> GenreParser.matches(stream.genre, selectedGenre) }
            }
            seriesFiltered = seriesFiltered.filter { stream ->
                filter.genres.all { selectedGenre -> GenreParser.matches(stream.genre, selectedGenre) }
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

    private fun com.cstv.app.domain.model.VodStream.matchesTextQuery(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            actors?.contains(query, ignoreCase = true) == true ||
            director?.contains(query, ignoreCase = true) == true ||
            genre?.contains(query, ignoreCase = true) == true

    private fun com.cstv.app.domain.model.SeriesStream.matchesTextQuery(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            actors?.contains(query, ignoreCase = true) == true ||
            director?.contains(query, ignoreCase = true) == true ||
            genre?.contains(query, ignoreCase = true) == true
}
