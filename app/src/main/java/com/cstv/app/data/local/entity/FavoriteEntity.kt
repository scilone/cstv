package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * T20: pure state — a reference to a canonical [MediaRefEntity] plus the one business value that
 * belongs to this table. Title, cover and category are no longer duplicated here; screens resolve
 * them from the current catalogue via a join (see `FavoritesDao` projections).
 */
@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid"), Index(value = ["profileId", "addedAt"])],
)
data class FavoriteEntity(
    val profileId: Int,
    val mediaUid: Long,
    val addedAt: Long,
)
