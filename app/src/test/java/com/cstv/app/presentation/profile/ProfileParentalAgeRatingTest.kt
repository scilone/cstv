package com.cstv.app.presentation.profile

import com.cstv.app.data.security.FakeSharedPreferences
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.model.ParentalPinFeedback
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * F44, tâche 6 : modifier le niveau d'âge autorisé exige toujours le PIN
 * (§7.2/§8.6), y compris pour débrider (`null`). Ce ViewModel est le seul
 * point d'entrée de ce changement — aucun modèle instrumenté requis (règle
 * AGENTS.md §9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileParentalAgeRatingTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val testDispatcher = StandardTestDispatcher()
    private val profileRepository: ProfileRepository = mock()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 0L }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = 0L }
    private lateinit var pinStore: ParentalPinStore
    private lateinit var viewModel: ProfileViewModel

    private fun profile(id: Int, maxAgeRating: Int? = null) = Profile(id, "P$id", 0, id.toLong(), maxAgeRating = maxAgeRating)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinStore = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
        whenever(profileRepository.observeProfiles()).thenReturn(flowOf(listOf(profile(5))))
        whenever(profileRepository.autoStartProfileId).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(-1))
        viewModel = ProfileViewModel(profileRepository, null, pinStore)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `first activation requires creating a device pin before applying the change`() = runTest {
        viewModel.requestMaxAgeRatingChange(5, 12)

        val pending = viewModel.state.value.pendingAgeRatingChange
        assertEquals(5, pending?.profileId)
        assertEquals(12, pending?.newMaxAgeRating)
        assertTrue(pending!!.requiresPinCreation)
    }

    @Test
    fun `creating the device pin applies the pending change`() = runTest {
        viewModel.requestMaxAgeRatingChange(5, 12)

        viewModel.createDevicePinAndApplyPendingChange("1234")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.pendingAgeRatingChange)
        verify(profileRepository).updateMaxAgeRating(5, 12)
        assertTrue(pinStore.hasPin())
    }

    @Test
    fun `an existing pin does not require creation again`() = runTest {
        pinStore.createPin("1234")

        viewModel.requestMaxAgeRatingChange(5, 16)

        assertEquals(false, viewModel.state.value.pendingAgeRatingChange?.requiresPinCreation)
    }

    @Test
    fun `the correct pin applies the change, including unbridging to null`() = runTest {
        pinStore.createPin("1234")
        viewModel.requestMaxAgeRatingChange(5, null)

        viewModel.confirmAgeRatingChange("1234")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.pendingAgeRatingChange)
        verify(profileRepository).updateMaxAgeRating(5, null)
    }

    @Test
    fun `a wrong pin refuses the change and leaves it pending`() = runTest {
        pinStore.createPin("1234")
        viewModel.requestMaxAgeRatingChange(5, 12)

        viewModel.confirmAgeRatingChange("0000")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ParentalPinFeedback.Incorrect, viewModel.state.value.ageRatingChangeFeedback)
        assertEquals(5, viewModel.state.value.pendingAgeRatingChange?.profileId)
        verify(profileRepository, org.mockito.kotlin.never()).updateMaxAgeRating(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `cancelling clears the pending change without applying it`() = runTest {
        pinStore.createPin("1234")
        viewModel.requestMaxAgeRatingChange(5, 12)

        viewModel.cancelAgeRatingChange()

        assertNull(viewModel.state.value.pendingAgeRatingChange)
        verify(profileRepository, org.mockito.kotlin.never()).updateMaxAgeRating(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull())
    }
}
