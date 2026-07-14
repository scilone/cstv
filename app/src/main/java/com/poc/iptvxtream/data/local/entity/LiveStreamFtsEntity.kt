package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

// Phase 40 : table virtuelle FTS4 pour la recherche globale. Autonome (pas de
// contentEntity) : synchronisée à la main depuis LiveTvDao (upsertLiveFts /
// clearFtsByCategory / clearAllFts), en miroir des écritures sur live_streams.
// Le rowid implicite de cette table est backfillé sur LiveStreamEntity.streamId
// (voir Migrations.kt / LiveTvDao) pour permettre la jointure sans colonne dédiée.
@Fts4
@Entity(tableName = "live_streams_fts")
data class LiveStreamFtsEntity(
    val name: String,
    val categoryId: String
)
