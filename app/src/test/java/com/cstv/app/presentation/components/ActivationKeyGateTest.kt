package com.cstv.app.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationKeyGateTest {

    @Test
    fun orphanKeyUpAloneIsConsumed() {
        val gate = ActivationKeyGate()
        assertTrue(gate.onKeyUp())
    }

    @Test
    fun matchedKeyDownThenKeyUpIsNotConsumed() {
        val gate = ActivationKeyGate()
        gate.onKeyDown()
        assertFalse(gate.onKeyUp())
    }

    @Test
    fun orphanKeyUpThenFullPairOnlyConsumesFirst() {
        val gate = ActivationKeyGate()
        assertTrue(gate.onKeyUp())
        gate.onKeyDown()
        assertFalse(gate.onKeyUp())
    }

    @Test
    fun twoConsecutiveOrphanKeyUpsAreBothConsumed() {
        val gate = ActivationKeyGate()
        assertTrue(gate.onKeyUp())
        assertTrue(gate.onKeyUp())
    }
}
