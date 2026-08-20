package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ParentalAccessPolicyTest {

    private val policy = ParentalAccessPolicy()

    @Test
    fun `an unbridged profile is always allowed, regardless of classification`() {
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = null, classification = 18, action = ParentalActionType.PLAY)
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = null, classification = null, action = ParentalActionType.DOWNLOAD)
        )
    }

    @Test
    fun `an unknown classification is refused with UNCLASSIFIED, never treated as ALL`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = null, action = ParentalActionType.PLAY)
        assertEquals(AccessDecision.PinRequired(BlockReason.UNCLASSIFIED), decision)
    }

    @Test
    fun `a classification above the allowed level is refused with TOO_MATURE`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = 16, action = ParentalActionType.PLAY)
        assertEquals(AccessDecision.PinRequired(BlockReason.TOO_MATURE), decision)
    }

    @Test
    fun `a classification at or below the allowed level is allowed`() {
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = 12, action = ParentalActionType.PLAY)
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = 0, action = ParentalActionType.PLAY)
        )
    }

    @Test
    fun `DOWNLOAD follows the same defensive rule as PLAY for an unknown classification`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.TEN, classification = null, action = ParentalActionType.DOWNLOAD)
        assertEquals(AccessDecision.PinRequired(BlockReason.UNCLASSIFIED), decision)
    }

    @Test
    fun `a profile bridged to ALL still blocks any classified-above-ALL content`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.ALL, classification = 10, action = ParentalActionType.PLAY)
        assertEquals(AccessDecision.PinRequired(BlockReason.TOO_MATURE), decision)
    }

    // --- F45 §7.9 : exemples explicitement listés par le spec (âge exact, plus de palier) ---

    @Test
    fun `exact classifications compare directly against the profile threshold, 13 vs 12 and 16`() {
        assertEquals(
            AccessDecision.PinRequired(BlockReason.TOO_MATURE),
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = 13, action = ParentalActionType.PLAY),
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.SIXTEEN, classification = 13, action = ParentalActionType.PLAY),
        )
    }

    @Test
    fun `exact classifications compare directly against the profile threshold, 15 vs 12 and 16`() {
        assertEquals(
            AccessDecision.PinRequired(BlockReason.TOO_MATURE),
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = 15, action = ParentalActionType.PLAY),
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.SIXTEEN, classification = 15, action = ParentalActionType.PLAY),
        )
    }

    @Test
    fun `exact classifications compare directly against the profile threshold, 17 vs 16 and 18`() {
        assertEquals(
            AccessDecision.PinRequired(BlockReason.TOO_MATURE),
            policy.evaluate(maxAgeRating = AgeRating.SIXTEEN, classification = 17, action = ParentalActionType.PLAY),
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.EIGHTEEN, classification = 17, action = ParentalActionType.PLAY),
        )
    }
}
