package com.cstv.app.presentation.livetv.components

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.LiveStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class LiveChannelPreviewTest {

    @Test
    fun focusingAStreamMakesItTheCandidate() {
        assertEquals(42, resolvePreviewCandidate(currentCandidateId = null, streamId = 42, focused = true))
    }

    @Test
    fun unfocusingTheCurrentCandidateClearsIt() {
        assertNull(resolvePreviewCandidate(currentCandidateId = 42, streamId = 42, focused = false))
    }

    @Test
    fun staleUnfocusEventDoesNotClearANewerCandidate() {
        // Le KeyUp/onFocusChanged(false) de la carte 1 arrive après que la
        // carte 2 a déjà pris le focus : il ne doit pas effacer le candidat 2.
        assertEquals(2, resolvePreviewCandidate(currentCandidateId = 2, streamId = 1, focused = false))
    }

    @Test
    fun changingCandidateReplacesThePreviousOne() {
        assertEquals(2, resolvePreviewCandidate(currentCandidateId = 1, streamId = 2, focused = true))
    }

    // --- F25-R3 : cœur temporel (activatePreviewAfterDelay) ---

    @Test
    fun noCandidateDeactivatesImmediatelyWithoutWaiting() = runTest {
        var deactivated = false
        var activated: Int? = null
        activatePreviewAfterDelay(
            enabled = true,
            candidateId = null,
            deactivate = { deactivated = true },
            activate = { activated = it }
        )
        assertTrue(deactivated)
        assertNull(activated)
    }

    @Test
    fun disabledDeactivatesImmediatelyEvenWithACandidate() = runTest {
        var deactivated = false
        var activated: Int? = null
        activatePreviewAfterDelay(
            enabled = false,
            candidateId = 7,
            deactivate = { deactivated = true },
            activate = { activated = it }
        )
        assertTrue(deactivated)
        assertNull(activated)
    }

    @Test
    fun candidateHeldLessThanOneSecondNeverActivates() = runTest {
        var activated: Int? = null
        val job = launch {
            activatePreviewAfterDelay(
                enabled = true,
                candidateId = 7,
                deactivate = {},
                activate = { activated = it }
            )
        }
        advanceTimeBy(999)
        job.cancel()
        assertNull(activated)
    }

    @Test
    fun candidateHeldOneFullSecondActivatesOnce() = runTest {
        var activated: Int? = null
        activatePreviewAfterDelay(
            enabled = true,
            candidateId = 7,
            deactivate = {},
            activate = { activated = it }
        )
        assertEquals(7, activated)
    }

    @Test
    fun rapidCandidateChangeOnlyActivatesTheLastOne() = runTest {
        val activations = mutableListOf<Int>()
        // Modélise le redémarrage de LaunchedEffect(state.candidateId) par
        // Compose : la coroutine du candidat précédent est annulée dès
        // qu'un nouveau candidat se présente.
        var job = launch {
            activatePreviewAfterDelay(
                enabled = true, candidateId = 1, deactivate = {}, activate = { activations += it }
            )
        }
        advanceTimeBy(400)
        job.cancel()
        job = launch {
            activatePreviewAfterDelay(
                enabled = true, candidateId = 2, deactivate = {}, activate = { activations += it }
            )
        }
        advanceUntilIdle()

        assertEquals(listOf(2), activations)
    }

    // --- F25-R3 : cycle de vie de LiveChannelPreviewState (lecteur mocké) ---

    private fun testStream(id: Int) = LiveStream(
        streamId = id, name = "Chaîne $id", streamIcon = null, epgChannelId = null, num = id, categoryId = "1"
    )

    private fun testCredentials() = Credentials(host = "http://example.com", port = 80, username = "u", password = "p")

    // F25-R3 : `MediaItem.fromUri` passe par `android.net.Uri.parse`, non
    // mockable en JVM sans Robolectric (absent du projet) — voir le
    // paramètre `mediaItemFactory` de `activate`.
    private val fakeMediaItem: (String) -> MediaItem = { mock() }

    @Test
    fun activateStartsPlaybackOnTheCandidateStream() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        state.credentials = testCredentials()
        val stream = testStream(7)

        state.onFocusChanged(stream, focused = true)
        state.activate(7, fakeMediaItem)

        assertEquals(7, state.activeStreamId)
        verify(player).setMediaItem(any<MediaItem>())
        verify(player).prepare()
        verify(player).play()
    }

    @Test
    fun activateWithoutCredentialsDoesNothing() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        val stream = testStream(7)

        state.onFocusChanged(stream, focused = true)
        state.activate(7)

        assertNull(state.activeStreamId)
        verify(player, never()).play()
    }

    @Test
    fun deactivateStopsAndClearsThePlayer() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        state.credentials = testCredentials()
        state.onFocusChanged(testStream(7), focused = true)
        state.activate(7, fakeMediaItem)

        state.deactivate()

        assertNull(state.activeStreamId)
        verify(player).stop()
        verify(player).clearMediaItems()
    }

    @Test
    fun deactivateWithoutAnActiveStreamIsANoOp() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)

        state.deactivate()

        verify(player, never()).stop()
    }

    @Test
    fun stopClearsCandidateAndActiveStreamAndStopsThePlayer() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        state.credentials = testCredentials()
        state.onFocusChanged(testStream(7), focused = true)
        state.activate(7, fakeMediaItem)

        state.stop()

        assertNull(state.candidateId)
        assertNull(state.activeStreamId)
        verify(player).stop()
        verify(player).clearMediaItems()
    }

    @Test
    fun markFailedDeactivatesTheCurrentlyActiveStream() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        state.credentials = testCredentials()
        state.onFocusChanged(testStream(7), focused = true)
        state.activate(7, fakeMediaItem)

        state.markFailed(7)

        assertNull(state.activeStreamId)
        verify(player).stop()
    }

    @Test
    fun aFailedStreamIsNeverRetried() {
        val player = mock<ExoPlayer>()
        val state = LiveChannelPreviewState(player)
        state.credentials = testCredentials()
        state.onFocusChanged(testStream(7), focused = true)
        state.activate(7, fakeMediaItem)
        state.markFailed(7)

        // La chaîne redevient candidate (nouveau focus) puis on retente : plus
        // aucune tentative de lecture ne doit avoir lieu.
        state.onFocusChanged(testStream(7), focused = true)
        state.activate(7, fakeMediaItem)

        // `play()` a bien eu lieu une fois lors de la tentative initiale
        // (avant l'échec) ; ce qui compte est l'absence de seconde tentative.
        assertNull(state.activeStreamId)
        verify(player, times(1)).play()
    }
}
