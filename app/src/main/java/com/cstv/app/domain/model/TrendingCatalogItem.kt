package com.cstv.app.domain.model

/**
 * Une tendance TMDB effectivement rapprochée d'un média présent dans le
 * catalogue local. Porte le [TrendingTitle] (poster/titre TMDB pour l'affichage)
 * et **exactement un** flux catalogue cible pour l'ouverture du détail :
 * [vodStream] si film, [seriesStream] si série (l'autre est `null`).
 */
data class TrendingCatalogItem(
    val trending: TrendingTitle,
    val vodStream: VodStream?,
    val seriesStream: SeriesStream?
)
