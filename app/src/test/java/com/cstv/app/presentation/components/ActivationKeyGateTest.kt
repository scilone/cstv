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
        gate.onKeyDown(isRepeat = false)
        assertFalse(gate.onKeyUp())
    }

    @Test
    fun orphanKeyUpThenFullPairOnlyConsumesFirst() {
        val gate = ActivationKeyGate()
        assertTrue(gate.onKeyUp())
        gate.onKeyDown(isRepeat = false)
        assertFalse(gate.onKeyUp())
    }

    @Test
    fun twoConsecutiveOrphanKeyUpsAreBothConsumed() {
        val gate = ActivationKeyGate()
        assertTrue(gate.onKeyUp())
        assertTrue(gate.onKeyUp())
    }

    @Test
    fun repeatedKeyDownInheritedFromAnotherWindowDoesNotPairTheKeyUp() {
        // Fenêtre ouverte par un appui long : la touche est encore enfoncée,
        // Android livre ses KeyDown de répétition à la nouvelle fenêtre avant
        // le KeyUp. Sans cette règle, la boîte se refermait toujours seule.
        val gate = ActivationKeyGate()
        gate.onKeyDown(isRepeat = true)
        gate.onKeyDown(isRepeat = true)
        assertTrue(gate.onKeyUp())
    }

    @Test
    fun freshPressFollowedByItsOwnRepeatsStillActivates() {
        // Pression née dans la fenêtre : maintenir la touche ne doit pas
        // annuler l'activation attendue au relâchement.
        val gate = ActivationKeyGate()
        gate.onKeyDown(isRepeat = false)
        gate.onKeyDown(isRepeat = true)
        assertFalse(gate.onKeyUp())
    }

    @Test
    fun inheritedPressThenGenuinePressActivatesOnlyTheSecond() {
        val gate = ActivationKeyGate()
        gate.onKeyDown(isRepeat = true)
        assertTrue(gate.onKeyUp())
        gate.onKeyDown(isRepeat = false)
        assertFalse(gate.onKeyUp())
    }
}
