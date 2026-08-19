package com.cstv.app.data.local.entity

import androidx.room.Entity

/** Classification T22 mémorisée par média catalogue. */
@Entity(tableName = "content_classifications", primaryKeys = ["kind", "providerId"])
data class ContentClassificationEntity(
    val kind: String,
    val providerId: Int,
    val title: String,
    val year: Int?,
    val ageRating: Int?,
    val resolvedAt: Long,
)
