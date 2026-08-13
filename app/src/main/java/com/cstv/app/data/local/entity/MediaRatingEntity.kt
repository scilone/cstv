package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "media_ratings",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid")],
)
data class MediaRatingEntity(
    val profileId: Int,
    val mediaUid: Long,
    val value: Int,
)
