package com.cstv.app.domain.model

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

    // Valeur de remplissage complète à écarter avant tout split ("N/A" doit être
    // reconnu ici car le split sur "/" le casserait en "N" + "A").
    private val WHOLE_PLACEHOLDERS = setOf("inconnu", "n/a", "na", "unknown", "-", "")
    private val TOKEN_PLACEHOLDERS = setOf("inconnu", "na", "unknown", "-", "")

    // Séparateurs rencontrés dans les strings Xtream : virgule ("Action, Thriller")
    // et slash ("Action/Aventure").
    private val SEPARATORS = Regex("[,/]")

    /** Découpe la string genre en genres individuels nettoyés (casse préservée). */
    fun parseGenres(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        if (raw.trim().lowercase() in WHOLE_PLACEHOLDERS) return emptyList()
        return raw.split(SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in TOKEN_PLACEHOLDERS }
    }

    /** Clé de comparaison insensible à la casse et aux espaces de bord. */
    fun normalize(genre: String): String = genre.trim().lowercase()

    /**
     * Vrai si [storedRaw] contient [selectedGenre] après split (virgule/slash) +
     * trim, en comparaison insensible à la casse (token-à-token exact, pas de
     * sous-chaîne : "War" ne matche pas "Warrior").
     */
    fun matches(storedRaw: String?, selectedGenre: String): Boolean {
        val target = normalize(selectedGenre)
        if (target.isEmpty()) return false
        return parseGenres(storedRaw).any { normalize(it) == target }
    }

    /** Nombre de genres communs (insensible à la casse) entre deux strings brutes. */
    fun sharedGenreCount(rawA: String?, rawB: String?): Int {
        val a = parseGenres(rawA).map { normalize(it) }.toSet()
        if (a.isEmpty()) return 0
        return parseGenres(rawB).map { normalize(it) }.toSet().count { it in a }
    }
}
