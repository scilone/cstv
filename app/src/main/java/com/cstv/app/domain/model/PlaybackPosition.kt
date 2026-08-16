package com.cstv.app.domain.model

data class PlaybackPosition(
    val streamId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastAccessedAt: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    // "movie" ou "episode" (jamais "series" : reflète `media_refs.kind`, passé
    // tel quel à `ViewingHistoryRepositoryImpl.removeFromContinueWatching` comme
    // paramètre `kind` de la suppression — le confondre avec la convention
    // "movie"/"series" de `FavoriteItem.type` a déjà cassé plusieurs filtres
    // silencieusement (B31).
    val type: String? = null,
    val containerExtension: String? = null,
    val seriesId: Int? = null,
    val episodeNum: Int? = null,
    val seasonNum: Int? = null,
    val plot: String? = null,
    val duration: String? = null,
    val releaseDate: String? = null,
    val categoryId: String? = null,
    val genre: String? = null
)
