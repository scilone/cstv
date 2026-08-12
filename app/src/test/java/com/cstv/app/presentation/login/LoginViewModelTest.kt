package com.cstv.app.presentation.login

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.AutoLoginRejection
import com.cstv.app.domain.usecase.AutoLoginUseCase
import com.cstv.app.domain.usecase.GetSavedCredentialsUseCase
import com.cstv.app.domain.usecase.LoginUseCase
import com.cstv.app.domain.usecase.LogoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var loginUseCase: LoginUseCase

    @Mock
    private lateinit var getSavedCredentialsUseCase: GetSavedCredentialsUseCase

    @Mock
    private lateinit var logoutUseCase: LogoutUseCase

    @Mock
    private lateinit var autoLoginUseCase: AutoLoginUseCase

    @Mock
    private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager

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

    /**
     * F33 T6 : l'auto-login Xtream ne doit plus démarrer dans `init` mais sur
     * appel explicite `startAutoLogin()`, une fois le gate CSTV résolu.
     */
    @Test
    fun test_autoLogin_doesNotStart_withoutAnExplicitCall() = runTest {
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager)

        // `Checking` reste la valeur de construction du StateFlow : la preuve
        // que l'auto-login n'est pas parti est l'absence d'appel ci-dessous,
        // pas cette valeur par défaut.
        assertEquals(AutoLoginState.Checking, viewModel.autoLoginState.value)
        verify(getSavedCredentialsUseCase, never())()
        verify(autoLoginUseCase, never())()
    }

    @Test
    fun test_autoLogin_noSavedCredentials_transitionsToNoCredentials() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertNull(viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_rememberMeFalse_transitionsToNoCredentials() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = false)
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_success_transitionsToSuccess() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.Online(userInfo))

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        assertEquals(AutoLoginState.Success(userInfo), viewModel.autoLoginState.value)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_autoLogin_failure_transitionsToError() = runTest {
        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        whenever(getSavedCredentialsUseCase()).thenReturn(creds)
        whenever(autoLoginUseCase()).thenReturn(
            AutoLoginOutcome.Rejected(AutoLoginRejection.INVALID_CREDENTIALS, "Invalid credentials")
        )

        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        assertTrue(viewModel.autoLoginState.value is AutoLoginState.Error)
        assertEquals("Invalid credentials", (viewModel.autoLoginState.value as AutoLoginState.Error).message)
        assertEquals(creds, viewModel.savedCredentials.value)
    }

    @Test
    fun test_logout_clearsCredentials_andTransitionsToNoCredentials() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        viewModel.logout()

        verify(logoutUseCase).invoke()
        assertEquals(LoginState.Idle, viewModel.loginState.value)
        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
        assertNull(viewModel.savedCredentials.value)
    }

    @Test
    fun test_login_success_transitionsToSuccess() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(loginUseCase(creds)).thenReturn(userInfo)

        viewModel.login(creds)

        assertEquals(LoginState.Success(userInfo), viewModel.loginState.value)
    }

    @Test
    fun test_login_failure_transitionsToError() = runTest {
        whenever(getSavedCredentialsUseCase()).thenReturn(null)
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = LoginViewModel(loginUseCase, getSavedCredentialsUseCase, logoutUseCase, autoLoginUseCase, catalogSyncManager).also { it.startAutoLogin() }

        val creds = Credentials("host", 80, "username", "password", rememberMe = true)
        whenever(loginUseCase(creds)).thenThrow(RuntimeException("Connection Error"))

        viewModel.login(creds)

        assertTrue(viewModel.loginState.value is LoginState.Error)
        assertEquals("Connection Error", (viewModel.loginState.value as LoginState.Error).message)
    }
}
