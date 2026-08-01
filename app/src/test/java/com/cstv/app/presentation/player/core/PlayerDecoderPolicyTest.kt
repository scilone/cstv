package com.cstv.app.presentation.player.core

import androidx.media3.exoplayer.DefaultRenderersFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerDecoderPolicyTest {

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
}
