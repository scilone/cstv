package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.remote.api.DynamicBaseUrlInterceptor
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.dto.LiveCategoryDto
import com.poc.iptvxtream.data.remote.dto.LiveStreamDto
import com.poc.iptvxtream.data.remote.dto.LoginResponseDto
import com.poc.iptvxtream.data.remote.dto.UserInfoDto
import com.poc.iptvxtream.data.remote.dto.VodCategoryDto
import com.poc.iptvxtream.data.remote.dto.VodStreamDto
import com.poc.iptvxtream.data.remote.dto.VodInfoResponseDto
import com.poc.iptvxtream.data.remote.dto.SeriesCategoryDto
import com.poc.iptvxtream.data.remote.dto.SeriesStreamDto
import com.poc.iptvxtream.data.remote.dto.SeriesInfoResponseDto
import com.poc.iptvxtream.domain.model.*
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

    private lateinit var authRepository: AuthRepositoryImpl

    private val credentials = Credentials("test.com", 80, "username", "password", true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Set TimeZone to UTC to make date parsing tests deterministic across all machines
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        apiService = FakeXtreamApiService()
        authRepository = AuthRepositoryImpl(apiService, credentialsManager, baseUrlInterceptor)
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
            categoryId: String,
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
            categoryId: String,
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
            categoryId: String,
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
    }
}
