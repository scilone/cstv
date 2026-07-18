package com.cstv.app.presentation.login

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.usecase.GetSavedCredentialsUseCase
import com.cstv.app.domain.usecase.LoginUseCase
import com.cstv.app.domain.usecase.LogoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @Mock
    private lateinit var loginUseCase: LoginUseCase

    @Mock
    private lateinit var getSavedCredentialsUseCase: GetSavedCredentialsUseCase

    @Mock
    private lateinit var logoutUseCase: LogoutUseCase

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_autoLogin_noSavedCredentials_transitionsToNoCredentials() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertNull(viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_rememberMeFalse_transitionsToNoCredentials() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = false)
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_success_transitionsToSuccess() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)
        whenever(loginUseCase(creds)).thenReturn(userInfo)

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        assertEquals(AutoLoginState.Success(userInfo), viewModel.autoLoginState.value)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_failure_transitionsToError() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)
        whenever(loginUseCase(creds)).thenThrow(RuntimeException("Invalid credentials"))

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        assertTrue(viewModel.autoLoginState.value is AutoLoginState.Error)
        assertEquals("Invalid credentials", (viewModel.autoLoginState.value as AutoLoginState.Error).message)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_logout_clearsCredentials_andTransitionsToNoCredentials() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        viewModel.logout()

        verify(logoutUseCase).invoke()
        assertEquals(LoginState.Idle, viewModel.loginState.value)
        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertNull(viewModel.savedCredentials.value)
    }

    @Test
    fun test_login_success_transitionsToSuccess() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(loginUseCase(creds)).thenReturn(userInfo)

        viewModel.login(creds)

        assertEquals(LoginState.Success(userInfo), viewModel.loginState.value)
    }

    @Test
    fun test_login_failure_transitionsToError() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase)

        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        whenever(loginUseCase(creds)).thenThrow(RuntimeException("Connection Error"))

        viewModel.login(creds)

        assertTrue(viewModel.loginState.value is LoginState.Error)
        assertEquals("Connection Error", (viewModel.loginState.value as LoginState.Error).message)
    }
}
