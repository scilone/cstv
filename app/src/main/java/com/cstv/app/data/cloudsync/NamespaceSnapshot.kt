package com.cstv.app.data.cloudsync

import com.google.gson.JsonElement

data class NamespaceSnapshot(val schemaVersion: Int, val namespace: String, val objects: Map<String, JsonElement>)

sealed interface SnapshotEncodeResult { data class Success(val bytes: ByteArray) : SnapshotEncodeResult; data object TooLarge : SnapshotEncodeResult }
sealed interface SnapshotDecodeResult {
    data class Success(val snapshot: NamespaceSnapshot) : SnapshotDecodeResult
    data object Incompatible : SnapshotDecodeResult
    data object TooLarge : SnapshotDecodeResult
    data object Malformed : SnapshotDecodeResult
}
