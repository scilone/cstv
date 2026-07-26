package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Épisode persisté avec sa saison. Table plutôt que blob JSON : les épisodes
 * sont joints aux positions de lecture (`playback_positions.streamId` =
 * [episodeId]) et la navigation « épisode suivant » doit rester requêtable.
 */
@Entity(
    tableName = "series_episodes",
    indices = [Index(value = ["seriesId", "seasonNum"])]
)
data class SeriesEpisodeEntity(
    @PrimaryKey val episodeId: Int,
    val seriesId: Int,
    val seasonNum: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String,
    val plot: String? = null,
    val duration: String? = null,
    val releaseDate: String? = null,
    val movieImage: String? = null,
    val orderIndex: Int = 0,
    val cachedAt: Long
)
