package com.cstv.app.domain.model

enum class PendingCloudOp { NONE, UPLOAD, DELETE, DELETE_IF_MATCH }

data class IptvCloudBackupState(
    val accountId: String? = null,
    val consent: Boolean = false,
    val lastEtag: String? = null,
    val pendingOp: PendingCloudOp = PendingCloudOp.NONE,
    /** Credentials are retained only while an upload is pending. */
    val credentials: Credentials? = null
)

sealed interface IptvBackupOutcome {
    data object Skipped : IptvBackupOutcome
    data object Saved : IptvBackupOutcome
    data object Deleted : IptvBackupOutcome
    data object Deferred : IptvBackupOutcome
}

sealed interface IptvRestoreOutcome {
    data object Absent : IptvRestoreOutcome
    data object Unavailable : IptvRestoreOutcome
    data object Unreadable : IptvRestoreOutcome
    data class Restored(val credentials: Credentials) : IptvRestoreOutcome
}
