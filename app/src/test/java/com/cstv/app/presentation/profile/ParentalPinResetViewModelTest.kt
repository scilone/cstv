package com.cstv.app.presentation.profile

import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.usecase.ParentalPinResetUseCase
import com.cstv.app.domain.usecase.RequestOtpUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** F44, tâche 7 : PIN oublié — flow OTP réutilisé en mode réauthentification (§8.5). */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentalPinResetViewModelTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val testDispatcher = StandardTestDispatcher()
    private val requestOtpUseCase: RequestOtpUseCase = mock()
    private val parentalPinResetUseCase: ParentalPinResetUseCase = mock()
    private val cstvAuthRepository: CstvAuthRepository = mock()
    private lateinit var viewModel: ParentalPinResetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ParentalPinResetViewModel(requestOtpUseCase, parentalPinResetUseCase, cstvAuthRepository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starting without a connected account surfaces an error, no OTP sent`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn(null)

        viewModel.start()

        assertEquals(com.cstv.app.R.string.parental_pin_reset_no_account, viewModel.state.value.errorRes)
        assertNull(viewModel.state.value.step)
    }

    @Test
    fun `a successful otp request moves to the awaiting-otp step`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn("a@b.com")
        whenever(requestOtpUseCase("a@b.com")).thenReturn(Result.success(Unit))

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ParentalPinResetStep.AwaitingOtp("a@b.com"), viewModel.state.value.step)
    }

    @Test
    fun `offline or unavailable backend on otp request surfaces an error and stays put`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn("a@b.com")
        whenever(requestOtpUseCase("a@b.com")).thenReturn(Result.failure(java.io.IOException("offline")))

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.step)
        assertEquals(com.cstv.app.R.string.parental_pin_reset_otp_failed, viewModel.state.value.errorRes)
    }

    @Test
    fun `a correct otp moves to the new-pin step`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn("a@b.com")
        whenever(requestOtpUseCase("a@b.com")).thenReturn(Result.success(Unit))
        whenever(parentalPinResetUseCase.verifyReauthentication("a@b.com", "123456")).thenReturn(Result.success(Unit))

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.submitOtp("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ParentalPinResetStep.AwaitingNewPin, viewModel.state.value.step)
    }

    @Test
    fun `an invalid otp stays on the same step with an error`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn("a@b.com")
        whenever(requestOtpUseCase("a@b.com")).thenReturn(Result.success(Unit))
        whenever(parentalPinResetUseCase.verifyReauthentication("a@b.com", "000000")).thenReturn(Result.failure(RuntimeException()))

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.submitOtp("000000")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ParentalPinResetStep.AwaitingOtp("a@b.com"), viewModel.state.value.step)
        assertEquals(com.cstv.app.R.string.parental_pin_reset_otp_invalid, viewModel.state.value.errorRes)
    }

    @Test
    fun `submitting the new pin completes the flow when the use case accepts it`() = runTest {
        whenever(parentalPinResetUseCase.resetPin("1234")).thenReturn(true)

        viewModel.submitNewPin("1234")

        assertTrue(viewModel.state.value.completed)
        assertNull(viewModel.state.value.step)
    }

    @Test
    fun `an expired freshness window on the final step restarts the flow with an error`() = runTest {
        whenever(parentalPinResetUseCase.resetPin("1234")).thenReturn(false)

        viewModel.submitNewPin("1234")

        assertEquals(false, viewModel.state.value.completed)
        assertEquals(com.cstv.app.R.string.parental_pin_reset_expired, viewModel.state.value.errorRes)
        assertNull(viewModel.state.value.step)
    }

    @Test
    fun `cancel resets the whole state`() = runTest {
        whenever(cstvAuthRepository.storedEmail()).thenReturn("a@b.com")
        whenever(requestOtpUseCase("a@b.com")).thenReturn(Result.success(Unit))
        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancel()

        assertEquals(ParentalPinResetUiState(), viewModel.state.value)
    }
}
