package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Fenêtre EPG par chaîne. La clé primaire composite remplace l'ancienne clé
 * `streamId` seule, qui ne mémorisait qu'un unique programme et rendait tout
 * EPG hors ligne impossible.
 */
@Entity(
    tableName = "epg_cache",
    primaryKeys = ["streamId", "startTimestamp"],
    indices = [Index(value = ["streamId", "endTimestamp"])]
)
data class EpgCacheEntity(
    val streamId: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val title: String,
    val description: String?,
    val cachedAt: Long
)
