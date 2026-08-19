package com.cstv.app.domain.usecase

import com.cstv.app.data.security.FakeSharedPreferences
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * F44, tâche 7 : réinitialisation du PIN par OTP (§8.5). Le backend ne
 * connaît jamais le PIN — seule une réauthentification fraîche (≤5 min)
 * autorise `resetPin`.
 */
class ParentalPinResetUseCaseTest {

    private var now = 0L
    private val timeProvider = object : TimeProvider { override fun nowMillis() = now }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = now }
    private val pinStore = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
    private val verifyOtpUseCase: VerifyOtpUseCase = mock()
    private lateinit var useCase: ParentalPinResetUseCase

    @Before
    fun setUp() {
        useCase = ParentalPinResetUseCase(verifyOtpUseCase, pinStore, timeProvider)
    }

    @Test
    fun `resetPin fails without any prior OTP verification`() {
        assertFalse(useCase.resetPin("1234"))
    }

    @Test
    fun `a successful OTP verification opens a fresh window that allows resetPin`() = runTest {
        whenever(verifyOtpUseCase("a@b.com", "123456")).thenReturn(Result.success(mock()))

        val result = useCase.verifyReauthentication("a@b.com", "123456")

        assertTrue(result.isSuccess)
        assertTrue(useCase.resetPin("1234"))
        assertTrue(pinStore.verifyPin("1234") == com.cstv.app.data.security.PinVerificationResult.Correct)
    }

    @Test
    fun `a failed OTP verification never opens a fresh window`() = runTest {
        whenever(verifyOtpUseCase("a@b.com", "000000")).thenReturn(Result.failure(RuntimeException("invalid code")))

        val result = useCase.verifyReauthentication("a@b.com", "000000")

        assertTrue(result.isFailure)
        assertFalse(useCase.resetPin("1234"))
    }

    @Test
    fun `resetPin fails once the 5-minute freshness window has expired`() = runTest {
        whenever(verifyOtpUseCase("a@b.com", "123456")).thenReturn(Result.success(mock()))
        useCase.verifyReauthentication("a@b.com", "123456")

        now += ParentalPinResetUseCase.FRESHNESS_WINDOW_MS + 1

        assertFalse(useCase.isReauthenticationFresh())
        assertFalse(useCase.resetPin("1234"))
    }

    @Test
    fun `a successful reset consumes the freshness window, refusing a second reset`() = runTest {
        whenever(verifyOtpUseCase("a@b.com", "123456")).thenReturn(Result.success(mock()))
        useCase.verifyReauthentication("a@b.com", "123456")

        assertTrue(useCase.resetPin("1234"))
        assertFalse(useCase.resetPin("5678"))
    }

    @Test
    fun `an offline or unavailable backend leaves the existing pin intact`() = runTest {
        pinStore.createPin("9999")
        whenever(verifyOtpUseCase("a@b.com", "123456")).thenReturn(Result.failure(java.io.IOException("offline")))

        useCase.verifyReauthentication("a@b.com", "123456")
        assertFalse(useCase.resetPin("1234"))

        assertEquals(com.cstv.app.data.security.PinVerificationResult.Correct, pinStore.verifyPin("9999"))
    }
}
