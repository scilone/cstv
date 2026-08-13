package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_refs",
    indices = [Index(value = ["accountKey", "kind", "providerId"], unique = true)]
)
data class MediaRefEntity(
    @PrimaryKey(autoGenerate = true) val mediaUid: Long = 0,
    val accountKey: String,
    val kind: String,
    val providerId: Int
)
