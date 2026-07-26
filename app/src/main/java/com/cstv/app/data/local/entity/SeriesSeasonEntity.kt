package com.cstv.app.data.local.entity

import androidx.room.Entity

/**
 * Saison persistée lors de l'ouverture en ligne d'une fiche série. Peuplée à la
 * consultation et non par balayage : un `get_series_info` par série produirait
 * exactement le trafic que T4 supprime.
 */
@Entity(tableName = "series_seasons", primaryKeys = ["seriesId", "seasonNumber"])
data class SeriesSeasonEntity(
    val seriesId: Int,
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val cover: String? = null,
    val cachedAt: Long
)
