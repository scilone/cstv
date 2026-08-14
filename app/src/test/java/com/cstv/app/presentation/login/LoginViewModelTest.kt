package com.cstv.app.presentation.login

import android.content.Context
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.AutoLoginRejection
import com.cstv.app.domain.usecase.AutoLoginUseCase
import com.cstv.app.domain.usecase.LoginUseCase
import com.cstv.app.domain.usecase.LogoutUseCase
import com.cstv.app.domain.usecase.SetIptvCloudBackupConsentUseCase
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.model.IptvCloudBackupNotifier
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
    private lateinit var logoutUseCase: LogoutUseCase

    @Mock
    private lateinit var autoLoginUseCase: AutoLoginUseCase

    @Mock
    private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager

    @Mock
    private lateinit var context: Context

    private lateinit var backup: FakeBackup

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        backup = FakeBackup()
    }

    private fun newViewModel() = LoginViewModel(
        loginUseCase,
        logoutUseCase,
        autoLoginUseCase,
        catalogSyncManager,
        backup,
        SetIptvCloudBackupConsentUseCase(backup),
        IptvCloudBackupNotifier(),
        context
    )

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
        viewModel = newViewModel()

        // `Checking` reste la valeur de construction du StateFlow : la preuve
        // que l'auto-login n'est pas parti est l'absence d'appel ci-dessous,
        // pas cette valeur par défaut.
        assertEquals(AutoLoginState.Checking, viewModel.autoLoginState.value)
        verify(autoLoginUseCase, never())()
    }

    @Test
    fun test_autoLogin_noSavedCredentials_transitionsToNoCredentials() = runTest {
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)

        viewModel = newViewModel().also { it.startAutoLogin() }

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
    }

    @Test
    fun test_autoLogin_savedCredentials_transitionsToNoCredentialsWhenRepositoryRejects() = runTest {
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)

        viewModel = newViewModel().also { it.startAutoLogin() }

        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
    }

    @Test
    fun test_autoLogin_success_transitionsToSuccess() = runTest {
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.Online(userInfo))

        viewModel = newViewModel().also { it.startAutoLogin() }

        assertEquals(AutoLoginState.Success(userInfo), viewModel.autoLoginState.value)
    }

    @Test
    fun test_autoLogin_failure_transitionsToError() = runTest {
        whenever(autoLoginUseCase()).thenReturn(
            AutoLoginOutcome.Rejected(AutoLoginRejection.INVALID_CREDENTIALS, "Invalid credentials")
        )

        viewModel = newViewModel().also { it.startAutoLogin() }

        assertTrue(viewModel.autoLoginState.value is AutoLoginState.Error)
        assertEquals("Invalid credentials", (viewModel.autoLoginState.value as AutoLoginState.Error).message)
    }

    @Test
    fun test_logout_clearsCredentials_andTransitionsToNoCredentials() = runTest {
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = newViewModel().also { it.startAutoLogin() }

        viewModel.logout()

        verify(logoutUseCase).invoke()
        assertEquals(LoginState.Idle, viewModel.loginState.value)
        assertEquals(AutoLoginState.NoCredentials, viewModel.autoLoginState.value)
    }

    @Test
    fun test_login_success_transitionsToSuccess() = runTest {
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = newViewModel().also { it.startAutoLogin() }

        val creds = Credentials("host", 80, "username", "password")
        val userInfo = UserInfo("username", true, "Active", "12/12/2026", 1, 0, "OK")
        whenever(loginUseCase(creds)).thenReturn(userInfo)

        viewModel.login(creds)

        assertEquals(LoginState.Success(userInfo), viewModel.loginState.value)
    }

    @Test
    fun test_login_failure_transitionsToError() = runTest {
        whenever(autoLoginUseCase()).thenReturn(AutoLoginOutcome.NoCredentials)
        viewModel = newViewModel().also { it.startAutoLogin() }

        val creds = Credentials("host", 80, "username", "password")
        whenever(loginUseCase(creds)).thenThrow(RuntimeException("Connection Error"))

        viewModel.login(creds)

        assertTrue(viewModel.loginState.value is LoginState.Error)
        assertEquals("Connection Error", (viewModel.loginState.value as LoginState.Error).message)
    }

    private class FakeBackup : IptvCredentialsBackupRepository {
        var accountId: String? = null
        var consent = false
        var setConsentCalls = mutableListOf<Boolean>()
        override fun linkedAccountId(): String? = accountId
        override fun isConsentEnabled(): Boolean = consent
        override suspend fun setConsent(enabled: Boolean): IptvBackupOutcome {
            setConsentCalls += enabled
            consent = enabled
            return IptvBackupOutcome.Skipped
        }
        override suspend fun onAuthenticated(credentials: Credentials) = IptvBackupOutcome.Skipped
        override suspend fun restore() = IptvRestoreOutcome.Absent
        override suspend fun invalidateRestored() = IptvBackupOutcome.Skipped
        override suspend fun deleteForIptvLogout() = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut() = IptvBackupOutcome.Skipped
        override suspend fun drainPending() = IptvBackupOutcome.Skipped
    }
}
