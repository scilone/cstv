package com.cstv.app.presentation.player.core

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * T23 tâche 4 (§8.2, §8.7). `ExoPlayer` est une interface Media3 (mockable en JVM sans device) ;
 * la fabrique réelle (`buildExoPlayer`, adapter Android) est injectée pour ne tester que le cycle
 * stop/release/rebuild du contrôleur, pas la construction ExoPlayer elle-même.
 */
class PlaybackEngineControllerTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Test
    fun initialPlayer_isBuiltWithDefaultPlan() {
        val plans = mutableListOf<PlaybackRepairPlan>()
        val controller = PlaybackEngineController { plan -> plans.add(plan); mock() }

        assertEquals(listOf(PlaybackRepairPlan.DEFAULT), plans)
        assertEquals(PlaybackRepairPlan.DEFAULT.decoderStrategy, plans.single().decoderStrategy)
    }

    @Test
    fun rebuild_releasesThePreviousInstance_beforeBuildingTheNewOne() {
        val old = mock<ExoPlayer>()
        val new = mock<ExoPlayer>()
        var buildCount = 0
        val controller = PlaybackEngineController { buildCount++; if (buildCount == 1) old else new }

        assertSame(old, controller.player)

        val rebuilt = controller.rebuild(PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED))

        assertSame(new, rebuilt)
        assertSame(new, controller.player)
        verify(old).stop()
        verify(old).clearVideoSurface()
        verify(old).release()
        verify(new, never()).release()
    }

    @Test
    fun rebuild_buildsExactlyOneNewInstance() {
        var buildCount = 0
        val controller = PlaybackEngineController { buildCount++; mock() }

        controller.rebuild(PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED))

        // 1 pour l'instance initiale (plan DEFAULT) + 1 pour le rebuild — jamais deux instances
        // vivantes en même temps (§8.7), jamais de reconstruction en double par appel.
        assertEquals(2, buildCount)
    }

    @Test
    fun rebuild_passesThePlanUnchanged_toTheFactory() {
        val plans = mutableListOf<PlaybackRepairPlan>()
        val controller = PlaybackEngineController { plan -> plans.add(plan); mock() }
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED)

        controller.rebuild(plan)

        assertEquals(listOf(PlaybackRepairPlan.DEFAULT, plan), plans)
    }

    @Test
    fun setMediaItem_forwardsToTheCurrentPlayer() {
        val player = mock<ExoPlayer>()
        val controller = PlaybackEngineController { player }
        val item = mock<MediaItem>()

        controller.setMediaItem(item)

        verify(player).setMediaItem(item)
    }

    @Test
    fun rebuild_reattachesTheLastMediaItem_toTheNewInstance() {
        val old = mock<ExoPlayer>()
        val new = mock<ExoPlayer>()
        var buildCount = 0
        val controller = PlaybackEngineController { buildCount++; if (buildCount == 1) old else new }
        val item = mock<MediaItem>()

        controller.setMediaItem(item)
        controller.rebuild(PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED))

        verify(new).setMediaItem(item)
    }

    @Test
    fun rebuild_withoutAnyMediaItemSetYet_doesNotCallSetMediaItemOnTheNewInstance() {
        val new = mock<ExoPlayer>()
        var buildCount = 0
        val controller = PlaybackEngineController { buildCount++; if (buildCount == 1) mock() else new }

        controller.rebuild(PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED))

        verify(new, never()).setMediaItem(org.mockito.kotlin.any<MediaItem>())
    }

    @Test
    fun release_stopsAndReleasesTheCurrentPlayer() {
        val player = mock<ExoPlayer>()
        val controller = PlaybackEngineController { player }

        controller.release()

        verify(player).stop()
        verify(player).clearVideoSurface()
        verify(player).release()
    }
}
