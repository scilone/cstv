package com.cstv.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "media_ratings", primaryKeys = ["profileId", "mediaType", "mediaId"])
data class MediaRatingEntity(
    val profileId: Int,
    val mediaType: String,
    val mediaId: Int,
    val value: Int
)
