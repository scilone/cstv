package com.cstv.app.presentation.player.core

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Contrats de la télécommande : ce sont les seules règles de pilotage du player
 * vérifiables hors device, le focus D-pad et la composition ne l'étant pas dans
 * ce projet (`AGENTS.md`).
 */
class PlayerRemoteKeysTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Test
    fun `media keys are recognized regardless of the overlay state`() {
        assertEquals(PlayerKeyIntent.PlayPause, resolveMediaKeyIntent(Key.MediaPlayPause))
        assertEquals(PlayerKeyIntent.PlayPause, resolveMediaKeyIntent(Key.Spacebar))
        assertEquals(PlayerKeyIntent.Play, resolveMediaKeyIntent(Key.MediaPlay))
        assertEquals(PlayerKeyIntent.Pause, resolveMediaKeyIntent(Key.MediaPause))
        assertEquals(PlayerKeyIntent.FastForward, resolveMediaKeyIntent(Key.MediaFastForward))
        assertEquals(PlayerKeyIntent.Rewind, resolveMediaKeyIntent(Key.MediaRewind))
        assertEquals(PlayerKeyIntent.Next, resolveMediaKeyIntent(Key.MediaNext))
        assertEquals(PlayerKeyIntent.Next, resolveMediaKeyIntent(Key.MediaSkipForward))
        assertEquals(PlayerKeyIntent.Previous, resolveMediaKeyIntent(Key.MediaPrevious))
        assertEquals(PlayerKeyIntent.Previous, resolveMediaKeyIntent(Key.MediaSkipBackward))
        assertEquals(PlayerKeyIntent.Stop, resolveMediaKeyIntent(Key.MediaStop))
    }

    @Test
    fun `direction and back keys are never treated as media keys`() {
        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Back)
            .forEach { assertNull(resolveMediaKeyIntent(it)) }
    }

    @Test
    fun `arrows only navigate the options while the controls are visible`() {
        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
            .forEach { assertNull(resolveTvDpadIntent(it, controlsVisible = true)) }
    }

    @Test
    fun `arrows reveal the controls while they are hidden`() {
        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
            .forEach {
                assertEquals(PlayerKeyIntent.RevealControls, resolveTvDpadIntent(it, controlsVisible = false))
            }
    }

    @Test
    fun `center activates the focused option when the controls are visible`() {
        listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            .forEach { assertNull(resolveTvDpadIntent(it, controlsVisible = true)) }
    }

    @Test
    fun `center toggles playback when the controls are hidden`() {
        listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            .forEach {
                assertEquals(PlayerKeyIntent.PlayPause, resolveTvDpadIntent(it, controlsVisible = false))
            }
    }

    @Test
    fun `unrelated keys leave the dpad resolution untouched`() {
        listOf(Key.A, Key.Menu, Key.Back).forEach {
            assertNull(resolveTvDpadIntent(it, controlsVisible = true))
            assertNull(resolveTvDpadIntent(it, controlsVisible = false))
        }
    }
}
