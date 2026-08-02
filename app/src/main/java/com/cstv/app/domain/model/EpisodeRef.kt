package com.cstv.app.domain.model

/** Référence (saison, épisode) comparée par ordre lexicographique — voir [NewEpisodeDetector]. */
data class EpisodeRef(val season: Int, val episode: Int) : Comparable<EpisodeRef> {
    override fun compareTo(other: EpisodeRef): Int =
        compareValuesBy(this, other, EpisodeRef::season, EpisodeRef::episode)
}
