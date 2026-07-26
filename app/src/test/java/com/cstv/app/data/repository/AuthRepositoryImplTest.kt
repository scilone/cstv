package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.DynamicBaseUrlInterceptor
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.dto.EpgResponseDto
import com.cstv.app.data.remote.dto.LiveCategoryDto
import com.cstv.app.data.remote.dto.LiveStreamDto
import com.cstv.app.data.remote.dto.LoginResponseDto
import com.cstv.app.data.remote.dto.UserInfoDto
import com.cstv.app.data.remote.dto.VodCategoryDto
import com.cstv.app.data.remote.dto.VodStreamDto
import com.cstv.app.data.remote.dto.VodInfoResponseDto
import com.cstv.app.data.remote.dto.SeriesCategoryDto
import com.cstv.app.data.remote.dto.SeriesStreamDto
import com.cstv.app.data.remote.dto.SeriesInfoResponseDto
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.TrailerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    private lateinit var apiService: FakeXtreamApiService

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var baseUrlInterceptor: DynamicBaseUrlInterceptor

    @Mock
    private lateinit var trailerRepository: TrailerRepository

    @Mock
    private lateinit var networkMonitor: com.cstv.app.domain.network.NetworkMonitor

    @Mock
    private lateinit var syncStateDao: com.cstv.app.data.local.dao.CatalogSyncStateDao

    @Mock
    private lateinit var settingsManager: com.cstv.app.data.local.storage.SettingsManager

    @Mock
    private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager

    private lateinit var syncStateInitializer: com.cstv.app.data.sync.CatalogSyncStateInitializer

    private lateinit var authRepository: AuthRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Set TimeZone to UTC to make date parsing tests deterministic across all machines
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        apiService = FakeXtreamApiService()
        syncStateInitializer = com.cstv.app.data.sync.CatalogSyncStateInitializer(
            syncStateDao, settingsManager, credentialsManager
        )
        authRepository = AuthRepositoryImpl(
            apiService,
            credentialsManager,
            baseUrlInterceptor,
            com.cstv.app.data.remote.api.XtreamRequestGate(),
            trailerRepository,
            networkMonitor,
            syncStateDao,
            syncStateInitializer,
            catalogSyncManager
        )
    }

    @Test
    fun login_success_returnsUserInfo() = runTest {
        val userDto = UserInfoDto(
            username = "username",
            password = "password",
            message = "Hello",
            auth = 1,
            status = "Active",
            expDate = 2000000000L, // 18/05/2033 in UTC (Future date)
            isTrial = 0,
            activeCons = 1,
            maxConnections = 2
        )
        apiService.loginResult = { LoginResponseDto(userDto, null) }

        val result = authRepository.login(credentials)

        assertNotNull(result)
        assertEquals("username", result.username)
        assertTrue(result.auth)
        assertEquals("Active", result.status)
        assertEquals("18/05/2033", result.expiryDate)
        assertEquals(2, result.maxConnections)
        assertEquals(1, result.activeConnections)
        assertEquals("Hello", result.message)
    }

    @Test
    fun login_invalidCredentials_throwsInvalidCredentialsException() = runTest {
        val userDto = UserInfoDto(
            username = "username",
            password = "password",
            message = "Auth failed",
            auth = 0,
            status = "Active",
            expDate = null,
            isTrial = 0,
            activeCons = 0,
            maxConnections = 0
        )
        apiService.loginResult = { LoginResponseDto(userDto, null) }

        assertThrows(InvalidCredentialsException::class.java) {
            runBlocking {
                authRepository.login(credentials)
            }
        }
    }

    @Test
    fun login_expiredAccount_throwsAccountExpiredException() = runTest {
        val userDto = UserInfoDto(
            username = "username",
            password = "password",
            message = "Expired",
            auth = 1,
            status = "Expired",
            expDate = 1600000000L, // Past date (Expired relative to 2026)
            isTrial = 0,
            activeCons = 0,
            maxConnections = 1
        )
        apiService.loginResult = { LoginResponseDto(userDto, null) }

        assertThrows(AccountExpiredException::class.java) {
            runBlocking {
                authRepository.login(credentials)
            }
        }
    }

    @Test
    fun login_timeoutException_throwsNetworkTimeoutException() = runTest {
        apiService.loginResult = { throw SocketTimeoutException("Timeout") }

        assertThrows(NetworkTimeoutException::class.java) {
            runBlocking {
                authRepository.login(credentials)
            }
        }
    }

    @Test
    fun login_connectException_throwsServerUnreachableException() = runTest {
        apiService.loginResult = { throw ConnectException("Connection refused") }

        assertThrows(ServerUnreachableException::class.java) {
            runBlocking {
                authRepository.login(credentials)
            }
        }
    }

    private class FakeXtreamApiService : XtreamApiService {
        var loginResult: () -> LoginResponseDto = { throw IOException("Not stubbed") }
        
        override suspend fun login(username: String, password: String): LoginResponseDto {
            return loginResult()
        }

        override suspend fun getLiveCategories(
            username: String,
            password: String,
            action: String
        ): List<LiveCategoryDto> {
            return emptyList()
        }

        override suspend fun getLiveStreams(
            username: String,
            password: String,
            categoryId: String?,
            action: String
        ): List<LiveStreamDto> {
            return emptyList()
        }

        override suspend fun getVodCategories(
            username: String,
            password: String,
            action: String
        ): List<VodCategoryDto> {
            return emptyList()
        }

        override suspend fun getVodStreams(
            username: String,
            password: String,
            categoryId: String?,
            action: String
        ): List<VodStreamDto> {
            return emptyList()
        }

        override suspend fun getVodInfo(
            username: String,
            password: String,
            vodId: Int,
            action: String
        ): VodInfoResponseDto {
            throw IOException("Not stubbed")
        }

        override suspend fun getSeriesCategories(
            username: String,
            password: String,
            action: String
        ): List<SeriesCategoryDto> {
            return emptyList()
        }

        override suspend fun getSeriesStreams(
            username: String,
            password: String,
            categoryId: String?,
            action: String
        ): List<SeriesStreamDto> {
            return emptyList()
        }

        override suspend fun getSeriesInfo(
            username: String,
            password: String,
            seriesId: Int,
            action: String
        ): SeriesInfoResponseDto {
            throw IOException("Not stubbed")
        }

        override suspend fun getShortEpg(
            username: String,
            password: String,
            streamId: Int,
            action: String
        ): EpgResponseDto {
            return EpgResponseDto(emptyList())
        }
    }

    // --- T4 : connexion automatique et démarrage sans réseau ---

    private fun stubCompleteCatalog(forKey: String) {
        val states = com.cstv.app.data.local.entity.CatalogSection.CATALOG_SECTIONS.map {
            com.cstv.app.data.local.entity.CatalogSyncStateEntity(
                section = it, accountKey = forKey, lastSuccessAt = 1L
            )
        }
        runBlocking { org.mockito.kotlin.whenever(syncStateDao.getAll()).thenReturn(states) }
    }

    @Test
    fun autoLogin_withoutStoredCredentials_returnsNoCredentials() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(null)

        assertTrue(authRepository.autoLogin() is AutoLoginOutcome.NoCredentials)
    }

    @Test
    fun autoLogin_withRememberMeDisabled_returnsNoCredentials() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials())
            .thenReturn(credentials.copy(rememberMe = false))

        assertTrue(authRepository.autoLogin() is AutoLoginOutcome.NoCredentials)
    }

    @Test
    fun autoLogin_online_validatesAgainstThePanel() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        org.mockito.kotlin.whenever(syncStateDao.getAll()).thenReturn(emptyList())
        apiService.loginResult = {
            LoginResponseDto(
                UserInfoDto("username", "password", "Hello", 1, "Active", 2000000000L, 0, 1, 2),
                null
            )
        }

        val outcome = authRepository.autoLogin()

        assertTrue(outcome is AutoLoginOutcome.Online)
        assertFalse((outcome as AutoLoginOutcome.Online).userInfo.isOfflineSession)
    }

    /**
     * Repli hors ligne : accordé uniquement parce qu'une validation réseau a
     * déjà réussi **et** que le catalogue est complet pour ce compte.
     */
    @Test
    fun autoLogin_offline_withPriorValidationAndCompleteCatalog_grantsOfflineSession() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(1L)
        org.mockito.kotlin.whenever(credentialsManager.getLastUserInfo())
            .thenReturn(UserInfo("username", true, "Active", "18/05/2033", 2, 1, "", isOfflineSession = true))
        stubCompleteCatalog(com.cstv.app.data.sync.CatalogServerKey.from(credentials))
        org.mockito.kotlin.whenever(credentialsManager.getLastValidatedAccountKey())
            .thenReturn(com.cstv.app.data.sync.AccountKey.from(credentials))

        val outcome = authRepository.autoLogin()

        assertTrue(outcome is AutoLoginOutcome.OfflineSession)
        assertTrue((outcome as AutoLoginOutcome.OfflineSession).userInfo.isOfflineSession)
    }

    /** Sans validation antérieure, la première connexion exige Internet. */
    @Test
    fun autoLogin_offline_withoutPriorValidation_isRejected() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(0L)
        stubCompleteCatalog(com.cstv.app.data.sync.CatalogServerKey.from(credentials))

        val outcome = authRepository.autoLogin()

        assertEquals(AutoLoginRejection.NO_LOCAL_SESSION, (outcome as AutoLoginOutcome.Rejected).reason)
    }

    /**
     * Cas limite §5.5 : un catalogue partiel issu d'une première
     * synchronisation interrompue n'ouvre pas le mode hors-ligne.
     */
    @Test
    fun autoLogin_offline_withPartialCatalog_isRejected() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(1L)
        org.mockito.kotlin.whenever(credentialsManager.getLastUserInfo())
            .thenReturn(UserInfo("username", true, "Active", "18/05/2033", 2, 1, ""))
        val key = com.cstv.app.data.sync.CatalogServerKey.from(credentials)
        org.mockito.kotlin.whenever(syncStateDao.getAll()).thenReturn(
            listOf(
                com.cstv.app.data.local.entity.CatalogSyncStateEntity(
                    section = com.cstv.app.data.local.entity.CatalogSection.LIVE_STREAMS,
                    accountKey = key,
                    lastSuccessAt = 1L
                )
            )
        )

        val outcome = authRepository.autoLogin()

        assertEquals(AutoLoginRejection.NO_LOCAL_SESSION, (outcome as AutoLoginOutcome.Rejected).reason)
    }

    /** Un refus explicite du panel n'est jamais contourné par un repli local. */
    @Test
    fun autoLogin_invalidCredentials_isRejectedWithoutFallback() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        org.mockito.kotlin.whenever(syncStateDao.getAll()).thenReturn(emptyList())
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(1L)
        apiService.loginResult = {
            LoginResponseDto(
                UserInfoDto("username", "password", "", 0, "Active", 2000000000L, 0, 0, 1),
                null
            )
        }

        val outcome = authRepository.autoLogin()

        assertEquals(AutoLoginRejection.INVALID_CREDENTIALS, (outcome as AutoLoginOutcome.Rejected).reason)
        org.mockito.kotlin.verify(credentialsManager).clearOfflineSessionValidation()
    }

    /** Un compte expiré est une réponse du serveur, pas une panne. */
    @Test
    fun autoLogin_expiredAccount_isRejectedWithoutFallback() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        org.mockito.kotlin.whenever(syncStateDao.getAll()).thenReturn(emptyList())
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(1L)
        apiService.loginResult = {
            LoginResponseDto(
                UserInfoDto("username", "password", "", 1, "Expired", 1000L, 0, 0, 1),
                null
            )
        }

        val outcome = authRepository.autoLogin()

        assertEquals(AutoLoginRejection.ACCOUNT_EXPIRED, (outcome as AutoLoginOutcome.Rejected).reason)
        org.mockito.kotlin.verify(credentialsManager).clearOfflineSessionValidation()
    }

    /** Un serveur injoignable est une panne : le repli local reste possible. */
    @Test
    fun autoLogin_serverUnreachable_fallsBackToOfflineSession() = runTest {
        org.mockito.kotlin.whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        org.mockito.kotlin.whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        org.mockito.kotlin.whenever(credentialsManager.getLastSuccessfulLoginAt()).thenReturn(1L)
        org.mockito.kotlin.whenever(credentialsManager.getLastUserInfo())
            .thenReturn(UserInfo("username", true, "Active", "18/05/2033", 2, 1, ""))
        stubCompleteCatalog(com.cstv.app.data.sync.CatalogServerKey.from(credentials))
        org.mockito.kotlin.whenever(credentialsManager.getLastValidatedAccountKey())
            .thenReturn(com.cstv.app.data.sync.AccountKey.from(credentials))
        apiService.loginResult = { throw ConnectException("injoignable") }

        assertTrue(authRepository.autoLogin() is AutoLoginOutcome.OfflineSession)
    }

    /** Une connexion réussie horodate la validation et contrôle le compte. */
    @Test
    fun login_success_recordsValidationAndChecksAccountChange() = runTest {
        apiService.loginResult = {
            LoginResponseDto(
                UserInfoDto("username", "password", "", 1, "Active", 2000000000L, 0, 0, 1),
                null
            )
        }

        authRepository.login(credentials)

        org.mockito.kotlin.verify(credentialsManager).setLastSuccessfulLoginAt(org.mockito.kotlin.any())
        org.mockito.kotlin.verify(credentialsManager)
            .setLastValidatedAccountKey(com.cstv.app.data.sync.AccountKey.from(credentials))
        org.mockito.kotlin.verify(credentialsManager).saveLastUserInfo(org.mockito.kotlin.any())
        org.mockito.kotlin.verify(catalogSyncManager)
            .onAccountAuthenticated(com.cstv.app.data.sync.CatalogServerKey.from(credentials))
    }
}
