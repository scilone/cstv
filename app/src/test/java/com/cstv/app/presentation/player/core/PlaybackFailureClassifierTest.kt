package com.cstv.app.presentation.player.core

import android.os.SystemClock
import android.text.TextUtils
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import com.cstv.app.domain.model.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import java.io.IOException

class PlaybackFailureClassifierTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    /**
     * `ExoPlaybackException.createForRenderer`/`createForSource` et `Format.Builder().build()`
     * touchent `android.text.TextUtils.isEmpty` et `android.os.SystemClock.elapsedRealtime` en
     * interne (dérivation du message, horodatage) — non mockés par défaut dans les tests JVM
     * sans device (AGENTS.md, T23 §8.8 : « détails Media3 non exécutables en JVM contenus dans
     * des adapters minces »). Les deux static mocks ci-dessous ne remplacent aucune logique
     * testée, seulement ces deux effets de bord du SDK Android absents du stub jar.
     */
    private fun <T> withAndroidStubsMocked(block: () -> T): T =
        Mockito.mockStatic(TextUtils::class.java).use { textUtils ->
            textUtils.`when`<Boolean> { TextUtils.isEmpty(any()) }.thenAnswer { inv -> (inv.arguments[0] as? CharSequence).isNullOrEmpty() }
            Mockito.mockStatic(SystemClock::class.java).use { systemClock ->
                systemClock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(0L)
                block()
            }
        }

    private fun rendererError(errorCode: Int, format: Format? = null) = withAndroidStubsMocked {
        ExoPlaybackException.createForRenderer(
            RuntimeException("boom"), "AudioRenderer", 0, format, /* rendererFormatSupport = */ 0,
            /* isRecoverable = */ false, errorCode
        )
    }

    private fun sourceError(errorCode: Int) = withAndroidStubsMocked {
        ExoPlaybackException.createForSource(IOException("boom"), errorCode)
    }

    private fun audioFormat(language: String? = "fra", label: String? = "VFF") = withAndroidStubsMocked {
        Format.Builder()
            .setSampleMimeType("audio/eac3")
            .setLanguage(language)
            .setCodecs("ec-3")
            .setChannelCount(6)
            .setRoleFlags(0)
            .setLabel(label)
            .build()
    }

    // --- classify() : familles de codes décodeur ---

    @Test
    fun classify_decoderInitFailed_routesToDecoder() {
        assertEquals(PlaybackFailureType.DECODER, PlaybackFailureClassifier.classify(rendererError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
    }

    @Test
    fun classify_decodingFailed_routesToDecoder() {
        assertEquals(PlaybackFailureType.DECODER, PlaybackFailureClassifier.classify(rendererError(PlaybackException.ERROR_CODE_DECODING_FAILED)))
    }

    @Test
    fun classify_decodingFormatUnsupported_routesToDecoder() {
        assertEquals(PlaybackFailureType.DECODER, PlaybackFailureClassifier.classify(rendererError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
    }

    @Test
    fun classify_audioTrackInitFailed_routesToDecoder() {
        assertEquals(PlaybackFailureType.DECODER, PlaybackFailureClassifier.classify(rendererError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)))
    }

    // --- classify() : familles de codes réseau/source ---

    @Test
    fun classify_networkConnectionFailed_routesToNetworkSource() {
        assertEquals(PlaybackFailureType.NETWORK_SOURCE, PlaybackFailureClassifier.classify(sourceError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
    }

    @Test
    fun classify_badHttpStatus_routesToNetworkSource() {
        assertEquals(PlaybackFailureType.NETWORK_SOURCE, PlaybackFailureClassifier.classify(sourceError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
    }

    @Test
    fun classify_fileNotFound_routesToNetworkSource() {
        assertEquals(PlaybackFailureType.NETWORK_SOURCE, PlaybackFailureClassifier.classify(sourceError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)))
    }

    // --- classify() : live window et inconnu ---

    @Test
    fun classify_behindLiveWindow_routesToLiveWindow() {
        assertEquals(PlaybackFailureType.LIVE_WINDOW, PlaybackFailureClassifier.classify(sourceError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)))
    }

    @Test
    fun classify_unqualifiableCode_routesToUnknown_withoutTriggeringRepair() {
        assertEquals(PlaybackFailureType.UNKNOWN, PlaybackFailureClassifier.classify(sourceError(PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR)))
    }

    // --- failedTrack() : extraction de l'empreinte ---

    @Test
    fun failedTrack_audioRendererError_extractsAudioFingerprint() {
        val format = audioFormat()
        val error = rendererError(PlaybackException.ERROR_CODE_DECODING_FAILED, format)

        val fingerprint = withAndroidStubsMocked { PlaybackFailureClassifier.failedTrack(error) }

        assertEquals(TrackKind.AUDIO, fingerprint?.trackKind)
        // Format normalise "fra" (ISO 639-2/B) en "fr" (ISO 639-1) à la construction.
        assertEquals("fr", fingerprint?.language)
        assertEquals("audio/eac3", fingerprint?.mimeType)
        assertEquals("ec-3", fingerprint?.codecs)
        assertEquals(6, fingerprint?.channelCount)
        assertEquals("VFF", fingerprint?.label)
    }

    @Test
    fun failedTrack_videoRendererError_extractsVideoFingerprint() {
        val format = withAndroidStubsMocked { Format.Builder().setSampleMimeType("video/avc").build() }
        val error = rendererError(PlaybackException.ERROR_CODE_DECODING_FAILED, format)

        val fingerprint = withAndroidStubsMocked { PlaybackFailureClassifier.failedTrack(error) }

        assertEquals(TrackKind.VIDEO, fingerprint?.trackKind)
    }

    @Test
    fun failedTrack_sourceError_returnsNull() {
        // Type TYPE_SOURCE : rejeté avant tout accès au format, aucun stub Android nécessaire ici.
        assertNull(PlaybackFailureClassifier.failedTrack(sourceError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
    }

    @Test
    fun failedTrack_rendererErrorWithoutFormat_returnsNull() {
        // `rendererFormat` null : rejeté avant `MimeTypes.getTrackType`, aucun stub nécessaire ici.
        assertNull(PlaybackFailureClassifier.failedTrack(rendererError(PlaybackException.ERROR_CODE_DECODING_FAILED, format = null)))
    }

    @Test
    fun failedTrack_nonExoPlaybackException_returnsNull() {
        val genericError = withAndroidStubsMocked { PlaybackException("boom", null, PlaybackException.ERROR_CODE_DECODING_FAILED) }
        assertNull(PlaybackFailureClassifier.failedTrack(genericError))
    }
}
