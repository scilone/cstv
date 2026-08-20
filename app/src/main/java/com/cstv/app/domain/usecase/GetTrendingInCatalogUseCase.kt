package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.ExternalCatalogLink
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.CpuLowPriorityDispatcher
import com.cstv.app.domain.model.ExternalCatalogMatcher
import com.cstv.app.domain.repository.ExternalCatalogLinkRepository
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject

private const val KIND_MOVIE = "movie"
private const val KIND_SERIES = "series"

class GetTrendingInCatalogUseCase @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val catalogFreshness: com.cstv.app.data.sync.CatalogFreshness,
    private val externalCatalogLinkRepository: ExternalCatalogLinkRepository
) {

    // Retour utilisateur du 2026-08-18 (F39/T21) : sur cache expiré,
    // `buildMatchedTrends` relit et normalise tout le catalogue (films +
    // séries) puis fait tourner le matcher de similarité dessus — même
    // famine de temps CPU que le moteur de recommandations avant son passage
    // sur un fil dédié (voir `GetRecommendationsUseCase`). `Dispatchers.Default`
    // prenait les quatre cœurs de l'appareil pendant ce calcul, au détriment
    // de la navigation. `CpuLowPriorityDispatcher` sérialise ce travail
    // derrière l'UI au lieu de la concurrencer.
    suspend operator fun invoke(): List<TrendingCatalogItem> = withContext(CpuLowPriorityDispatcher.instance) {
        com.cstv.app.di.IptvLog.d("CATALOG", "🚀 GetTrendingInCatalogUseCase triggered.")

        // Invalidate cache if catalog was resynchronized after cache generation (Bug B-3)
        val lastVodSync = catalogFreshness.vodSyncedAt()
        val lastSeriesSync = catalogFreshness.seriesSyncedAt()
        val lastCatalogSyncTime = maxOf(lastVodSync, lastSeriesSync)

        // 1. Check persistent global device cache
        val cachedGlobal = trendingRepository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime)
        val matchedList = if (cachedGlobal != null) {
            com.cstv.app.di.IptvLog.d("CATALOG", "💾 Global matched cache HIT. Loaded ${cachedGlobal.size} items: " +
                cachedGlobal.joinToString { "'${it.trendingTitle.title}' ↔ '${it.matchedMovie?.name ?: it.matchedSeries?.name ?: "No Stream"}'" }
            )
            cachedGlobal
        } else {
            com.cstv.app.di.IptvLog.d("CATALOG", "💾 Global matched cache MISS. Fetching raw trends and matching...")
            // Le rafraîchissement forcé au lancement ne doit jamais faire disparaître
            // la ligne Tendances : si CATALOG est injoignable, on retombe sur le cache
            // persistant, qui n'a pas été effacé.
            buildMatchedTrends().takeIf { it.isNotEmpty() }
                ?: trendingRepository
                    // Repli de dernier recours : mêmes dérogations que `cached()`,
                    // une ligne Tendances datée valant mieux que pas de ligne.
                    .getCachedMatchedTrendsGlobal(
                        ignoreSessionRefresh = true,
                        ignoreExpiration = true,
                        ignoreCatalogSync = true
                    )
                    .orEmpty()
                    .also { fallback ->
                        if (fallback.isNotEmpty()) {
                            com.cstv.app.di.IptvLog.w("CATALOG", "💾 Rafraîchissement CATALOG indisponible : repli sur le cache persistant (${fallback.size} éléments).")
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
        com.cstv.app.di.IptvLog.d("CATALOG", "🏁 GetTrendingInCatalogUseCase returning ${finalResult.size} items to the UI.")
        finalResult
    }

    suspend fun isCacheExpired(): Boolean = withContext(Dispatchers.Default) {
        val lastCatalogSyncTime = maxOf(catalogFreshness.vodSyncedAt(), catalogFreshness.seriesSyncedAt())
        trendingRepository.isCacheExpired(lastCatalogSyncTime)
    }

    /**
     * Lecture immédiate du cache persistant, sans aucun accès réseau.
     *
     * `invoke()` force un rafraîchissement CATALOG au premier appel de la session
     * (voir `CatalogSessionRefreshGate`) : sur un relancement d'application, la
     * Hero Card n'apparaissait donc qu'après un aller-retour réseau complet, et
     * s'insérait dans un accueil déjà rendu. Cette entrée sert le contenu déjà
     * connu tout de suite ; l'appelant enchaîne sur `invoke()` en arrière-plan.
     *
     * Aucune des trois invalidations ne s'applique ici, pas même celle du
     * catalogue resynchronisé (B-3) : au démarrage à froid, la synchronisation
     * du catalogue est justement ce qui vient de s'exécuter, donc la condition
     * était toujours vraie et la Hero Card manquait à chaque lancement. Un
     * appariement périmé n'a d'effet que sur un identifiant de flux, corrigé
     * quelques secondes plus tard par le rafraîchissement.
     */
    suspend fun cached(): List<TrendingCatalogItem> = withContext(Dispatchers.Default) {
        val cached = trendingRepository
            .getCachedMatchedTrendsGlobal(
                ignoreSessionRefresh = true,
                ignoreExpiration = true,
                ignoreCatalogSync = true
            )
            .orEmpty()
        if (cached.isEmpty()) return@withContext emptyList()

        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)
        val result = cached.mapNotNull { filterItem(it, hiddenMovies, hiddenSeries) }.take(10)
        com.cstv.app.di.IptvLog.d("CATALOG", "⚡ Tendances servies depuis le cache sans attente réseau : ${result.size} éléments.")
        result
    }

    /**
     * Récupère les tendances CATALOG et les met en correspondance avec le catalogue
     * local. Retourne une liste vide si CATALOG est injoignable ou si aucun titre ne
     * correspond ; l'appelant décide alors du repli.
     */
    private suspend fun buildMatchedTrends(): List<TrendingCatalogItem> {
        // 2. Fetch fresh trends from API and match them if cache is expired/null
        val trendingList = trendingRepository.getTrending()
        if (trendingList.isEmpty()) {
            com.cstv.app.di.IptvLog.w("CATALOG", "🌐 CATALOG API returned empty list of trends. Reverting to hero card.")
            return emptyList()
        }

        // Log raw trends returned by CATALOG API!
        com.cstv.app.di.IptvLog.d("CATALOG", "🌐 CATALOG raw trends [${trendingList.size} items]: " +
            trendingList.joinToString { "[${if (it.isMovie) "Movie" else "Series"}] '${it.title}' (${it.year})" }
        )

        // Ne charger que les années utiles (plus les titres non encore enrichis)
        // au lieu de tout le catalogue : l'appariement exige désormais l'année
        // exacte, tout le reste serait normalisé pour rien.
        val movieYears = trendingList.filter { it.isMovie }.mapNotNull { it.year }.toSet()
        val seriesYears = trendingList.filterNot { it.isMovie }.mapNotNull { it.year }.toSet()

        val allMovies = try {
            val list = vodRepository.getCachedVodStreamsByYears(movieYears)
            com.cstv.app.di.IptvLog.d("CATALOG", "📦 Loaded ${list.size} movies from local database cache (années ${movieYears.sorted()}).")
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.cstv.app.di.IptvLog.e("CATALOG", "📦 Failed to load movies from local database", e)
            emptyList()
        }

        val allSeries = try {
            val list = seriesRepository.getCachedSeriesStreamsByYears(seriesYears)
            com.cstv.app.di.IptvLog.d("CATALOG", "📦 Loaded ${list.size} series from local database cache (années ${seriesYears.sorted()}).")
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.cstv.app.di.IptvLog.e("CATALOG", "📦 Failed to load series from local database", e)
            emptyList()
        }

        com.cstv.app.di.IptvLog.d("CATALOG", "⚡ Pre-normalizing IPTV titles...")
        val normalizedMovies = ExternalCatalogMatcher.prepareMovies(allMovies)
        val normalizedSeries = ExternalCatalogMatcher.prepareSeries(allSeries)
        com.cstv.app.di.IptvLog.d("CATALOG", "⚡ Pre-normalization complete. Running similarity algorithms...")

        // T24 : résolution batch des externalId déjà associés, avant tout
        // matching — un item connu saute entièrement le scan du catalogue.
        val externalIds = trendingList.mapNotNull { it.externalId }.distinct()
        val lookupStartNanos = System.nanoTime()
        val linksByExternalId = if (externalIds.isNotEmpty()) {
            externalCatalogLinkRepository.findByExternalIds(externalIds).groupBy { it.externalId }
        } else emptyMap()
        val lookupMs = (System.nanoTime() - lookupStartNanos) / 1_000_000

        val fullMatchedResult = mutableListOf<TrendingCatalogItem>()
        val newlyMatchedLinks = mutableListOf<ExternalCatalogLink>()
        // Xtream movie and series identifiers use separate namespaces, so a
        // shared set would incorrectly reject a series whose id matches an
        // already matched movie id (or vice versa).
        val seenMovieIds = mutableSetOf<Int>()
        val seenSeriesIds = mutableSetOf<Int>()
        var roomHits = 0
        var matcherFallbacks = 0
        val matcherStartNanos = System.nanoTime()

        for (trending in trendingList) {
            val knownLinks = trending.externalId?.let { linksByExternalId[it] }
            if (trending.isMovie) {
                val knownMovies = knownLinks?.filter { it.kind == KIND_MOVIE }
                if (!knownMovies.isNullOrEmpty()) {
                    // Existence/catégories masquées revalidées plus tard par `filterItem` (Bug B-3).
                    roomHits++
                    knownMovies.forEach { seenMovieIds.add(it.providerId) }
                    fullMatchedResult.add(
                        TrendingCatalogItem(
                            trendingTitle = trending,
                            matchedMovies = knownMovies.map { com.cstv.app.domain.model.VodStream(streamId = it.providerId, name = trending.title, streamIcon = null, rating = null, added = null, categoryId = "") }
                        )
                    )
                    continue
                }
                matcherFallbacks++
                val match = ExternalCatalogMatcher.findBestMatches(
                    externalTitle = trending.title,
                    externalYear = trending.year,
                    catalog = normalizedMovies,
                    excludedIds = seenMovieIds
                )
                if (match != null) {
                    match.candidates.forEach { seenMovieIds.add(it.streamId) }
                    com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Match movie: '${trending.title}' (CATALOG ${trending.year}) ↔ ${match.candidates.size} version(s) found (best score: ${match.score}, yearRank: ${match.yearRank.name})")
                    fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedMovies = match.candidates))
                    trending.externalId?.let { cid ->
                        match.candidates.forEach { newlyMatchedLinks.add(ExternalCatalogLink(KIND_MOVIE, it.streamId, cid)) }
                    }
                } else {
                    com.cstv.app.di.IptvLog.d("CATALOG", "❔ No match found in catalog for trending movie: '${trending.title}'")
                }
            } else {
                val knownSeries = knownLinks?.filter { it.kind == KIND_SERIES }
                if (!knownSeries.isNullOrEmpty()) {
                    roomHits++
                    knownSeries.forEach { seenSeriesIds.add(it.providerId) }
                    fullMatchedResult.add(
                        TrendingCatalogItem(
                            trendingTitle = trending,
                            matchedSeriesList = knownSeries.map { com.cstv.app.domain.model.SeriesStream(seriesId = it.providerId, name = trending.title, cover = null, rating = null, added = null, categoryId = "") }
                        )
                    )
                    continue
                }
                matcherFallbacks++
                val match = ExternalCatalogMatcher.findBestMatches(
                    externalTitle = trending.title,
                    externalYear = trending.year,
                    catalog = normalizedSeries,
                    excludedIds = seenSeriesIds
                )
                if (match != null) {
                    match.candidates.forEach { seenSeriesIds.add(it.seriesId) }
                    com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Match series: '${trending.title}' (CATALOG ${trending.year}) ↔ ${match.candidates.size} version(s) found (best score: ${match.score}, yearRank: ${match.yearRank.name})")
                    fullMatchedResult.add(TrendingCatalogItem(trendingTitle = trending, matchedSeriesList = match.candidates))
                    trending.externalId?.let { cid ->
                        match.candidates.forEach { newlyMatchedLinks.add(ExternalCatalogLink(KIND_SERIES, it.seriesId, cid)) }
                    }
                } else {
                    com.cstv.app.di.IptvLog.d("CATALOG", "❔ No match found in catalog for trending series: '${trending.title}'")
                }
            }
        }
        val matcherMs = (System.nanoTime() - matcherStartNanos) / 1_000_000
        com.cstv.app.di.IptvLog.d(
            "PERF",
            "Trending external-id lookup: $roomHits/${trendingList.size} hits Room en ${lookupMs}ms, $matcherFallbacks fallback match en ${matcherMs}ms"
        )

        if (newlyMatchedLinks.isNotEmpty()) {
            runCatching { externalCatalogLinkRepository.persistAll(newlyMatchedLinks) }
                .onFailure { com.cstv.app.di.IptvLog.e("CATALOG", "Persistance externalId impossible (T24)", it) }
        }

        // Save full matched results globally before returning
        if (fullMatchedResult.isNotEmpty()) {
            trendingRepository.saveMatchedTrendsGlobal(fullMatchedResult)
        } else {
            com.cstv.app.di.IptvLog.w("CATALOG", "⚠️ No trends matches found in the entire local database catalog!")
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
                    com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Movie '${item.trendingTitle.title}' filtered out because ALL matched versions were deleted from database.")
                    return null
                }
                val allowedMovies = existingMovies.filter { it.categoryId !in hiddenMovies }
                if (allowedMovies.isNotEmpty()) {
                    val selected = allowedMovies.first()
                    com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Selected allowed movie version for '${item.trendingTitle.title}': '${selected.name}' (Category: '${selected.categoryId}')")
                    item.copy(matchedMovie = selected, matchedMovies = allowedMovies)
                } else {
                    com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Movie '${item.trendingTitle.title}' filtered out because ALL matched versions belong to hidden categories.")
                    null // All matched movie versions are hidden
                }
            } else if (!seriesList.isNullOrEmpty()) {
                // Revalidate that candidates still exist in the database (Bug B-3)
                val existingSeries = seriesList.mapNotNull { seriesRepository.getStreamById(it.seriesId) }
                if (existingSeries.isEmpty()) {
                    com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Series '${item.trendingTitle.title}' filtered out because ALL matched versions were deleted from database.")
                    return null
                }
                val allowedSeries = existingSeries.filter { it.categoryId !in hiddenSeries }
                if (allowedSeries.isNotEmpty()) {
                    val selected = allowedSeries.first()
                    com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Selected allowed series version for '${item.trendingTitle.title}': '${selected.name}' (Category: '${selected.categoryId}')")
                    item.copy(matchedSeries = selected, matchedSeriesList = allowedSeries)
                } else {
                    com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Series '${item.trendingTitle.title}' filtered out because ALL matched versions belong to hidden categories.")
                    null // All matched series versions are hidden
                }
            } else {
                // Backward compatibility: support old v1.47.25 cache structure where lists are null but singular elements are present
                if (item.matchedMovie != null) {
                    val existingMovie = vodRepository.getStreamById(item.matchedMovie.streamId)
                    if (existingMovie != null) {
                        val isHidden = existingMovie.categoryId in hiddenMovies
                        if (!isHidden) {
                            com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Selected allowed movie version (Legacy Cache) for '${item.trendingTitle.title}': '${existingMovie.name}'")
                            item.copy(matchedMovie = existingMovie, matchedMovies = listOf(existingMovie))
                        } else {
                            com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Movie '${item.trendingTitle.title}' (Legacy Cache) filtered out because it is in a hidden category.")
                            null
                        }
                    } else {
                        com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Movie '${item.trendingTitle.title}' (Legacy Cache) filtered out because it was deleted from database.")
                        null
                    }
                } else if (item.matchedSeries != null) {
                    val existingSeries = seriesRepository.getStreamById(item.matchedSeries.seriesId)
                    if (existingSeries != null) {
                        val isHidden = existingSeries.categoryId in hiddenSeries
                        if (!isHidden) {
                            com.cstv.app.di.IptvLog.d("CATALOG", "🎯 Selected allowed series version (Legacy Cache) for '${item.trendingTitle.title}': '${existingSeries.name}'")
                            item.copy(matchedSeries = existingSeries, matchedSeriesList = listOf(existingSeries))
                        } else {
                            com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Series '${item.trendingTitle.title}' (Legacy Cache) filtered out because it is in a hidden category.")
                            null
                        }
                    } else {
                        com.cstv.app.di.IptvLog.d("CATALOG", "🚫 Series '${item.trendingTitle.title}' (Legacy Cache) filtered out because it was deleted from database.")
                        null
                    }
                } else {
                    // Skip items that have absolutely no matched streams
                    null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.cstv.app.di.IptvLog.e("CATALOG", "⚠️ Exception while filtering item: ${e.message}", e)
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
