package com.cstv.app.presentation.player.core

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind

/** Catégorie d'échec de lecture qualifiée par [PlaybackFailureClassifier] (T23 §8.3). */
enum class PlaybackFailureType { DECODER, NETWORK_SOURCE, LIVE_WINDOW, UNKNOWN }

/**
 * Qualifie une [PlaybackException] Media3 pour décider si la séquence de réparation T23 (§8.4)
 * doit se déclencher — seule [PlaybackFailureType.DECODER] la déclenche (§7.4, cas limite : une
 * erreur réseau/flux introuvable laisse le comportement existant inchangé). Un code non reconnu
 * route vers [PlaybackFailureType.UNKNOWN], qui ne déclenche rien non plus, pour ne pas masquer
 * une panne réseau derrière trois reconstructions coûteuses (§8.3).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackFailureClassifier {

    private val DECODER_ERROR_CODES = setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
    )

    private val NETWORK_SOURCE_ERROR_CODES = setOf(
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )

    fun classify(error: PlaybackException): PlaybackFailureType = when {
        error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackFailureType.LIVE_WINDOW
        error.errorCode in DECODER_ERROR_CODES -> PlaybackFailureType.DECODER
        error.errorCode in NETWORK_SOURCE_ERROR_CODES -> PlaybackFailureType.NETWORK_SOURCE
        else -> PlaybackFailureType.UNKNOWN
    }

    /**
     * Empreinte de la piste responsable, quand elle est identifiable. `null` si l'erreur n'est
     * pas un `ExoPlaybackException` de type `TYPE_RENDERER`, ou si son format de piste ou son
     * type (audio/vidéo) est absent — la séquence se déroule alors à l'aveugle dans l'ordre fixe
     * (cas limite §7.4, hypothèse étape 1).
     */
    fun failedTrack(error: PlaybackException): TrackFingerprint? {
        val exoError = error as? ExoPlaybackException ?: return null
        if (exoError.type != ExoPlaybackException.TYPE_RENDERER) return null
        val format = exoError.rendererFormat ?: return null
        val trackKind = trackKindOf(format) ?: return null
        return TrackFingerprint(
            trackKind = trackKind,
            language = format.language,
            mimeType = format.sampleMimeType,
            codecs = format.codecs,
            channelCount = format.channelCount.takeIf { it != Format.NO_VALUE },
            roleFlags = format.roleFlags,
            label = format.label
        )
    }

    private fun trackKindOf(format: Format): TrackKind? = when (MimeTypes.getTrackType(format.sampleMimeType)) {
        C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
        C.TRACK_TYPE_VIDEO -> TrackKind.VIDEO
        else -> null
    }
}
