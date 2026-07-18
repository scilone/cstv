package com.cstv.app.domain.model

/**
 * Calcule l'épisode à enchaîner après [current] (Phase 59 — lecture auto de
 * l'épisode suivant), à partir de la carte des épisodes d'une série
 * (`SeriesDetails.episodes`, clé = numéro de saison).
 *
 * Règle (validée avec l'utilisateur) :
 * 1. Même saison : l'épisode dont `episodeNum == current.episodeNum + 1`, s'il
 *    existe dans la map.
 * 2. Sinon, première saison strictement supérieure présente dans la map (la
 *    plus petite parmi celles restantes, pour être robuste à un éventuel trou
 *    de numérotation) → son épisode de plus petit `episodeNum`.
 * 3. Sinon `null` : fin de la série, aucun enchaînement.
 *
 * Retourne `null` si la map est vide (cas d'une reprise depuis l'accueil où le
 * détail complet n'est pas propagé) : ni autoplay ni bouton dans ce cas.
 */
fun computeNextEpisode(
    episodes: Map<Int, List<SeriesEpisode>>,
    current: SeriesEpisode
): SeriesEpisode? {
    episodes[current.seasonNum]
        ?.firstOrNull { it.episodeNum == current.episodeNum + 1 }
        ?.let { return it }

    val nextSeason = episodes.keys.filter { it > current.seasonNum }.minOrNull()
        ?: return null
    return episodes[nextSeason]?.minByOrNull { it.episodeNum }
}

/**
 * Calcule l'épisode précédent [current] : symétrique de [computeNextEpisode].
 *
 * 1. Même saison : l'épisode `episodeNum - 1`, s'il existe.
 * 2. Sinon, la saison précédente présente la plus proche (la plus grande
 *    parmi celles inférieures, robuste à un trou de numérotation) → son
 *    épisode de plus grand `episodeNum`.
 * 3. Sinon `null` : premier épisode de la série, rien avant.
 */
fun computePreviousEpisode(
    episodes: Map<Int, List<SeriesEpisode>>,
    current: SeriesEpisode
): SeriesEpisode? {
    episodes[current.seasonNum]
        ?.firstOrNull { it.episodeNum == current.episodeNum - 1 }
        ?.let { return it }

    val previousSeason = episodes.keys.filter { it < current.seasonNum }.maxOrNull()
        ?: return null
    return episodes[previousSeason]?.maxByOrNull { it.episodeNum }
}
