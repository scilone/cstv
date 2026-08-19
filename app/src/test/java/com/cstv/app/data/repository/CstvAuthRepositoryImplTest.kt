package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.CstvSessionStateHolder
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.api.CstvSessionExpiredException
import com.cstv.app.data.remote.dto.CstvAccessTokenDto
import com.cstv.app.data.remote.dto.CstvAccountDto
import com.cstv.app.data.remote.dto.CstvProfileDto
import com.cstv.app.data.remote.dto.CstvOtpVerifyDto
import com.cstv.app.domain.model.CstvBlockedReason
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.model.CstvTimestampParser
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.util.TimeProvider
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.IOException

class CstvAuthRepositoryImplTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var api: CstvApiService
    @Mock private lateinit var sessions: CstvSessionManager
    @Mock private lateinit var clock: TimeProvider
    @Mock private lateinit var network: NetworkMonitor
    @Mock private lateinit var profiles: ProfileRepository
    @Mock private lateinit var profileDao: ProfileDao
    @Mock private lateinit var emptinessProbe: CloudProfileEmptinessProbe

    private lateinit var stateHolder: CstvSessionStateHolder
    private lateinit var repository: CstvAuthRepositoryImpl

    private fun accountDto(id: String = "account", activeUntil: String = "2099-12-31T23:59:59Z") =
        CstvAccountDto(id, "mail@example.test", true, activeUntil, emptyList())

    private fun errorResponse(code: Int, json: String) =
        Response.error<Any>(code, json.toResponseBody("application/json".toMediaType()))

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Réplique du repli reconnexion (cf. CatalogSyncManagerImplTest) : sans stub,
        // `network.isOnline` mocké renvoie `null` et la veille lancée dans `init` plante
        // sur un thread réel, dont l'exception non captée pollue les tests suivants.
        whenever(network.isOnline).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))
        stateHolder = CstvSessionStateHolder()
        repository = CstvAuthRepositoryImpl(
            api = api,
            sessions = sessions,
            stateHolder = stateHolder,
            errors = CstvErrorMapper(Gson()),
            clock = clock,
            networkMonitor = network,
            profileRepository = profiles,
            profileCloudReconciler = ProfileCloudReconciler(profileDao, api, CstvErrorMapper(Gson()), emptinessProbe)
        )
    }

    /** Réconciliation neutre : rien à fusionner côté Room. À stubber dans chaque test qui atteint /v1/me avec succès. */
    private suspend fun stubEmptyReconciliation() {
        whenever(profileDao.getAll()).thenReturn(emptyList())
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())
    }

    // ---- requestOtp -------------------------------------------------------

    @Test
    fun requestOtp_success_neverRevealsAccountExistence() = runTest {
        whenever(api.requestOtp(org.mockito.kotlin.any())).thenReturn(Response.success(Unit))

        val result = repository.requestOtp("mail@example.test")

        assertTrue(result.isSuccess)
    }

    @Test
    fun requestOtp_rateLimited_mapsTo429() = runTest {
        whenever(api.requestOtp(org.mockito.kotlin.any()))
            .thenReturn(errorResponse(429, "{\"code\":\"RATE_LIMITED\"}") as Response<Unit>)

        val result = repository.requestOtp("mail@example.test")

        assertTrue(result.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.RateLimited, (result.exceptionOrNull() as CstvException).cstvError)
    }

    @Test
    fun requestOtp_backendUnreachable_mapsToUnavailable() = runTest {
        whenever(api.requestOtp(org.mockito.kotlin.any())).thenAnswer { throw IOException("no network") }

        val result = repository.requestOtp("mail@example.test")

        assertTrue(result.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.Unavailable, (result.exceptionOrNull() as CstvException).cstvError)
    }

    // ---- verifyOtp ----------------------------------------------------------

    @Test
    fun verifyOtp_invalidCode_returnsInvalidOtp_withoutPersistingASession() = runTest {
        whenever(api.verifyOtp(org.mockito.kotlin.any()))
            .thenReturn(errorResponse(400, "{\"code\":\"INVALID_OTP\"}") as Response<CstvAccessTokenDto>)

        val result = repository.verifyOtp("mail@example.test", "000000")

        assertTrue(result.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.InvalidOtp, (result.exceptionOrNull() as CstvException).cstvError)
        verify(sessions, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun verifyOtp_doubleValidation_secondAttemptFailsAsConsumed_firstAlreadySaved() = runTest {
        // 1er appel : pas de session avant `save()`, puis `resolveSession()` relit
        // le mock — qui n'a pas d'état réel — donc on lui fait retourner ensuite
        // le jeton fraîchement "sauvegardé" pour simuler la persistance.
        whenever(sessions.get()).thenReturn(null, CstvSession("token", null, "mail@example.test", 1_000L + 3_600_000L))
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.verifyOtp(org.mockito.kotlin.any()))
            .thenReturn(Response.success(CstvAccessTokenDto("token", 3_600)))
            .thenReturn(errorResponse(400, "{\"code\":\"OTP_CONSUMED\"}") as Response<CstvAccessTokenDto>)
        whenever(api.getMe()).thenReturn(Response.success(accountDto()))
        stubEmptyReconciliation()

        val first = repository.verifyOtp("mail@example.test", "123456")
        val second = repository.verifyOtp("mail@example.test", "123456")

        assertTrue(first.isSuccess)
        assertTrue(second.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.InvalidOtp, (second.exceptionOrNull() as CstvException).cstvError)
        // 2 = 1 flux complet réussi (jeton puis /v1/me) ; le second essai,
        // rejeté par le backend, n'ajoute aucune sauvegarde supplémentaire.
        verify(sessions, org.mockito.kotlin.times(2)).save(org.mockito.kotlin.any())
    }

    @Test
    fun verifyOtp_success_persistsExpiresInAsAnUpperBound_thenAdoptsBackendActiveUntil() = runTest {
        val activeUntil = "2099-06-15T10:00:00Z"
        whenever(sessions.get()).thenReturn(null, CstvSession("token", null, "mail@example.test", 1_000L + 3_600_000L))
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.verifyOtp(org.mockito.kotlin.any()))
            .thenReturn(Response.success(CstvAccessTokenDto("token", 3_600)))
        whenever(api.getMe()).thenReturn(Response.success(accountDto(activeUntil = activeUntil)))
        stubEmptyReconciliation()

        val result = repository.verifyOtp("mail@example.test", "123456")

        assertTrue(result.isSuccess)
        val saved = argumentCaptor<CstvSession>()
        verify(sessions, org.mockito.kotlin.times(2)).save(saved.capture())
        assertEquals(1_000L + 3_600_000L, saved.firstValue.expiresAtMillis) // borne locale immédiate
        assertEquals(CstvTimestampParser.parseMillis(activeUntil), saved.secondValue.expiresAtMillis) // /v1/me fait foi
    }

    @Test
    fun verifyOtp_accountDisabledJustAfterVerify_isReportedAsFailure() = runTest {
        whenever(sessions.get()).thenReturn(null, CstvSession("token", null, "mail@example.test", 1_000L + 3_600_000L))
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.verifyOtp(org.mockito.kotlin.any()))
            .thenReturn(Response.success(CstvAccessTokenDto("token", 3_600)))
        whenever(api.getMe()).thenReturn(errorResponse(403, "{\"code\":\"ACCOUNT_DISABLED\"}") as Response<CstvAccountDto>)

        val result = repository.verifyOtp("mail@example.test", "123456")

        assertTrue(result.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.Disabled, (result.exceptionOrNull() as CstvException).cstvError)
    }

    @Test
    fun verifyOtp_backendUnreachable_mapsToUnavailable() = runTest {
        whenever(api.verifyOtp(org.mockito.kotlin.any())).thenAnswer { throw IOException("no network") }

        val result = repository.verifyOtp("mail@example.test", "123456")

        assertTrue(result.isFailure)
        assertEquals(com.cstv.app.domain.model.CstvError.Unavailable, (result.exceptionOrNull() as CstvException).cstvError)
    }

    // ---- resolveSession — machine d'états (F33 §5.3) -----------------------

    @Test
    fun resolveSession_noStoredSession_isSignedOutWithNoSession() = runTest {
        whenever(sessions.get()).thenReturn(null)

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.SignedOut(), state)
        assertEquals(state, stateHolder.state.value)
    }

    @Test
    fun resolveSession_locallyExpiredToken_isSignedOutWithTokenExpired_evenOffline() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 1_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis() // >= expiresAtMillis
        doReturn(false).whenever(network).isCurrentlyOnline() // même hors ligne

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.SignedOut)
        assertEquals(com.cstv.app.domain.model.SignedOutReason.TokenExpired, (state as CstvSessionState.SignedOut).reason)
        verify(api, never()).getMe()
    }

    @Test
    fun resolveSession_validTokenButBackendUnreachable_isOffline_withoutTouchingProfiles() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(false).whenever(network).isCurrentlyOnline()

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.Offline(session), state)
        verify(api, never()).getMe()
        verify(profiles, never()).purgeAllProfiles()
    }

    @Test
    fun resolveSession_networkDropsMidCall_fallsBackToOffline() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenAnswer { throw IOException("dropped") }

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.Offline(session), state)
    }

    @Test
    fun resolveSession_sessionExpiredExceptionFromInterceptor_isSignedOutWithTokenExpired() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenAnswer { throw CstvSessionExpiredException() }

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.SignedOut)
        assertEquals(com.cstv.app.domain.model.SignedOutReason.TokenExpired, (state as CstvSessionState.SignedOut).reason)
    }

    @Test
    fun resolveSession_accountDisabled_blocksImmediately() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(errorResponse(403, "{\"code\":\"ACCOUNT_DISABLED\"}") as Response<CstvAccountDto>)

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.Blocked(CstvBlockedReason.Disabled), state)
    }

    @Test
    fun resolveSession_accountExpired_blocksImmediately() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(errorResponse(403, "{\"code\":\"ACCOUNT_EXPIRED\"}") as Response<CstvAccountDto>)

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.Blocked(CstvBlockedReason.Expired), state)
    }

    @Test
    fun resolveSession_rejectedToken_signsOutAsSessionRejected() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(errorResponse(401, "{}") as Response<CstvAccountDto>)

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.SignedOut)
        assertEquals(com.cstv.app.domain.model.SignedOutReason.SessionRejected, (state as CstvSessionState.SignedOut).reason)
    }

    @Test
    fun resolveSession_unexpectedServerError_fallsBackToOffline_ratherThanBlocking() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(errorResponse(500, "{}") as Response<CstvAccountDto>)

        val state = repository.resolveSession()

        assertEquals(CstvSessionState.Offline(session), state)
    }

    @Test
    fun resolveSession_sameAccountId_neverPurgesLocalProfiles() = runTest {
        val session = CstvSession("token", "same-account", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(Response.success(accountDto(id = "same-account")))
        stubEmptyReconciliation()

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.Active)
        verify(profiles, never()).purgeAllProfiles()
    }

    @Test
    fun resolveSession_differentAccountId_purgesBeforePublishingActive_thenReconciles() = runTest {
        val session = CstvSession("token", "old-account", "old@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(Response.success(accountDto(id = "new-account")))
        stubEmptyReconciliation()

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.Active)
        verify(profiles).purgeAllProfiles()
    }

    @Test
    fun resolveSession_noPreviousAccountId_neverPurges_firstAssociationOnly() = runTest {
        val session = CstvSession("token", null, "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(Response.success(accountDto(id = "new-account")))
        stubEmptyReconciliation()

        repository.resolveSession()

        verify(profiles, never()).purgeAllProfiles()
    }

    // ---- signOut / storedEmail ----------------------------------------------

    @Test
    fun signOut_clearsOnlyTheCstvSession_neverTouchesProfiles() = runTest {
        repository.signOut()

        verify(sessions).clearSession()
        verify(profiles, never()).purgeAllProfiles()
        assertEquals(CstvSessionState.SignedOut(), stateHolder.state.value)
    }

    @Test
    fun storedEmail_delegatesToSessionManager() {
        whenever(sessions.storedEmail()).thenReturn("mail@example.test")

        assertEquals("mail@example.test", repository.storedEmail())
    }

    /**
     * Non-régression : `CstvAccountDto.toDomain()` omettait `maxAgeRating` en
     * construisant `CstvProfile` depuis `/v1/me`, donc toujours `null` quel
     * que soit le réglage réellement stocké côté backend. `ProfileCloudReconciler`
     * écrasant le niveau local avec cette valeur (toujours `null`) à chaque
     * démarrage, la restriction d'âge d'un profil disparaissait à chaque
     * relance de l'app.
     */
    @Test
    fun resolveSession_carriesProfileMaxAgeRatingFromMe_neverDropsIt() = runTest {
        val session = CstvSession("token", "acc", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(session)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        val dto = CstvAccountDto(
            "acc", "mail@example.test", true, "2099-12-31T23:59:59Z",
            listOf(CstvProfileDto("remote-1", "Nico", 0, "2024-01-01T00:00:00Z", maxAgeRating = 16))
        )
        whenever(api.getMe()).thenReturn(Response.success(dto))
        stubEmptyReconciliation()

        val state = repository.resolveSession()

        assertTrue(state is CstvSessionState.Active)
        assertEquals(16, (state as CstvSessionState.Active).account.profiles.single().maxAgeRating)
    }
}
