package com.cstv.app.domain.model

data class SeriesDetails(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val seasons: List<SeriesSeason>,
    val episodes: Map<Int, List<SeriesEpisode>>,
    val director: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val actors: String? = null,
    /**
     * Fiche reconstruite depuis le catalogue local parce que la série n'a
     * jamais été ouverte en ligne ou que le panel a refusé ses métadonnées :
     * les saisons et épisodes peuvent manquer, ce ne sont pas des valeurs
     * fournies par le serveur.
     */
    val isMetadataIncomplete: Boolean = false
)
