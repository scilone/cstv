package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.CstvSessionStateHolder
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.repository.CloudProfileEmptinessProbe
import com.cstv.app.data.repository.CstvAuthRepositoryImpl
import com.cstv.app.data.repository.ProfileCloudReconciler
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.util.TimeProvider
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * F33 §5.7 — « Se déconnecter » (Xtream) et « Se déconnecter du compte CSTV »
 * sont deux actions indépendantes qui ne doivent jamais se marcher dessus :
 * l'une ne touche que les identifiants Xtream, l'autre que la session CSTV.
 * `account_id` n'est jamais effacé par une déconnexion CSTV seule — seule une
 * reconnexion à un compte *différent* purge les profils (F33 §5.4 étape 4).
 */
class LogoutIsolationTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var xtreamAuthRepository: AuthRepository

    @Mock private lateinit var api: CstvApiService
    @Mock private lateinit var sessions: CstvSessionManager
    @Mock private lateinit var clock: TimeProvider
    @Mock private lateinit var network: NetworkMonitor
    @Mock private lateinit var profiles: ProfileRepository
    @Mock private lateinit var profileDao: ProfileDao
    @Mock private lateinit var emptinessProbe: CloudProfileEmptinessProbe

    private lateinit var stateHolder: CstvSessionStateHolder
    private lateinit var cstvAuthRepository: CstvAuthRepositoryImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        stateHolder = CstvSessionStateHolder()
        cstvAuthRepository = CstvAuthRepositoryImpl(
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

    @Test
    fun `xtream logout only clears xtream credentials`() {
        LogoutUseCase(xtreamAuthRepository, NoopBackup(), com.cstv.app.domain.model.IptvCloudBackupNotifier(), CoroutineScope(Dispatchers.Unconfined))()

        verify(xtreamAuthRepository).clearCredentials()
        // Aucune dépendance CSTV n'existe même dans le constructeur de
        // LogoutUseCase : ce test documente l'isolation structurelle.
    }

    private class NoopBackup : com.cstv.app.domain.repository.IptvCredentialsBackupRepository {
        override fun linkedAccountId(): String? = null
        override fun isConsentEnabled() = false
        override suspend fun setConsent(enabled: Boolean) = com.cstv.app.domain.model.IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: com.cstv.app.domain.model.Credentials) = com.cstv.app.domain.model.IptvBackupOutcome.Skipped
        override suspend fun restore() = com.cstv.app.domain.model.IptvRestoreOutcome.Absent
        override suspend fun deleteForIptvLogout() = com.cstv.app.domain.model.IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut() = com.cstv.app.domain.model.IptvBackupOutcome.Skipped
        override suspend fun drainPending() = com.cstv.app.domain.model.IptvBackupOutcome.Skipped
    }

    @Test
    fun `cstv sign out only clears the cstv session, never profiles or xtream credentials`() = runTest {
        cstvAuthRepository.signOut()

        verify(sessions).clearSession()
        verify(profiles, never()).purgeAllProfiles()
        verifyNoInteractions(xtreamAuthRepository)
        assertEquals(CstvSessionState.SignedOut(), stateHolder.state.value)
    }

    @Test
    fun `reconnecting to the same cstv account after a sign out never purges existing profiles`() = runTest {
        // `CstvSessionManagerImpl.clearSession()` retire le jeton mais conserve
        // `account_id` (F33 §5.7) : une reconnexion au même compte le retrouve
        // donc identique lors du prochain `/v1/me`.
        val reconnected = CstvSession("new-token", "same-account", "mail@example.test", 5_000L)
        whenever(sessions.get()).thenReturn(reconnected)
        doReturn(1_000L).whenever(clock).nowMillis()
        doReturn(true).whenever(network).isCurrentlyOnline()
        whenever(api.getMe()).thenReturn(
            Response.success(
                com.cstv.app.data.remote.dto.CstvAccountDto(
                    "same-account", "mail@example.test", true, "2099-12-31T23:59:59Z", emptyList()
                )
            )
        )
        whenever(profileDao.getAll()).thenReturn(emptyList())
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())

        val state = cstvAuthRepository.resolveSession()

        assertEquals(true, state is CstvSessionState.Active)
        verify(profiles, never()).purgeAllProfiles()
    }
}
