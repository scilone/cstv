package com.cstv.app.domain.model

data class TrendingTitle(
    /** Opaque backend identity: only persisted or sent back to `/videos`. */
    val canonicalId: String?,
    val title: String,
    val isMovie: Boolean,
    val year: Int?,
    val posterUrl: String?,
    /**
     * Visuel paysage TMDB (`backdrop_path`). Nullable avec valeur par défaut :
     * les entrées déjà présentes dans le cache persistant (sérialisé en JSON)
     * ont été écrites avant l'ajout du champ et se relisent donc à `null`.
     */
    val backdropUrl: String? = null
) {
    @Deprecated("Tests and legacy cache only; production receives canonicalId from CSTV")
    constructor(tmdbId: Int, title: String, isMovie: Boolean, year: Int?, posterUrl: String?, backdropUrl: String? = null) :
        this(if (isMovie) "movie:$tmdbId" else "series:$tmdbId", title, isMovie, year, posterUrl, backdropUrl)
    /**
     * URL à utiliser dans un cadre large (Hero Card, carrousel) : le poster
     * TMDB est un portrait, son recadrage en 16/9 coupe systématiquement le
     * sujet. On préfère donc le backdrop quand il existe.
     */
    fun landscapeImageUrl(): String? = backdropUrl?.takeIf { it.isNotBlank() }
        ?: posterUrl?.takeIf { it.isNotBlank() }
}
