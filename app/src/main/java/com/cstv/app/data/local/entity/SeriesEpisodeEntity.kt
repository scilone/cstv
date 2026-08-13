package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Épisode persisté avec sa saison. Table plutôt que blob JSON : les épisodes sont joints aux
 * positions de lecture (`media_refs(kind = 'episode').providerId` = [episodeId]) et la navigation
 * « épisode suivant » doit rester requêtable.
 *
 * T20 : clé étrangère vers `series_streams(seriesId)` en cascade, cf. `SeriesSeasonEntity`.
 * L'index composite `(seriesId, seasonNum)` couvre déjà `seriesId` en colonne de tête, donc sert
 * aussi l'index requis par la clé étrangère.
 */
@Entity(
    tableName = "series_episodes",
    foreignKeys = [
        ForeignKey(entity = SeriesStreamEntity::class, parentColumns = ["seriesId"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
    ],
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
