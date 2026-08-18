package com.cstv.app.domain.model

object RecommendationEngine {
    
    // Balanced weighted scoring criteria (Total: 1.00)
    private const val WEIGHT_GENRE = 0.35
    private const val WEIGHT_CATEGORY = 0.25
    private const val WEIGHT_ACTORS = 0.10
    private const val WEIGHT_DIRECTOR = 0.10
    private const val WEIGHT_RATING = 0.15
    private const val WEIGHT_FRESHNESS = 0.05

    const val MAX_RECOMMENDATIONS = 100
    private const val FRESHNESS_DECAY_DAYS = 180.0
    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000L

    private val EXCLUDED_NAMES = setOf("inconnu", "n/a", "na", "unknown", "-", "")

    data class ProfileTaste(
        val genreWeights: Map<String, Double>,
        val categoryWeights: Map<String, Double>,
        val actorWeights: Map<String, Double>,
        val directorWeights: Map<String, Double>
    )

    data class TasteSignal(val item: RecommendableItem, val weight: Double)

    interface RecommendableItem {
        val uniqueId: String
        val genres: String?
        val categoryId: String
        val rating: String?
        val addedEpoch: String?
        val releaseYear: Int?
        val actors: String?
        val director: String?

        val parsedGenres: List<String>
            get() = GenreParser.parseGenres(genres).map { GenreParser.normalize(it) }
        val parsedActors: List<String>
            get() = parseNamesList(actors)
        val normalizedDirector: String?
            get() {
                val d = director?.trim()?.lowercase()
                return if (!d.isNullOrBlank() && d !in EXCLUDED_NAMES) d else null
            }
    }

    class RecommendableVod(
        override val uniqueId: String,
        override val genres: String?,
        override val categoryId: String,
        override val rating: String?,
        override val addedEpoch: String?,
        override val releaseYear: Int?,
        override val actors: String?,
        override val director: String?
    ) : RecommendableItem {
        override val parsedGenres: List<String> by lazy { GenreParser.parseGenres(genres).map { GenreParser.normalize(it) } }
        override val parsedActors: List<String> by lazy { parseNamesList(actors) }
        override val normalizedDirector: String? by lazy {
            val d = director?.trim()?.lowercase()
            if (!d.isNullOrBlank() && d !in EXCLUDED_NAMES) d else null
        }

        constructor(stream: VodStream) : this(
            uniqueId = stream.streamId.toString(),
            genres = stream.genre,
            categoryId = stream.categoryId,
            rating = stream.rating,
            addedEpoch = stream.added,
            releaseYear = stream.releaseYear,
            actors = stream.actors,
            director = stream.director
        )
    }

    class RecommendableSeries(
        override val uniqueId: String,
        override val genres: String?,
        override val categoryId: String,
        override val rating: String?,
        override val addedEpoch: String?,
        override val releaseYear: Int?,
        override val actors: String?,
        override val director: String?
    ) : RecommendableItem {
        override val parsedGenres: List<String> by lazy { GenreParser.parseGenres(genres).map { GenreParser.normalize(it) } }
        override val parsedActors: List<String> by lazy { parseNamesList(actors) }
        override val normalizedDirector: String? by lazy {
            val d = director?.trim()?.lowercase()
            if (!d.isNullOrBlank() && d !in EXCLUDED_NAMES) d else null
        }

        constructor(series: SeriesStream) : this(
            uniqueId = series.seriesId.toString(),
            genres = series.genre,
            categoryId = series.categoryId,
            rating = series.rating,
            addedEpoch = series.added,
            releaseYear = series.releaseYear,
            actors = series.actors,
            director = series.director
        )
    }

    /**
     * Construit le profil de goûts de l'utilisateur à partir des médias distincts qu'il a regardés.
     */
    fun buildProfileTaste(watchedItems: List<RecommendableItem>): ProfileTaste =
        buildWeightedProfileTaste(watchedItems.map { TasteSignal(it, 1.0) })

    fun buildWeightedProfileTaste(signals: List<TasteSignal>): ProfileTaste {
        if (signals.isEmpty()) return ProfileTaste(emptyMap(), emptyMap(), emptyMap(), emptyMap())

        val genreCounts = mutableMapOf<String, Double>()
        val categoryCounts = mutableMapOf<String, Double>()
        val actorCounts = mutableMapOf<String, Double>()
        val directorCounts = mutableMapOf<String, Double>()

        var totalItemsWithGenres = 0.0
        var totalItemsWithActors = 0.0
        var totalItemsWithDirector = 0.0
        val totalItems = signals.sumOf { it.weight }

        for ((item, weight) in signals) {
            // Count category
            categoryCounts[item.categoryId] = (categoryCounts[item.categoryId] ?: 0.0) + weight

            // Count genres
            val itemGenres = item.parsedGenres
            if (itemGenres.isNotEmpty()) {
                totalItemsWithGenres += weight
                for (g in itemGenres) {
                    val normalized = GenreParser.normalize(g)
                    if (normalized.isNotBlank()) {
                        genreCounts[normalized] = (genreCounts[normalized] ?: 0.0) + weight
                    }
                }
            }

            // Count actors (parsing comma-separated values)
            val itemActors = item.parsedActors
            if (itemActors.isNotEmpty()) {
                totalItemsWithActors += weight
                for (actor in itemActors) {
                    actorCounts[actor] = (actorCounts[actor] ?: 0.0) + weight
                }
            }

            // Count director
            val director = item.normalizedDirector
            if (director != null) {
                totalItemsWithDirector += weight
                directorCounts[director] = (directorCounts[director] ?: 0.0) + weight
            }
        }

        // Calculate relative weights (0.0 to 1.0)
        val categoryWeights = categoryCounts.mapValues { it.value / totalItems }

        val genreWeights = genreCounts.mapValues { 
            if (totalItemsWithGenres > 0) it.value / totalItemsWithGenres else 0.0
        }

        val actorWeights = actorCounts.mapValues {
            if (totalItemsWithActors > 0) it.value / totalItemsWithActors else 0.0
        }

        val directorWeights = directorCounts.mapValues {
            if (totalItemsWithDirector > 0) it.value / totalItemsWithDirector else 0.0
        }

        return ProfileTaste(genreWeights, categoryWeights, actorWeights, directorWeights)
    }

    /**
     * Score une liste de candidats par rapport au profil de goûts et retient le top 100.
     * @param candidates La liste des médias disponibles dans le catalogue.
     * @param taste Le profil de goûts calculé.
     * @param currentTimeMs Le timestamp actuel pour calculer la fraîcheur.
     * @param excludeIds Ensemble des identifiants (uniqueId) déjà regardés, à exclure.
     */
    fun <T : RecommendableItem> getTopRecommendations(
        candidates: List<T>,
        taste: ProfileTaste,
        currentTimeMs: Long,
        excludeIds: Set<String>
    ): List<T> {
        val scoredItems = mutableListOf<Pair<T, Double>>()

        for (candidate in candidates) {
            if (excludeIds.contains(candidate.uniqueId)) continue

            val score = scoreCandidate(candidate, taste, currentTimeMs)
            scoredItems.add(Pair(candidate, score))
        }

        // Départage stable (score égal → note puis added décroissants)
        return scoredItems.sortedWith(
            Comparator { a, b ->
                val scoreCompare = b.second.compareTo(a.second)
                if (scoreCompare != 0) return@Comparator scoreCompare

                // Same score -> compare rating
                val ratingA = parseRating(a.first.rating)
                val ratingB = parseRating(b.first.rating)
                val ratingCompare = ratingB.compareTo(ratingA)
                if (ratingCompare != 0) return@Comparator ratingCompare

                // Same score & rating -> compare added (freshness)
                val addedA = parseAddedEpoch(a.first.addedEpoch)
                val addedB = parseAddedEpoch(b.first.addedEpoch)
                addedB.compareTo(addedA)
            }
        ).take(MAX_RECOMMENDATIONS).map { it.first }
    }

    internal fun scoreCandidate(candidate: RecommendableItem, taste: ProfileTaste, currentTimeMs: Long): Double {
        // 1. Genre Score (0.0 to 1.0)
        var genreScore = 0.0
        val candidateGenres = candidate.parsedGenres
        if (candidateGenres.isNotEmpty()) {
            var sumWeights = 0.0
            for (normalized in candidateGenres) {
                sumWeights += taste.genreWeights[normalized] ?: 0.0
            }
            genreScore = sumWeights.coerceAtMost(1.0)
        }

        // 2. Category Score (0.0 to 1.0)
        val categoryScore = taste.categoryWeights[candidate.categoryId] ?: 0.0

        // 3. Actors Score (0.0 to 1.0)
        var actorsScore = 0.0
        val candidateActors = candidate.parsedActors
        if (candidateActors.isNotEmpty()) {
            var sumWeights = 0.0
            for (actor in candidateActors) {
                sumWeights += taste.actorWeights[actor] ?: 0.0
            }
            actorsScore = sumWeights.coerceAtMost(1.0)
        }

        // 4. Director Score (0.0 to 1.0)
        val director = candidate.normalizedDirector
        val directorScore = if (director != null) {
            taste.directorWeights[director] ?: 0.0
        } else {
            0.0
        }

        // 5. Rating Score (0.0 to 1.0)
        val ratingScore = (parseRating(candidate.rating) / 10.0).coerceIn(0.0, 1.0)

        // 6. Freshness Score (0.0 to 1.0)
        var freshnessScore = 0.0
        val addedEpoch = parseAddedEpoch(candidate.addedEpoch)
        if (addedEpoch > 0) {
            val ageMs = currentTimeMs - (addedEpoch * 1000L) // Assuming addedEpoch is in seconds
            val ageDays = (ageMs.toDouble() / MILLIS_PER_DAY).coerceAtLeast(0.0)
            
            // Linear decay from 1.0 (today) to 0.0 (at 180 days)
            freshnessScore = if (ageDays >= FRESHNESS_DECAY_DAYS) {
                0.0
            } else {
                (1.0 - (ageDays / FRESHNESS_DECAY_DAYS)).coerceIn(0.0, 1.0)
            }
        }

        // Compute final balanced weighted score (Total weight: 1.00)
        return (genreScore * WEIGHT_GENRE) +
               (categoryScore * WEIGHT_CATEGORY) +
               (actorsScore * WEIGHT_ACTORS) +
               (directorScore * WEIGHT_DIRECTOR) +
               (ratingScore * WEIGHT_RATING) +
               (freshnessScore * WEIGHT_FRESHNESS)
    }

    private fun parseNamesList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it !in EXCLUDED_NAMES }
    }

    private fun parseRating(rating: String?): Double {
        if (rating.isNullOrBlank()) return 5.0
        return rating.trim().toDoubleOrNull() ?: 5.0
    }

    private fun parseAddedEpoch(added: String?): Long {
        if (added.isNullOrBlank()) return 0L
        return added.trim().toLongOrNull() ?: 0L
    }
}
