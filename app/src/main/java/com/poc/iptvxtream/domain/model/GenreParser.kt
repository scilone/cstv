package com.poc.iptvxtream.domain.model

/**
 * Parsing défensif de la string `genre` Xtream (ex: "Action, Thriller",
 * "Action,Aventure", "Comédie"). Le champ est une chaîne libre remplie par le
 * panel : casse et espaces variables, séparateur virgule. Objet pur et
 * testable — aucune dépendance Android/Room.
 *
 * Le genre est enrichi en arrière-plan puis stocké dans `vod_streams.genre` /
 * `series_streams.genre` (voir Vod/SeriesRepositoryImpl). Quand le panel ne
 * fournit rien, l'enrichissement écrit "Inconnu" : ces valeurs de remplissage
 * sont exclues de la liste des filtres.
 */
object GenreParser {

    private val PLACEHOLDERS = setOf("inconnu", "n/a", "na", "unknown", "")

    /** Découpe la string genre en genres individuels nettoyés (casse préservée). */
    fun parseGenres(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in PLACEHOLDERS }
    }

    /** Clé de comparaison insensible à la casse et aux espaces de bord. */
    fun normalize(genre: String): String = genre.trim().lowercase()

    /**
     * Vrai si [storedRaw] contient [selectedGenre] après split par virgule +
     * trim, en comparaison insensible à la casse (token-à-token exact, pas de
     * sous-chaîne : "War" ne matche pas "Warrior").
     */
    fun matches(storedRaw: String?, selectedGenre: String): Boolean {
        val target = normalize(selectedGenre)
        if (target.isEmpty()) return false
        return parseGenres(storedRaw).any { normalize(it) == target }
    }

    /**
     * Agrège des strings genre brutes en genres distincts, triés
     * alphabétiquement et dédupliqués sans tenir compte de la casse (première
     * casse rencontrée conservée).
     */
    fun distinctGenres(rawGenres: List<String?>): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (raw in rawGenres) {
            for (g in parseGenres(raw)) {
                val key = normalize(g)
                if (key !in seen) seen[key] = g
            }
        }
        return seen.values.sortedBy { normalize(it) }
    }
}
