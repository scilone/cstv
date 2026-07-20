package com.cstv.app.domain.model

object RecommendationEngine {
    
    // Poids relatifs des critères de scoring
    private const val WEIGHT_GENRE = 0.40
    private const val WEIGHT_CATEGORY = 0.20
    private const val WEIGHT_RATING = 0.25
    private const val WEIGHT_FRESHNESS = 0.15

    const val MAX_RECOMMENDATIONS = 100
    private const val FRESHNESS_DECAY_DAYS = 180.0
    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000L

    data class ProfileTaste(
        val genreWeights: Map<String, Double>,
        val categoryWeights: Map<String, Double>
    )

    interface RecommendableItem {
        val uniqueId: String
        val genres: String?
        val categoryId: String
        val rating: String?
        val addedEpoch: String?
        val releaseYear: Int?
    }

    class RecommendableVod(val stream: VodStream) : RecommendableItem {
        override val uniqueId: String = stream.streamId.toString()
        override val genres: String? = stream.genre
        override val categoryId: String = stream.categoryId
        override val rating: String? = stream.rating
        override val addedEpoch: String? = stream.added
        override val releaseYear: Int? = stream.releaseYear
    }

    class RecommendableSeries(val series: SeriesStream) : RecommendableItem {
        override val uniqueId: String = series.seriesId.toString()
        override val genres: String? = series.genre
        override val categoryId: String = series.categoryId
        override val rating: String? = series.rating
        override val addedEpoch: String? = series.added
        override val releaseYear: Int? = series.releaseYear
    }

    /**
     * Construit le profil de goûts de l'utilisateur à partir des médias distincts qu'il a regardés.
     */
    fun buildProfileTaste(watchedItems: List<RecommendableItem>): ProfileTaste {
        if (watchedItems.isEmpty()) return ProfileTaste(emptyMap(), emptyMap())

        val genreCounts = mutableMapOf<String, Int>()
        val categoryCounts = mutableMapOf<String, Int>()

        var totalItemsWithGenres = 0
        var totalItems = watchedItems.size

        for (item in watchedItems) {
            // Count category
            categoryCounts[item.categoryId] = categoryCounts.getOrDefault(item.categoryId, 0) + 1

            // Count genres
            val itemGenres = GenreParser.parseGenres(item.genres)
            if (itemGenres.isNotEmpty()) {
                totalItemsWithGenres++
                for (g in itemGenres) {
                    val normalized = GenreParser.normalize(g)
                    if (normalized.isNotBlank()) {
                        genreCounts[normalized] = genreCounts.getOrDefault(normalized, 0) + 1
                    }
                }
            }
        }

        // Calculate relative weights (0.0 to 1.0)
        // For categories, it's straight count / total_items
        val categoryWeights = categoryCounts.mapValues { it.value.toDouble() / totalItems }

        // For genres, a single movie can have multiple genres, so we calculate
        // the frequency of a genre across the total number of items that HAD genres.
        // e.g., If I watched 10 movies with genres, and 8 were Westerns, Western weight = 0.8
        val genreWeights = genreCounts.mapValues { 
            if (totalItemsWithGenres > 0) it.value.toDouble() / totalItemsWithGenres else 0.0 
        }

        return ProfileTaste(genreWeights, categoryWeights)
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
        val candidateGenres = GenreParser.parseGenres(candidate.genres)
        if (candidateGenres.isNotEmpty()) {
            var sumWeights = 0.0
            for (g in candidateGenres) {
                val normalized = GenreParser.normalize(g)
                if (normalized.isNotBlank()) {
                    sumWeights += taste.genreWeights.getOrDefault(normalized, 0.0)
                }
            }
            genreScore = sumWeights.coerceAtMost(1.0)
        }

        // 2. Category Score (0.0 to 1.0)
        val categoryScore = taste.categoryWeights.getOrDefault(candidate.categoryId, 0.0)

        // 3. Rating Score (0.0 to 1.0)
        val ratingScore = (parseRating(candidate.rating) / 10.0).coerceIn(0.0, 1.0)

        // 4. Freshness Score (0.0 to 1.0)
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

        // Compute final weighted score
        return (genreScore * WEIGHT_GENRE) +
               (categoryScore * WEIGHT_CATEGORY) +
               (ratingScore * WEIGHT_RATING) +
               (freshnessScore * WEIGHT_FRESHNESS)
    }

    private fun parseRating(rating: String?): Double {
        if (rating.isNullOrBlank()) return 0.0
        return rating.trim().toDoubleOrNull() ?: 0.0
    }

    private fun parseAddedEpoch(added: String?): Long {
        if (added.isNullOrBlank()) return 0L
        return added.trim().toLongOrNull() ?: 0L
    }
}
