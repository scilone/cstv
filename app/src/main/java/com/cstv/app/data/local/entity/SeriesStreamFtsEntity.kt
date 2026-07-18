package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

// Phase 40 : voir LiveStreamFtsEntity pour le détail du pattern. rowid
// backfillé sur SeriesStreamEntity.seriesId.
@Fts4
@Entity(tableName = "series_streams_fts")
data class SeriesStreamFtsEntity(
    val name: String,
    val actors: String?,
    val director: String?,
    val genre: String?,
    val categoryId: String
)
