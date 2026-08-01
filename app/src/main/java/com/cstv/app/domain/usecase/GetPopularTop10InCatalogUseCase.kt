package com.cstv.app.domain.usecase

import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.PopularCatalogItem
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetPopularTop10InCatalogUseCase @Inject constructor(
    private val popularRepository: PopularRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val catalogFreshness: com.cstv.app.data.sync.CatalogFreshness
) {

    /**
     * T8 (règle 1) : cache existant renvoyé pour affichage immédiat quel que
     * soit son âge, sans jamais toucher le réseau. `null` si aucun cache
     * exploitable n'existe — dans ce cas, [loadFreshMovies]/[loadFreshSeries]
     * doit être utilisé pour le tout premier chargement (règle 5).
     */
    suspend fun cachedMovies(): List<VodStream>? {
        return try {
            val syncedAt = catalogFreshness.vodSyncedAt()
            val matches = popularRepository.getCachedMatchedMoviesIgnoringAge(syncedAt) ?: return null
            resolveMovies(matches, hiddenCategories(CategoryType.VOD)).take(10).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            IptvLog.e("TMDB", "Impossible de lire le cache Popular Films", e)
            null
        }
    }

    suspend fun cachedSeries(): List<SeriesStream>? {
        return try {
            val syncedAt = catalogFreshness.seriesSyncedAt()
            val matches = popularRepository.getCachedMatchedSeriesIgnoringAge(syncedAt) ?: return null
            resolveSeries(matches, hiddenCategories(CategoryType.SERIES)).take(10).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            IptvLog.e("TMDB", "Impossible de lire le cache Popular Séries", e)
            null
        }
    }

    /**
     * T8 (règle 5) : chargement "à froid", quand aucun cache n'existe encore
     * — réseau si nécessaire, résultat directement affichable.
     */
    suspend fun loadFreshMovies(): List<VodStream>? = loadMovies()

    suspend fun loadFreshSeries(): List<SeriesStream>? = loadSeries()

    /**
     * T8-R1 : décide si un cache déjà affiché mérite une actualisation
     * silencieuse. Un cache frais (< 24h et cohérent avec le catalogue actuel)
     * ne doit déclencher ni appel TMDB ni écriture persistante.
     */
    suspend fun isMoviesCacheExpired(): Boolean =
        popularRepository.isMoviesCacheExpired(catalogFreshness.vodSyncedAt())

    suspend fun isSeriesCacheExpired(): Boolean =
        popularRepository.isSeriesCacheExpired(catalogFreshness.seriesSyncedAt())

    /**
     * T8 (règles 2, 3, 4) : actualisation réseau qui persiste le résultat
     * dans le cache local sans jamais rien renvoyer d'affichable — c'est au
     * ViewModel de ne pas appliquer ce résultat à l'état de la session en
     * cours. Un échec (réseau, quota, catalogue vide) laisse le dernier cache
     * valide intact (règle 8).
     */
    suspend fun refreshMoviesSilently() {
        try {
            val fresh = buildMovieMatches()
            if (fresh.isNotEmpty()) popularRepository.saveMatchedMovies(fresh)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            IptvLog.e("TMDB", "Actualisation silencieuse Popular Films impossible", e)
        }
    }

    suspend fun refreshSeriesSilently() {
        try {
            val fresh = buildSeriesMatches()
            if (fresh.isNotEmpty()) popularRepository.saveMatchedSeries(fresh)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            IptvLog.e("TMDB", "Actualisation silencieuse Popular Séries impossible", e)
        }
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
        // Seules les années des titres TMDB peuvent produire un match (année
        // exacte obligatoire), les autres lignes seraient normalisées pour rien.
        val streams = vodRepository.getCachedVodStreamsByYears(popular.mapNotNull { it.year }.toSet())
        return withContext(Dispatchers.Default) {
            match(popular, TmdbCatalogMatcher.prepareMovies(streams)) { it.streamId }
        }
    }

    private suspend fun buildSeriesMatches(): List<PopularCatalogItem> {
        val popular = popularRepository.getPopularSeries()
        if (popular.isEmpty()) return emptyList()
        val streams = seriesRepository.getCachedSeriesStreamsByYears(popular.mapNotNull { it.year }.toSet())
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
