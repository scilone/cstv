package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

// Phase 40 : voir LiveStreamFtsEntity pour le détail du pattern. rowid
// backfillé sur VodStreamEntity.streamId.
@Fts4
@Entity(tableName = "vod_streams_fts")
data class VodStreamFtsEntity(
    val name: String,
    val actors: String?,
    val director: String?,
    val genre: String?,
    val categoryId: String
)
