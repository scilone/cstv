package com.cstv.app.presentation.cstv

import com.cstv.app.R
import com.cstv.app.data.repository.CstvException
import com.cstv.app.domain.model.CstvAccount
import com.cstv.app.domain.model.CstvError
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.model.SignedOutReason
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.usecase.RequestOtpUseCase
import com.cstv.app.domain.usecase.ResolveCstvSessionUseCase
import com.cstv.app.domain.usecase.VerifyOtpUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class CstvAuthViewModelTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(repository: FakeCstvAuthRepository): CstvAuthViewModel = CstvAuthViewModel(
        repository,
        RequestOtpUseCase(repository),
        VerifyOtpUseCase(repository),
        ResolveCstvSessionUseCase(repository)
    )

    @Test fun `invalid email stays local and exposes a string resource`() = runTest {
        val repository = FakeCstvAuthRepository()
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("not-an-email")
        viewModel.requestOtp()
        assertEquals(R.string.cstv_invalid_email, viewModel.uiState.value.messageRes)
        assertEquals(0, repository.requestOtpCalls)
    }

    @Test fun `requestOtp toggles loading then reports success without revealing account existence`() = runTest {
        val repository = FakeCstvAuthRepository()
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("mail@example.test")

        viewModel.requestOtp()

        assertFalse(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.otpRequested)
        assertNull(viewModel.uiState.value.messageRes)
        assertEquals(1, repository.requestOtpCalls)
    }

    @Test fun `requestOtp network failure keeps the typed email and offers a retry message`() = runTest {
        val repository = FakeCstvAuthRepository(requestOtpResult = Result.failure(CstvException(CstvError.Unavailable)))
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("mail@example.test")

        viewModel.requestOtp()

        assertEquals("mail@example.test", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.otpRequested)
        assertEquals(R.string.cstv_network_unavailable, viewModel.uiState.value.messageRes)
    }

    @Test fun `requestOtp rate limited surfaces the dedicated message`() = runTest {
        val repository = FakeCstvAuthRepository(requestOtpResult = Result.failure(CstvException(CstvError.RateLimited)))
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("mail@example.test")

        viewModel.requestOtp()

        assertEquals(R.string.cstv_rate_limited, viewModel.uiState.value.messageRes)
    }

    @Test fun `verifyOtp rejects a malformed code locally without calling the repository`() = runTest {
        val repository = FakeCstvAuthRepository()
        val viewModel = buildViewModel(repository)

        viewModel.verifyOtp("12a45")

        assertEquals(R.string.cstv_invalid_otp_format, viewModel.uiState.value.messageRes)
        assertEquals(0, repository.verifyOtpCalls)
    }

    @Test fun `verifyOtp success clears any previous message and stops loading`() = runTest {
        val repository = FakeCstvAuthRepository()
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("mail@example.test")

        viewModel.verifyOtp("123456")

        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.uiState.value.messageRes)
        assertEquals(1, repository.verifyOtpCalls)
    }

    @Test fun `verifyOtp invalid code surfaces the dedicated message, never the raw otp`() = runTest {
        val repository = FakeCstvAuthRepository(verifyOtpResult = Result.failure(CstvException(CstvError.InvalidOtp)))
        val viewModel = buildViewModel(repository)
        viewModel.updateEmail("mail@example.test")

        viewModel.verifyOtp("000000")

        // Le message exposé est une ressource entière (@StringRes) : par
        // construction, aucun code OTP ni jeton ne peut y transiter en clair.
        assertEquals(R.string.cstv_invalid_otp, viewModel.uiState.value.messageRes)
    }

    @Test fun `verifyOtp account disabled surfaces a blocking message`() = runTest {
        val repository = FakeCstvAuthRepository(verifyOtpResult = Result.failure(CstvException(CstvError.Disabled)))
        val viewModel = buildViewModel(repository)

        viewModel.verifyOtp("123456")

        assertEquals(R.string.cstv_account_disabled, viewModel.uiState.value.messageRes)
    }

    @Test fun `signedOut with a token expired reason prefills the last known email`() = runTest {
        val repository = FakeCstvAuthRepository(
            initialState = CstvSessionState.SignedOut(SignedOutReason.TokenExpired),
            storedEmail = "mail@example.test"
        )

        val viewModel = buildViewModel(repository)

        assertEquals("mail@example.test", viewModel.uiState.value.email)
    }

    @Test fun `signedOut with no prior session never prefills an email`() = runTest {
        val repository = FakeCstvAuthRepository(
            initialState = CstvSessionState.SignedOut(SignedOutReason.NoSession),
            storedEmail = "mail@example.test"
        )

        val viewModel = buildViewModel(repository)

        assertEquals("", viewModel.uiState.value.email)
    }

    @Test fun `signOut delegates to the repository`() = runTest {
        val repository = FakeCstvAuthRepository()
        val viewModel = buildViewModel(repository)

        viewModel.signOut()

        assertEquals(1, repository.signOutCalls)
    }

    private class FakeCstvAuthRepository(
        initialState: CstvSessionState = CstvSessionState.SignedOut(),
        private val storedEmail: String? = null,
        private val requestOtpResult: Result<Unit> = Result.success(Unit),
        private val verifyOtpResult: Result<CstvAccount> = Result.success(CstvAccount("id", "mail@example.test", true, 0, emptyList()))
    ) : CstvAuthRepository {
        override val sessionState = MutableStateFlow(initialState)
        var requestOtpCalls = 0; private set
        var verifyOtpCalls = 0; private set
        var signOutCalls = 0; private set

        override suspend fun requestOtp(email: String): Result<Unit> {
            requestOtpCalls++
            return requestOtpResult
        }
        override suspend fun verifyOtp(email: String, code: String): Result<CstvAccount> {
            verifyOtpCalls++
            return verifyOtpResult
        }
        override suspend fun resolveSession() = sessionState.value
        override fun signOut() { signOutCalls++; sessionState.value = CstvSessionState.SignedOut() }
        override fun storedEmail(): String? = storedEmail
    }
}
