package com.cstv.app.domain.model

/** Identifiant de la pseudo-catégorie « Tout », commune aux trois catalogues. */
const val ALL_CATEGORIES_ID = "all"

/** Valeurs dérivées d'une liste de médias pour alimenter le panneau de filtres. */
data class CatalogFacets(
    val genres: List<String>,
    val yearRange: IntRange,
    val filteredCount: Int
) {
    companion object {
        /**
         * Facettes neutres, servies quand le panneau de filtres n'est pas
         * accessible — mode « Tout », où le champ de recherche, le bouton
         * filtres et `AdvancedSearchSheet` sont tous derrière
         * `isSpecificCategory`. Les calculer y reviendrait à parcourir le
         * catalogue entier pour une valeur que personne ne lit.
         */
        val NONE = CatalogFacets(
            genres = emptyList(),
            yearRange = CatalogFacetsBuilder.DEFAULT_YEAR_RANGE,
            filteredCount = 0
        )
    }
}

/**
 * Dérivations d'une liste de médias : genres disponibles, amplitude d'années et
 * nombre d'éléments retenus par le filtre courant.
 *
 * Extrait des ViewModels catalogue parce que le coût est proportionnel à la
 * taille du catalogue et non à celle de l'écran : sur l'onglet « Tout », c'est
 * l'intégralité des flux qui est parcourue, avec un `Regex.split` et deux
 * `lowercase()` par média ([GenreParser]). Exécuté sur le dispatcher principal
 * — ce que fait `viewModelScope.launch` par défaut — ce parcours retardait
 * l'affichage de l'onglet de plusieurs centaines de millisecondes à chaque
 * émission de Room. Les appelants doivent donc l'invoquer sur un dispatcher de
 * calcul.
 *
 * Objet pur, sans dépendance Android ni coroutine, pour rester testable en JVM.
 */
object CatalogFacetsBuilder {

    /** Amplitude servie quand aucun média de la sélection ne porte d'année. */
    val DEFAULT_YEAR_RANGE = 1980..2025

    fun <T> build(
        items: List<T>,
        genreOf: (T) -> String?,
        yearOf: (T) -> Int?,
        ratingOf: (T) -> String?,
        filter: AdvancedSearchFilter
    ): CatalogFacets {
        val genres = LinkedHashMap<String, String>()
        var minYear = Int.MAX_VALUE
        var maxYear = Int.MIN_VALUE
        var matching = 0
        // Un seul parcours : les trois dérivations partagent la lecture des
        // champs, la plus coûteuse étant le découpage des genres.
        for (item in items) {
            val rawGenre = genreOf(item)
            for (genre in GenreParser.parseGenres(rawGenre)) {
                // `putIfAbsent` demanderait l'API 24 ; minSdk du projet : 21.
                val key = GenreParser.normalize(genre)
                if (!genres.containsKey(key)) genres[key] = genre
            }
            val year = yearOf(item)
            if (year != null) {
                if (year < minYear) minYear = year
                if (year > maxYear) maxYear = year
            }
            if (CatalogFilterMatcher.matchesContent(ratingOf(item), year, rawGenre, filter)) {
                matching++
            }
        }
        return CatalogFacets(
            genres = genres.values.sorted(),
            yearRange = if (minYear > maxYear) DEFAULT_YEAR_RANGE else minYear..maxYear,
            filteredCount = matching
        )
    }
}
