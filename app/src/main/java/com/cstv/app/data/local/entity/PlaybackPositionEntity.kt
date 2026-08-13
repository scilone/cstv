package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * T20: pure resume state. Title, cover, container extension and series/episode metadata are no
 * longer duplicated here; they are resolved from the catalogue via a join (see `VodDao`
 * projections). `mediaUid` disambiguates movie vs. episode by construction (its `MediaRefEntity`
 * carries `kind`), which the old bare `streamId` key could not.
 */
@Entity(
    tableName = "playback_positions",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid"), Index(value = ["profileId", "lastAccessedAt"])],
)
data class PlaybackPositionEntity(
    val profileId: Int,
    val mediaUid: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastAccessedAt: Long,
)
