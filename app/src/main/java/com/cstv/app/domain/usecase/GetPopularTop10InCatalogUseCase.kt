package com.cstv.app.domain.usecase

import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.PopularCatalogItem
import com.cstv.app.domain.model.PopularTop10Result
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TmdbCatalogMatcher
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.PopularRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetPopularTop10InCatalogUseCase @Inject constructor(
    private val popularRepository: PopularRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val catalogFreshness: com.cstv.app.data.sync.CatalogFreshness
) {
    suspend operator fun invoke(): PopularTop10Result = coroutineScope {
        // Les deux endpoints sont indépendants : une requête Films lente ne doit
        // jamais empêcher la branche Séries de livrer son résultat avant le
        // timeout global géré par HomeViewModel.
        val movies = async { loadMovies() }
        val series = async { loadSeries() }
        PopularTop10Result(movies = movies.await(), series = series.await())
    }

    private suspend fun loadMovies(): List<VodStream>? {
        return try {
        val syncedAt = catalogFreshness.vodSyncedAt()
        val matches = popularRepository.getCachedMatchedMovies(syncedAt)
            ?: buildMovieMatches()
                .also { fresh -> if (fresh.isNotEmpty()) popularRepository.saveMatchedMovies(fresh) }
                .takeIf { it.isNotEmpty() }
            // Rafraîchissement de lancement impossible (TMDB injoignable, catalogue
            // vide) : on repart sur le cache existant plutôt que de vider la ligne.
            ?: popularRepository.getCachedMatchedMovies(syncedAt, ignoreSessionRefresh = true)
            ?: return null
        val hidden = hiddenCategories(CategoryType.VOD)
        resolveMovies(matches, hidden).take(10).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        IptvLog.e("TMDB", "Impossible de résoudre les films populaires", e)
        null
        }
    }

    private suspend fun loadSeries(): List<SeriesStream>? {
        return try {
        val syncedAt = catalogFreshness.seriesSyncedAt()
        val matches = popularRepository.getCachedMatchedSeries(syncedAt)
            ?: buildSeriesMatches()
                .also { fresh -> if (fresh.isNotEmpty()) popularRepository.saveMatchedSeries(fresh) }
                .takeIf { it.isNotEmpty() }
            ?: popularRepository.getCachedMatchedSeries(syncedAt, ignoreSessionRefresh = true)
            ?: return null
        val hidden = hiddenCategories(CategoryType.SERIES)
        resolveSeries(matches, hidden).take(10).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        IptvLog.e("TMDB", "Impossible de résoudre les séries populaires", e)
        null
        }
    }

    private suspend fun buildMovieMatches(): List<PopularCatalogItem> {
        val popular = popularRepository.getPopularMovies()
        if (popular.isEmpty()) return emptyList()
        val streams = vodRepository.getCachedVodStreams("all")
        return withContext(Dispatchers.Default) {
            match(popular, TmdbCatalogMatcher.prepareMovies(streams)) { it.streamId }
        }
    }

    private suspend fun buildSeriesMatches(): List<PopularCatalogItem> {
        val popular = popularRepository.getPopularSeries()
        if (popular.isEmpty()) return emptyList()
        val streams = seriesRepository.getCachedSeriesStreams("all")
        return withContext(Dispatchers.Default) {
            match(popular, TmdbCatalogMatcher.prepareSeries(streams)) { it.seriesId }
        }
    }

    private fun <T> match(
        popular: List<TrendingTitle>,
        catalog: List<TmdbCatalogMatcher.CatalogCandidate<T>>,
        idOf: (T) -> Int
    ): List<PopularCatalogItem> {
        val usedIds = mutableSetOf<Int>()
        return popular.mapNotNull { title ->
            TmdbCatalogMatcher.findBestMatches(title.title, title.year, catalog, usedIds)?.let { match ->
                val ids = match.candidates.map(idOf)
                usedIds += ids
                IptvLog.d("TMDB", "🎯 Match popular: '${title.title}' (TMDB ${title.year}) ↔ ${ids.size} version(s) found (best score: ${match.score}, yearRank: ${match.yearRank.name})")
                PopularCatalogItem(ids)
            }
        }
    }

    private suspend fun resolveMovies(
        matches: List<PopularCatalogItem>,
        hiddenCategories: Set<String>
    ): List<VodStream> {
        val resolved = mutableListOf<VodStream>()
        for (match in matches) {
            for (id in match.localIds) {
                val stream = vodRepository.getStreamById(id)
                if (stream != null && stream.categoryId !in hiddenCategories) {
                    resolved += stream
                    break
                }
            }
        }
        return resolved
    }

    private suspend fun resolveSeries(
        matches: List<PopularCatalogItem>,
        hiddenCategories: Set<String>
    ): List<SeriesStream> {
        val resolved = mutableListOf<SeriesStream>()
        for (match in matches) {
            for (id in match.localIds) {
                val stream = seriesRepository.getStreamById(id)
                if (stream != null && stream.categoryId !in hiddenCategories) {
                    resolved += stream
                    break
                }
            }
        }
        return resolved
    }

    private suspend fun hiddenCategories(type: CategoryType): Set<String> =
        categoryPreferenceRepository.getPreferences(type)
            .filterValues { it.hidden }
            .keys
}
