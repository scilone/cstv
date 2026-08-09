package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.RecommendationEngine
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.MediaRatingRepository
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import com.cstv.app.data.local.storage.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
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
 */
private val recommendationDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "reco-engine").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }
}.asCoroutineDispatcher()

@Singleton
class GetRecommendationsUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val profileManager: ProfileManager,
    private val mediaRatingRepository: MediaRatingRepository,
    @Named("applicationScope") private val applicationScope: CoroutineScope
) {
    data class RecommendationResult(
        val movies: List<VodStream>,
        val series: List<SeriesStream>
    )

    private val mutex = Mutex()

    // Ecrits depuis le fil du moteur, lus depuis les appelants : `@Volatile`
    // garantit la visibilite sans elargir la portee du verrou.
    @Volatile private var cachedResult: RecommendationResult? = null
    @Volatile private var cachedProfileId: Int = -1
    @Volatile private var cacheTimestamp: Long = 0L

    /** Calcul en cours, partage par tous les appelants (voir [invoke]). */
    private var inFlight: Deferred<RecommendationResult>? = null
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

        val computation = mutex.withLock {
            val cached = cachedResult
            if (cached != null &&
                cachedProfileId == currentProfileId &&
                (currentTimeMs - cacheTimestamp) < TTL_MILLIS) {
                com.cstv.app.di.IptvLog.d("RECO", "Serving recommendations from cache for profile $currentProfileId")
                return withoutHiddenCategories(cached)
            }

            inFlight?.takeIf { it.isActive }
                ?: applicationScope
                    .async(recommendationDispatcher) { compute(currentProfileId, currentTimeMs) }
                    .also { inFlight = it }
        }
        return computation.await()
    }

    private suspend fun compute(
        currentProfileId: Int,
        currentTimeMs: Long
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
            val seriesHistoryIds = allHistory.filter { it.type == "series" && it.seriesId != null }.map { it.seriesId.toString() }.toSet()

            val ratings = try { mediaRatingRepository.getAllRatings() } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            val likedMovieIds = ratings.filter { it.mediaType == RatedMediaType.MOVIE && it.value == MediaRatingValue.LIKE }.map { it.mediaId.toString() }.toSet()
            val likedSeriesIds = ratings.filter { it.mediaType == RatedMediaType.SERIES && it.value == MediaRatingValue.LIKE }.map { it.mediaId.toString() }.toSet()
            val dislikedMovieIds = ratings.filter { it.mediaType == RatedMediaType.MOVIE && it.value == MediaRatingValue.DISLIKE }.map { it.mediaId.toString() }.toSet()
            val dislikedSeriesIds = ratings.filter { it.mediaType == RatedMediaType.SERIES && it.value == MediaRatingValue.DISLIKE }.map { it.mediaId.toString() }.toSet()
            val positiveMovieHistoryIds = movieHistoryIds - dislikedMovieIds - likedMovieIds
            val positiveSeriesHistoryIds = seriesHistoryIds - dislikedSeriesIds - likedSeriesIds

            // Calculate distinct watched items count
            val totalWatchedCount = movieHistoryIds.size + seriesHistoryIds.size

            // Cold start protection: require at least 3 distinct items
            if (totalWatchedCount < 3 && likedMovieIds.isEmpty() && likedSeriesIds.isEmpty()) {
                com.cstv.app.di.IptvLog.d("RECO", "Cold start: Not enough history ($totalWatchedCount < 3) for profile $currentProfileId. Returning empty.")
                val emptyResult = RecommendationResult(emptyList(), emptyList())
                updateCache(currentProfileId, currentTimeMs, emptyResult)
                return emptyResult
            }

            // 2. Fetch full catalog
            val catalogAt = System.nanoTime()
            val allMovies = try {
                vodRepository.getCachedVodStreams("all")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }

            val allSeries = try {
                seriesRepository.getCachedSeriesStreams("all")
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
            
            for (movie in allMovies) {
                when (movie.streamId.toString()) {
                    in likedMovieIds -> tasteSignals.add(RecommendationEngine.TasteSignal(RecommendationEngine.RecommendableVod(movie), 3.0))
                    in positiveMovieHistoryIds -> tasteSignals.add(RecommendationEngine.TasteSignal(RecommendationEngine.RecommendableVod(movie), 1.0))
                }
            }
            for (series in allSeries) {
                when (series.seriesId.toString()) {
                    in likedSeriesIds -> tasteSignals.add(RecommendationEngine.TasteSignal(RecommendationEngine.RecommendableSeries(series), 3.0))
                    in positiveSeriesHistoryIds -> tasteSignals.add(RecommendationEngine.TasteSignal(RecommendationEngine.RecommendableSeries(series), 1.0))
                }
            }

            // 5. Calculate taste profile
            val profileTaste = RecommendationEngine.buildWeightedProfileTaste(tasteSignals)
            com.cstv.app.di.IptvLog.d("RECO", "Profile Taste built. Top Genres: ${profileTaste.genreWeights.entries.sortedByDescending { it.value }.take(3)}")

            // 6. Score and get top 100 for each type
            val recommendableMovies = allowedMovies.map { RecommendationEngine.RecommendableVod(it) }
            val recommendableSeries = allowedSeries.map { RecommendationEngine.RecommendableSeries(it) }

            val recommendedMovies = RecommendationEngine.getTopRecommendations(
                candidates = recommendableMovies,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = movieHistoryIds + likedMovieIds + dislikedMovieIds
            ).map { it.stream }

            val recommendedSeries = RecommendationEngine.getTopRecommendations(
                candidates = recommendableSeries,
                taste = profileTaste,
                currentTimeMs = currentTimeMs,
                excludeIds = seriesHistoryIds + likedSeriesIds + dislikedSeriesIds
            ).map { it.series }

            val result = RecommendationResult(recommendedMovies, recommendedSeries)
            
            // 7. Update cache
            updateCache(currentProfileId, currentTimeMs, result)
            
            com.cstv.app.di.IptvLog.d("RECO", "Recommendations generated: ${result.movies.size} movies, ${result.series.size} series.")
            com.cstv.app.di.IptvLog.d(
                "PERF",
                "RECO calcul complet en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
            )
            return result
    }

    private fun updateCache(profileId: Int, timeMs: Long, result: RecommendationResult) {
        cachedProfileId = profileId
        cacheTimestamp = timeMs
        cachedResult = result
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

    suspend fun invalidateCache() {
        mutex.withLock {
            cachedProfileId = -1
            cachedResult = null
            inFlight = null
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
