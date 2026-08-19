package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ParentalAccessPolicyTest {

    private val policy = ParentalAccessPolicy()

    @Test
    fun `an unbridged profile is always allowed, regardless of classification`() {
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = null, classification = AgeRating.EIGHTEEN, action = ParentalActionType.PLAY)
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
        val decision = policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = AgeRating.SIXTEEN, action = ParentalActionType.PLAY)
        assertEquals(AccessDecision.PinRequired(BlockReason.TOO_MATURE), decision)
    }

    @Test
    fun `a classification at or below the allowed level is allowed`() {
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = AgeRating.TWELVE, action = ParentalActionType.PLAY)
        )
        assertEquals(
            AccessDecision.Allowed,
            policy.evaluate(maxAgeRating = AgeRating.TWELVE, classification = AgeRating.ALL, action = ParentalActionType.PLAY)
        )
    }

    @Test
    fun `DOWNLOAD follows the same defensive rule as PLAY for an unknown classification`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.TEN, classification = null, action = ParentalActionType.DOWNLOAD)
        assertEquals(AccessDecision.PinRequired(BlockReason.UNCLASSIFIED), decision)
    }

    @Test
    fun `a profile bridged to ALL still blocks any classified-above-ALL content`() {
        val decision = policy.evaluate(maxAgeRating = AgeRating.ALL, classification = AgeRating.TEN, action = ParentalActionType.PLAY)
        assertEquals(AccessDecision.PinRequired(BlockReason.TOO_MATURE), decision)
    }
}
