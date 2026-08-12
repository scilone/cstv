package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.CstvSessionStateHolder
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.api.CstvSessionExpiredException
import com.cstv.app.data.remote.dto.*
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.util.TimeProvider
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CstvAuthRepositoryImpl @Inject constructor(
    private val api: CstvApiService,
    private val sessions: CstvSessionManager,
    private val stateHolder: CstvSessionStateHolder,
    private val errors: CstvErrorMapper,
    private val clock: TimeProvider,
    private val networkMonitor: NetworkMonitor,
    private val profileRepository: ProfileRepository,
    private val profileCloudReconciler: ProfileCloudReconciler
) : CstvAuthRepository {
    override val sessionState: StateFlow<CstvSessionState> = stateHolder.state
    override suspend fun requestOtp(email: String): Result<Unit> = try {
        val result = api.requestOtp(CstvOtpRequestDto(email.trim()))
        if (result.isSuccessful) Result.success(Unit) else Result.failure(CstvException(errors.from(result)))
    } catch (_: java.io.IOException) { Result.failure(CstvException(CstvError.Unavailable)) }

    override suspend fun verifyOtp(email: String, code: String): Result<CstvAccount> {
        return try {
        val response = api.verifyOtp(CstvOtpVerifyDto(email.trim(), code))
        val token = response.body()
        if (!response.isSuccessful || token == null) return Result.failure(CstvException(errors.from(response)))
        // Keep the previously known account id until /me can compare it with the
        // new identity. Clearing it here would make an account-switch purge impossible.
        val previous = sessions.get()
        sessions.save(CstvSession(token.accessToken, previous?.accountId, email.trim(), clock.nowMillis() + token.expiresIn * 1000))
        when (val state = resolveSession()) {
            is CstvSessionState.Active -> Result.success(state.account)
            is CstvSessionState.Blocked -> Result.failure(CstvException(if (state.reason == CstvBlockedReason.Expired) CstvError.Expired else CstvError.Disabled))
            is CstvSessionState.Offline -> Result.success(CstvAccount(state.session.accountId.orEmpty(), state.session.accountEmail.orEmpty(), true, state.session.expiresAtMillis, emptyList()))
            else -> Result.failure(CstvException(CstvError.Unavailable))
        }
        } catch (_: java.io.IOException) { Result.failure(CstvException(CstvError.Unavailable)) }
    }

    override suspend fun resolveSession(): CstvSessionState {
        val session = sessions.get() ?: return CstvSessionState.SignedOut().also(stateHolder::publish)
        if (session.isExpired(clock.nowMillis())) return CstvSessionState.SignedOut(SignedOutReason.TokenExpired).also(stateHolder::publish)
        if (!networkMonitor.isCurrentlyOnline()) return CstvSessionState.Offline(session).also(stateHolder::publish)
        return try {
            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                val account = response.body()!!.toDomain()
                if (session.accountId != null && session.accountId != account.id) {
                    // The gate remains unresolved until the previous account's Room data is gone.
                    profileRepository.purgeAllProfiles()
                }
                sessions.setAccountActiveUntil(account.activeUntilMillis)
                sessions.setLastMeSuccessAt(clock.nowMillis())
                // The backend account validity is the authority for a CSTV
                // session. `expiresIn` only covers the short interval before
                // `/v1/me` can be resolved after OTP verification.
                sessions.save(
                    session.copy(
                        accountId = account.id,
                        accountEmail = account.email,
                        expiresAtMillis = account.activeUntilMillis
                    )
                )
                profileCloudReconciler.reconcile(account).getOrElse { throw it }
                CstvSessionState.Active(account).also(stateHolder::publish)
            } else {
                when (val error = errors.from(response)) {
                    CstvError.Disabled -> CstvSessionState.Blocked(CstvBlockedReason.Disabled)
                    CstvError.Expired -> CstvSessionState.Blocked(CstvBlockedReason.Expired)
                    CstvError.Rejected -> CstvSessionState.SignedOut(SignedOutReason.SessionRejected)
                    else -> CstvSessionState.Offline(session)
                }.also(stateHolder::publish)
            }
        } catch (_: CstvSessionExpiredException) { CstvSessionState.SignedOut(SignedOutReason.TokenExpired).also(stateHolder::publish) }
        catch (_: java.io.IOException) { CstvSessionState.Offline(session).also(stateHolder::publish) }
        catch (_: Exception) { CstvSessionState.Offline(session).also(stateHolder::publish) }
    }

    override fun signOut() { sessions.clearSession(); stateHolder.publish(CstvSessionState.SignedOut()) }
    override fun storedEmail(): String? = sessions.storedEmail()
    private fun CstvAccountDto.toDomain() = CstvAccount(id, email, enabled, CstvTimestampParser.parseMillis(activeUntil), profiles.map { CstvProfile(it.id, it.name, it.avatarId, CstvTimestampParser.parseMillis(it.createdAt)) })
}

class CstvException(val cstvError: CstvError) : Exception(cstvError.toString())
