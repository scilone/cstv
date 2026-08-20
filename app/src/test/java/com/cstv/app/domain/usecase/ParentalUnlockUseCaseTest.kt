package com.cstv.app.domain.usecase

import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.data.security.FakeSharedPreferences
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.repository.ParentalAuthorizationRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ParentalUnlockUseCaseTest {

    private val timeProvider = object : TimeProvider { override fun nowMillis() = 0L }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = 0L }
    private val pinStore = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
    private val grantStore = OneShotPlaybackGrantStore()
    private val profileRepository: ProfileRepository = mock()
    private val parentalAuthorizationRepository: ParentalAuthorizationRepository = mock()
    private lateinit var useCase: ParentalUnlockUseCase

    @Before
    fun setUp() {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        useCase = ParentalUnlockUseCase(pinStore, grantStore, profileRepository, parentalAuthorizationRepository)
    }

    @Test
    fun `a correct pin issues a grant that unlocks exactly this media`() = runTest {
        pinStore.createPin("1234")

        val result = useCase.unlock("1234", "movie_42") as ParentalUnlockResult.Unlocked

        assertTrue(grantStore.consume(1, "movie_42", result.nonce))
    }

    @Test
    fun `an incorrect pin issues no grant`() = runTest {
        pinStore.createPin("1234")

        val result = useCase.unlock("0000", "movie_42")

        assertEquals(ParentalUnlockResult.Incorrect, result)
    }

    @Test
    fun `a locked store refuses without comparing the pin`() = runTest {
        pinStore.createPin("1234")
        repeat(5) { pinStore.verifyPin("0000") }

        val result = useCase.unlock("1234", "movie_42") as ParentalUnlockResult.Locked
        assertTrue(result.remainingMillis > 0)
    }

    @Test
    fun `no remember target means no permanent authorization is persisted`() = runTest {
        pinStore.createPin("1234")

        useCase.unlock("1234", "movie_42")

        verifyNoInteractions(parentalAuthorizationRepository)
    }

    @Test
    fun `F45 - a correct pin with a remember target persists a permanent authorization`() = runTest {
        pinStore.createPin("1234")

        useCase.unlock("1234", "movie_42", ParentalAuthorizationTarget(MediaClassificationKind.MOVIE, 42))

        verify(parentalAuthorizationRepository).authorize(1, MediaClassificationKind.MOVIE, 42)
    }

    @Test
    fun `F45 - a remember target is ignored when the pin is incorrect`() = runTest {
        pinStore.createPin("1234")

        useCase.unlock("0000", "movie_42", ParentalAuthorizationTarget(MediaClassificationKind.MOVIE, 42))

        verifyNoInteractions(parentalAuthorizationRepository)
    }
}
