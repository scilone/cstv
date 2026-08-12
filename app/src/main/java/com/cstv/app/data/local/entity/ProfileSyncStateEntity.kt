package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** Last accepted cloud version for one profile namespace. Payload stays opaque to Room. */
@Entity(
    tableName = "profile_sync_state",
    primaryKeys = ["profileId", "namespace"],
    indices = [Index(value = ["profileId"])]
)
data class ProfileSyncStateEntity(
    val profileId: Int,
    val namespace: String,
    val etag: String? = null,
    val schemaVersion: Int = 1,
    val baseSnapshot: ByteArray? = null,
    val pending: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val failureCode: String? = null,
    val retryCount: Int = 0
)
