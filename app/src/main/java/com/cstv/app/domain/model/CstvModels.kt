package com.cstv.app.domain.model

data class CstvSession(
    val token: String,
    val accountId: String?,
    val accountEmail: String?,
    val expiresAtMillis: Long
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

sealed interface CstvSessionState {
    data object Unknown : CstvSessionState
    data class SignedOut(val reason: SignedOutReason = SignedOutReason.NoSession) : CstvSessionState
    data class Active(val account: CstvAccount) : CstvSessionState
    data class Offline(val session: CstvSession) : CstvSessionState
    data class Blocked(val reason: CstvBlockedReason) : CstvSessionState
}

enum class SignedOutReason { NoSession, TokenExpired, SessionRejected }
enum class CstvBlockedReason { Disabled, Expired }

data class CstvAccount(
    val id: String,
    val email: String,
    val enabled: Boolean,
    val activeUntilMillis: Long,
    val profiles: List<CstvProfile>
)

data class CstvProfile(val id: String, val name: String, val avatarId: Int, val createdAtMillis: Long)

sealed interface CstvError {
    data object InvalidOtp : CstvError
    data object InvalidEmail : CstvError
    data object RateLimited : CstvError
    data object Unavailable : CstvError
    data object ServerError : CstvError
    data object Rejected : CstvError
    data object Disabled : CstvError
    data object Expired : CstvError
    data object LastProfile : CstvError
    data object ProfileLimit : CstvError
    data object StorageQuota : CstvError
    /** T19-R3: kept distinct from [StorageQuota] (`NAMESPACE_LIMIT_REACHED` vs
     *  `STORAGE_QUOTA_EXCEEDED`) so the terminal sync status can name the actual cause instead of
     *  collapsing both backend quota codes into one generic label. */
    data object NamespaceLimit : CstvError
    data object ProfileNotFound : CstvError
    data object EtagMismatch : CstvError
    data object PayloadTooLarge : CstvError
    data object PreconditionRequired : CstvError
    data object Incompatible : CstvError
    data object PlaybackLockHeld : CstvError
    data object PlaybackLockRevoked : CstvError
    data object Unknown : CstvError
}
