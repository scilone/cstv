package com.cstv.app.domain.usecase

import com.cstv.app.data.security.FakeSharedPreferences
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ParentalUnlockUseCaseTest {

    private val timeProvider = object : TimeProvider { override fun nowMillis() = 0L }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = 0L }
    private val pinStore = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
    private val grantStore = OneShotPlaybackGrantStore()
    private val profileRepository: ProfileRepository = mock()
    private lateinit var useCase: ParentalUnlockUseCase

    @Before
    fun setUp() {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        useCase = ParentalUnlockUseCase(pinStore, grantStore, profileRepository)
    }

    @Test
    fun `a correct pin issues a grant that unlocks exactly this media`() {
        pinStore.createPin("1234")

        val result = useCase.unlock("1234", "movie_42") as ParentalUnlockResult.Unlocked

        assertTrue(grantStore.consume(1, "movie_42", result.nonce))
    }

    @Test
    fun `an incorrect pin issues no grant`() {
        pinStore.createPin("1234")

        val result = useCase.unlock("0000", "movie_42")

        assertEquals(ParentalUnlockResult.Incorrect, result)
    }

    @Test
    fun `a locked store refuses without comparing the pin`() {
        pinStore.createPin("1234")
        repeat(5) { pinStore.verifyPin("0000") }

        val result = useCase.unlock("1234", "movie_42") as ParentalUnlockResult.Locked
        assertTrue(result.remainingMillis > 0)
    }
}
