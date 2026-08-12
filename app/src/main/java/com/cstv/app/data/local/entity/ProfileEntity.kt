package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles", indices = [Index(value = ["remoteId"], unique = true)])
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatarId: Int,
    val createdAt: Long,
    /** UUID CSTV. Null means that this local profile has not been reconciled yet. */
    val remoteId: String? = null
)
