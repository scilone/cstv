package com.cstv.app.presentation.player.core

import androidx.media3.exoplayer.DefaultRenderersFactory
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerDecoderPolicyTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun `audio extension renderer mode stays PREFER`() {
        // EAC3/AC3/DTS doivent rester couverts par FFmpeg même quand un décodeur matériel
        // annonce le support du format sans le restituer correctement — comportement audio
        // d'origine, inchangé par B16.
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE
        )
    }

    @Test
    fun `video extension renderer mode is ON, not PREFER`() {
        // B16 : PREFER plaçait le décodeur vidéo logiciel FFmpeg avant le décodeur matériel,
        // corrompant l'image sur certains téléviseurs.
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE
        )
    }

    @Test
    fun `audio and video renderer modes are dissociated`() {
        // La régression relevée en review (M1) : un mode global unique changeait aussi la
        // priorité audio en corrigeant la vidéo. Les deux modes doivent rester distincts.
        assertNotEquals(
            PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE,
            PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE
        )
    }

    @Test
    fun `decoder fallback stays enabled`() {
        assertTrue(PlayerDecoderPolicy.ENABLE_DECODER_FALLBACK)
    }

    // --- T23 §8.1, review R2 : videoExtensionRendererMode(PlaybackRepairPlan) ---

    @Test
    fun `videoExtensionRendererMode DEFAULT keeps hardware-first video mode`() {
        assertEquals(
            PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE,
            PlayerDecoderPolicy.videoExtensionRendererMode(PlaybackRepairPlan.DEFAULT)
        )
    }

    @Test
    fun `videoExtensionRendererMode SOFTWARE_PREFERRED for a video failure switches video to PREFER`() {
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.VIDEO)
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerDecoderPolicy.videoExtensionRendererMode(plan))
    }

    @Test
    fun `videoExtensionRendererMode SOFTWARE_PREFERRED for an audio failure leaves video on hardware-first`() {
        // R2 : une erreur audio identifiée ne doit plus forcer la vidéo en logiciel — B16.
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO)
        assertEquals(PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE, PlayerDecoderPolicy.videoExtensionRendererMode(plan))
    }

    @Test
    fun `videoExtensionRendererMode SOFTWARE_PREFERRED with unidentified track stays conservative on video`() {
        // Séquence à l'aveugle (§7.4) : piste fautive inconnue, on tente aussi le logiciel vidéo.
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = null)
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerDecoderPolicy.videoExtensionRendererMode(plan))
    }

    @Test
    fun `SOFTWARE_PREFERRED for a video failure differs from DEFAULT`() {
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.VIDEO)
        assertNotEquals(
            PlayerDecoderPolicy.videoExtensionRendererMode(PlaybackRepairPlan.DEFAULT),
            PlayerDecoderPolicy.videoExtensionRendererMode(plan)
        )
    }
}
