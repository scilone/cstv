package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** T20: pure state — name, icon, category and channel number come from the current catalogue. */
@Entity(
    tableName = "recently_watched_live",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid"), Index(value = ["profileId", "watchedAt"])],
)
data class RecentlyWatchedLiveEntity(
    val profileId: Int,
    val mediaUid: Long,
    val watchedAt: Long,
)
