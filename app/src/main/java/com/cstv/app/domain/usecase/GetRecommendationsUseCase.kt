package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.RecommendationEngine
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.MediaRatingRepository
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import com.cstv.app.data.local.storage.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Fil unique, de priorité minimale, réservé au moteur de recommandation.
 *
 * Le calcul lit tout le catalogue (près de quatre mille films et deux mille
 * séries), construit un profil de goûts puis note chaque titre : une douzaine
 * de secondes sur un téléviseur d'entrée de gamme. Sur `Dispatchers.Default`,
 * il disposait d'autant de fils que l'appareil a de cœurs — quatre ici — et
 * les prenait tous. Le premier catalogue ouvert pendant ce temps mettait treize
 * secondes à afficher ses catégories, contre soixante-quinze millisecondes une
 * fois le calcul terminé ; `syncIfStale`, huit secondes contre neuf
 * millisecondes. Ce n'est pas une requête lente, c'est une famine de temps
 * processeur.
 *
 * Un seul fil, en `MIN_PRIORITY`, laisse trois cœurs à la navigation et fait
 * céder l'ordonnanceur devant les fils d'interface. Les recommandations
 * arrivent un peu plus tard sur l'Accueil : c'est une garniture, pas un contenu
 * qu'on attend.
 *
 * [CpuLowPriorityDispatcher] : fil partagé avec les autres calculs lourds sur
 * catalogue complet (matching TMDB des tendances, F39) plutôt qu'un fil dédié
 * par use case — les concurrencer sur deux fils MIN_PRIORITY distincts recrée
 * la même famine à deux au lieu de l'éliminer.
 */
private val recommendationDispatcher = com.cstv.app.domain.model.CpuLowPriorityDispatcher.instance

// `open` pour la même raison que `ClearCatalogCacheUseCase` : le projet n'a pas
// `mockito-inline` (voir AGENTS.md), et `FavoritesViewModel` doit pouvoir être
// testé avec un double de ce moteur — dont le calcul lit tout le catalogue.
@Singleton
open class GetRecommendationsUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val profileManager: ProfileManager,
    private val mediaRatingRepository: MediaRatingRepository,
    private val favoritesRepository: FavoritesRepository,
    @Named("applicationScope") private val applicationScope: CoroutineScope
) {
    data class RecommendationResult(
        val movies: List<VodStream>,
        val series: List<SeriesStream>
    )

    companion object {
        /**
         * Poids d'un média dans le profil de goûts.
         *
         * Trois niveaux d'intention, du plus faible au plus fort : l'avoir
         * simplement regardé, l'avoir mis en favori, l'avoir explicitement aimé.
         * Mettre un titre en favori est un geste délibéré — plus révélateur du
         * goût qu'une lecture, qui peut n'être qu'un essai abandonné au bout de
         * dix minutes — mais moins explicite qu'un pouce en l'air. Les niveaux
         * ne se cumulent pas : un favori également aimé compte 3,0, pas 5,0,
         * sans quoi un seul titre écraserait le profil.
         */
        internal const val WATCHED_WEIGHT = 1.0
        internal const val FAVORITE_WEIGHT = 2.0
        internal const val LIKED_WEIGHT = 3.0
    }

    private val mutex = Mutex()

    // Ecrits depuis le fil du moteur, lus depuis les appelants : `@Volatile`
    // garantit la visibilite sans elargir la portee du verrou.
    @Volatile private var cachedResult: RecommendationResult? = null
    @Volatile private var cachedProfileId: Int = -1
    @Volatile private var cacheTimestamp: Long = 0L

    /** Calcul en cours, partage par tous les appelants (voir [invoke]). */
    private var inFlight: Deferred<RecommendationResult>? = null
    /**
     * Version des données qui ont invalidé le cache. Une invalidation ne doit
     * pas détacher le Deferred en cours : il continue dans applicationScope et
     * doit rester partageable par les appelants. Si elle arrive pendant le
     * calcul, le premier appel qui reçoit l'ancien résultat enchaîne un seul
     * nouveau calcul pour la nouvelle version.
     */
    private var cacheGeneration = 0L
    private val TTL_MILLIS = 24L * 60 * 60 * 1000L
    private val _invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val invalidations: SharedFlow<Unit> = _invalidations.asSharedFlow()

    /**
     * Le calcul est partagé et survit à l'annulation de son appelant.
     *
     * `HomeViewModel.refreshRecommendations` annule le travail précédent avant
     * d'en relancer un : au lancement, deux déclencheurs se suivent de près et
     * treize secondes de calcul étaient jetées puis refaites — visible dans les
     * traces, deux « Calculating recommendations » pour un seul résultat. Le
     * calcul vit désormais dans la portée applicative, et un second appel
     * rejoint celui qui court au lieu d'en ouvrir un autre.
     */
    suspend operator fun invoke(
        currentTimeMs: Long = System.currentTimeMillis()
    ): RecommendationResult {
        val currentProfileId = profileManager.currentProfileId()

        val (computation, computationGeneration) = mutex.withLock {
            val cached = cachedResult
            if (cached != null &&
                cachedProfileId == currentProfileId &&
                (currentTimeMs - cacheTimestamp) < TTL_MILLIS) {
                com.cstv.app.di.IptvLog.d("RECO", "Serving recommendations from cache for profile $currentProfileId")
                return@withLock null to cacheGeneration
            }

            val running = inFlight?.takeIf { it.isActive }
            if (running != null) {
                running to cacheGeneration
            } else {
                val generation = cacheGeneration
                applicationScope
                    .async(recommendationDispatcher) { compute(currentProfileId, currentTimeMs, generation) }
                    .also {
                        inFlight = it
                    } to generation
            }
        }
        if (computation == null) {
            val cached = cachedResult ?: return RecommendationResult(emptyList(), emptyList())
            return withoutHiddenCategories(cached)
        }

        val result = computation.await()
        val mustRecompute = mutex.withLock { computationGeneration != cacheGeneration }
        return if (mustRecompute) invoke(currentTimeMs) else result
    }

    private suspend fun compute(
        currentProfileId: Int,
        currentTimeMs: Long,
        generation: Long
    ): RecommendationResult {
        val startedAt = System.nanoTime()
        com.cstv.app.di.IptvLog.d("RECO", "Calculating recommendations for profile $currentProfileId...")

            // 1. Get user history
            val allHistory = try {
                vodRepository.getAllPlaybackPositions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val movieHistoryIds = allHistory.filter { it.type == "movie" }.map { it.streamId.toString() }.toSet()
            // B31 : PlaybackPosition.type vaut "movie" ou "episode" (jamais
            // "series", voir sa doc) — ce filtre ne matchait jamais rien, les
            // séries de l'historique n'entraient donc jamais dans le calcul
            // des recommandations.
            val seriesHistoryIds = allHistory.filter { it.type == "episode" && it.seriesId != null }.map { it.seriesId.toString() }.toSet()

            val ratings = try { mediaRatingRepository.getAllRatings() } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            val likedMovieIds = ratings.filter { it.mediaType == RatedMediaType.MOVIE && it.value == MediaRatingValue.LIKE }.map { it.mediaId.toString() }.toSet()
            val likedSeriesIds = ratings.filter { it.mediaType == RatedMediaType.SERIES && it.value == MediaRatingValue.LIKE }.map { it.mediaId.toString() }.toSet()
            val dislikedMovieIds = ratings.filter { it.mediaType == RatedMediaType.MOVIE && it.value == MediaRatingValue.DISLIKE }.map { it.mediaId.toString() }.toSet()
            val dislikedSeriesIds = ratings.filter { it.mediaType == RatedMediaType.SERIES && it.value == MediaRatingValue.DISLIKE }.map { it.mediaId.toString() }.toSet()

            // Les favoris sont un signal de goût à part entière. Un titre
            // explicitement rejeté le reste, même s'il est en favori : le pouce
            // en bas est la décision la plus récente et la plus explicite.
            val favorites = try {
                favoritesRepository.observeFavorites().first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            val favoriteMovieIds = favorites
                .filter { it.type == "movie" || it.type == "vod" }
                .map { it.id.toString() }
                .toSet() - dislikedMovieIds - likedMovieIds
            val favoriteSeriesIds = favorites
                .filter { it.type == "series" }
                .map { it.id.toString() }
                .toSet() - dislikedSeriesIds - likedSeriesIds

            val positiveMovieHistoryIds = movieHistoryIds - dislikedMovieIds - likedMovieIds - favoriteMovieIds
            val positiveSeriesHistoryIds = seriesHistoryIds - dislikedSeriesIds - likedSeriesIds - favoriteSeriesIds

            // Calculate distinct watched items count
            val totalWatchedCount = movieHistoryIds.size + seriesHistoryIds.size

            // Cold start protection: require at least 3 distinct items
            if (totalWatchedCount < 3 &&
                likedMovieIds.isEmpty() && likedSeriesIds.isEmpty() &&
                favoriteMovieIds.isEmpty() && favoriteSeriesIds.isEmpty()
            ) {
                com.cstv.app.di.IptvLog.d("RECO", "Cold start: Not enough history ($totalWatchedCount < 3) for profile $currentProfileId. Returning empty.")
                val emptyResult = RecommendationResult(emptyList(), emptyList())
                updateCache(currentProfileId, currentTimeMs, generation, emptyResult)
                return emptyResult
            }

            // 2. Fetch lightweight projections
            val catalogAt = System.nanoTime()
            val allMovies = try {
                vodRepository.getRecommendableVodItems()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val allSeries = try {
                seriesRepository.getRecommendableSeriesItems()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            com.cstv.app.di.IptvLog.d(
                "PERF",
                "RECO lecture catalogue ${allMovies.size} films + ${allSeries.size} séries " +
                    "en ${(System.nanoTime() - catalogAt) / 1_000_000}ms"
            )

            // 3. Exclude hidden categories
            val hiddenMovieCategories = getHiddenCategories(CategoryType.VOD)
            val hiddenSeriesCategories = getHiddenCategories(CategoryType.SERIES)

            val allowedMovies = allMovies.filter { it.categoryId !in hiddenMovieCategories }
            val allowedSeries = allSeries.filter { it.categoryId !in hiddenSeriesCategories }

            // 4. Map history IDs to actual streams to build the profile taste
            val tasteSignals = mutableListOf<RecommendationEngine.TasteSignal>()
            
            // Les ensembles sont disjoints par construction (voir plus haut) :
            // le `when` retient donc un seul poids par média, jamais leur somme.
            for (movie in allMovies) {
                when (movie.uniqueId) {
                    in likedMovieIds -> tasteSignals.add(RecommendationEngine.TasteSignal(movie, LIKED_WEIGHT))
                    in favoriteMovieIds -> tasteSignals.add(RecommendationEngine.TasteSignal(movie, FAVORITE_WEIGHT))
                    in positiveMovieHistoryIds -> tasteSignals.add(RecommendationEngine.TasteSignal(movie, WATCHED_WEIGHT))
                }
            }
            for (series in allSeries) {
                when (series.uniqueId) {
                    in likedSeriesIds -> tasteSignals.add(RecommendationEngine.TasteSignal(series, LIKED_WEIGHT))
                    in favoriteSeriesIds -> tasteSignals.add(RecommendationEngine.TasteSignal(series, FAVORITE_WEIGHT))
                    in positiveSeriesHistoryIds -> tasteSignals.add(RecommendationEngine.TasteSignal(series, WATCHED_WEIGHT))
                }
            }

            // 5. Calculate taste profile
            val profileTaste = RecommendationEngine.buildWeightedProfileTaste(tasteSignals)
            com.cstv.app.di.IptvLog.d("RECO", "Profile Taste built. Top Genres: ${profileTaste.genreWeights.entries.sortedByDescending { it.value }.take(3)}")

            // 6. Score and get top 100 for each type
            // Un favori est déjà dans la liste de l'utilisateur — le lui
            // re-proposer dans « Recommandé pour vous » n'apporte rien. Les
            // identifiants viennent de `favorites` non filtré : ceux retirés
            // plus haut au profit d'un poids supérieur (aimé) sont déjà exclus
            // par `likedMovieIds`/`likedSeriesIds`, les rejetés par les
            // `disliked*`.
            val allFavoriteMovieIds = favorites.filter { it.type == "movie" || it.type == "vod" }.map { it.id.toString() }.toSet()
            val allFavoriteSeriesIds = favorites.filter { it.type == "series" }.map { it.id.toString() }.toSet()

            val recommendedMoviesProj = RecommendationEngine.getTopRecommendations(
                candidates = allowedMovies,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = movieHistoryIds + likedMovieIds + dislikedMovieIds + allFavoriteMovieIds
            )

            val recommendedSeriesProj = RecommendationEngine.getTopRecommendations(
                candidates = allowedSeries,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = seriesHistoryIds + likedSeriesIds + dislikedSeriesIds + allFavoriteSeriesIds
            )

            val recommendedMovieIds = recommendedMoviesProj.map { it.uniqueId.toInt() }
            val fullMoviesMap = try {
                vodRepository.getStreamsByIds(recommendedMovieIds).associateBy { it.streamId }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyMap()
            }
            val recommendedMovies = recommendedMovieIds.mapNotNull { fullMoviesMap[it] }

            val recommendedSeriesIds = recommendedSeriesProj.map { it.uniqueId.toInt() }
            val fullSeriesMap = try {
                seriesRepository.getStreamsByIds(recommendedSeriesIds).associateBy { it.seriesId }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyMap()
            }
            val recommendedSeries = recommendedSeriesIds.mapNotNull { fullSeriesMap[it] }

            val result = RecommendationResult(recommendedMovies, recommendedSeries)
            
            // 7. Update cache
            updateCache(currentProfileId, currentTimeMs, generation, result)
            
            com.cstv.app.di.IptvLog.d("RECO", "Recommendations generated: ${result.movies.size} movies, ${result.series.size} series.")
            com.cstv.app.di.IptvLog.d(
                "PERF",
                "RECO calcul complet en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
            )
            return result
    }

    private suspend fun updateCache(
        profileId: Int,
        timeMs: Long,
        generation: Long,
        result: RecommendationResult
    ) {
        mutex.withLock {
            if (generation != cacheGeneration) return
            cachedProfileId = profileId
            cacheTimestamp = timeMs
            cachedResult = result
        }
    }

    /**
     * Second garde-fou du masquage de catégories : le calcul écarte déjà les
     * catégories masquées, mais son résultat est conservé 24 h. Un masquage
     * décidé entre-temps laissait donc la rangée proposer des médias d'une
     * catégorie masquée jusqu'à l'expiration du cache — l'invalidation
     * explicite (`HomeViewModel`, sur `categoryPreferenceRepository.changes`)
     * couvre le cas nominal, ce filtre à la lecture couvre tous les autres
     * appelants, présents et futurs, sans relancer les treize secondes de
     * calcul du moteur.
     */
    private suspend fun withoutHiddenCategories(
        result: RecommendationResult
    ): RecommendationResult {
        val hiddenMovies = getHiddenCategories(CategoryType.VOD)
        val hiddenSeries = getHiddenCategories(CategoryType.SERIES)
        if (hiddenMovies.isEmpty() && hiddenSeries.isEmpty()) return result
        return RecommendationResult(
            movies = result.movies.filter { it.categoryId !in hiddenMovies },
            series = result.series.filter { it.categoryId !in hiddenSeries }
        )
    }

    open suspend fun invalidateCache() {
        mutex.withLock {
            cacheGeneration++
            cachedProfileId = -1
            cachedResult = null
        }
        _invalidations.emit(Unit)
    }

    // Visible for existing tests.
    internal suspend fun clearCache() = invalidateCache()

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
