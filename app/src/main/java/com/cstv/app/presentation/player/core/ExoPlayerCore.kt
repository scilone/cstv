package com.cstv.app.presentation.player.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.cstv.app.data.download.OfflineDownloadUtil
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

internal fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * `NextRenderersFactory` dont la construction des renderers vidéo ignore le mode d'extension
 * global reçu en paramètre (réglé pour préférer FFmpeg côté audio, voir [PlayerDecoderPolicy]) et
 * force [PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE], pour que le décodeur matériel du
 * téléviseur soit utilisé en priorité pour la vidéo sans changer la priorité audio (B16).
 */
@UnstableApi
private class VideoHardwarePreferredRenderersFactory(context: Context) : NextRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}

@UnstableApi
@Composable
fun rememberManagedExoPlayer(useOfflineCache: Boolean): ExoPlayer {
    val context = LocalContext.current

    val exoPlayer = remember {
        // Audio : FFmpeg (NextLib) reste préféré pour couvrir EAC3/AC3/DTS sur les appareils sans
        // décodeur matériel correspondant. Vidéo : le décodeur matériel est privilégié
        // (VideoHardwarePreferredRenderersFactory) — un décodeur logiciel préféré pour la vidéo
        // produit une image corrompue sur certains téléviseurs (cf. B16). Voir PlayerDecoderPolicy.
        val renderersFactory = VideoHardwarePreferredRenderersFactory(context)
            .setExtensionRendererMode(PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE)
            .setEnableDecoderFallback(PlayerDecoderPolicy.ENABLE_DECODER_FALLBACK)

        val builder = ExoPlayer.Builder(context, renderersFactory)
        if (useOfflineCache) {
            // Réservé aux VOD/Séries : le Live conserve sa source réseau actuelle.
            builder.setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OfflineDownloadUtil.getReadOnlyCacheDataSourceFactory(context)
                )
            )
        }
        builder.build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearVideoSurface()
            exoPlayer.release()
        }
    }

    return exoPlayer
}
