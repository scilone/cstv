package com.cstv.app.data.security

import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ParentalPinStoreTest {

    private var wallNow = 0L
    private var elapsedNow = 0L
    private val timeProvider = object : TimeProvider { override fun nowMillis() = wallNow }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = elapsedNow }
    private lateinit var store: ParentalPinStore

    @Before
    fun setUp() {
        store = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
    }

    @Test
    fun `no pin created yet reports hasPin false`() {
        assertFalse(store.hasPin())
    }

    @Test
    fun `a freshly created pin can be verified`() {
        store.createPin("1234")
        assertTrue(store.hasPin())
        assertEquals(PinVerificationResult.Correct, store.verifyPin("1234"))
    }

    @Test
    fun `a wrong pin is rejected without touching the stored hash`() {
        store.createPin("1234")
        assertEquals(PinVerificationResult.Incorrect, store.verifyPin("0000"))
        assertEquals(PinVerificationResult.Correct, store.verifyPin("1234"))
    }

    @Test
    fun `the fifth consecutive failure locks for 30 seconds`() {
        store.createPin("1234")
        repeat(4) { assertEquals(PinVerificationResult.Incorrect, store.verifyPin("0000")) }
        val result = store.verifyPin("0000")
        assertEquals(PinVerificationResult.JustLocked(ParentalPinStore.INITIAL_LOCK_MS), result)
    }

    @Test
    fun `attempts made while locked are refused without being compared`() {
        store.createPin("1234")
        repeat(5) { store.verifyPin("0000") }

        // Même le bon PIN est refusé pendant la temporisation.
        val result = store.verifyPin("1234")
        assertTrue(result is PinVerificationResult.Locked)
    }

    @Test
    fun `each new lockout doubles the previous duration up to the 15 minute cap`() {
        store.createPin("1234")

        fun lockOnceAndExpire(): Long {
            repeat(4) { store.verifyPin("0000") }
            val locked = store.verifyPin("0000") as PinVerificationResult.JustLocked
            // Laisse expirer le blocage sur les deux horloges (§8.3) : seule
            // l'horloge murale ne suffit pas (protection anti-retour d'horloge).
            wallNow += locked.remainingMillis
            elapsedNow += locked.remainingMillis
            return locked.remainingMillis
        }

        assertEquals(30_000L, lockOnceAndExpire())
        assertEquals(60_000L, lockOnceAndExpire())
        assertEquals(120_000L, lockOnceAndExpire())
        assertEquals(240_000L, lockOnceAndExpire())
        assertEquals(480_000L, lockOnceAndExpire())
        assertEquals(900_000L, lockOnceAndExpire()) // plafond 15 min
        assertEquals(900_000L, lockOnceAndExpire()) // reste plafonné
    }

    @Test
    fun `a correct pin resets both the failure counter and the lock level`() {
        store.createPin("1234")
        repeat(4) { store.verifyPin("0000") } // 4 échecs, pas encore de blocage
        assertEquals(PinVerificationResult.Correct, store.verifyPin("1234"))

        // Le compteur est reparti à zéro : il faut de nouveau 5 échecs, pas 1.
        repeat(4) { assertEquals(PinVerificationResult.Incorrect, store.verifyPin("0000")) }
        assertTrue(store.verifyPin("0000") is PinVerificationResult.JustLocked)
    }

    @Test
    fun `wall clock rollback alone does not lift an active lock`() {
        store.createPin("1234")
        repeat(5) { store.verifyPin("0000") }
        assertTrue(store.remainingLockMillis()!! > 0)

        // Retour d'horloge murale en arrière : le verrou basé sur le temps
        // écoulé pendant le process protège quand même.
        wallNow -= 60_000L
        assertTrue(store.remainingLockMillis()!! > 0)
    }

    @Test
    fun `the lock lifts once its duration has really elapsed`() {
        store.createPin("1234")
        repeat(5) { store.verifyPin("0000") }
        wallNow += ParentalPinStore.INITIAL_LOCK_MS
        elapsedNow += ParentalPinStore.INITIAL_LOCK_MS

        assertEquals(null, store.remainingLockMillis())
        assertEquals(PinVerificationResult.Correct, store.verifyPin("1234"))
    }

    @Test
    fun `replacePin overwrites the pin and clears lockout state`() {
        store.createPin("1234")
        repeat(5) { store.verifyPin("0000") }
        assertTrue(store.remainingLockMillis()!! > 0)

        store.replacePin("5678")

        assertEquals(null, store.remainingLockMillis())
        assertEquals(PinVerificationResult.Correct, store.verifyPin("5678"))
        assertEquals(PinVerificationResult.Incorrect, store.verifyPin("1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non 4-digit pin is rejected at creation`() {
        store.createPin("12345")
    }

    @Test
    fun `the plaintext pin is never stored`() {
        val prefs = FakeSharedPreferences()
        ParentalPinStore(prefs, timeProvider, monotonicClock).createPin("1234")
        assertFalse(prefs.all.values.any { it == "1234" })
    }
}
