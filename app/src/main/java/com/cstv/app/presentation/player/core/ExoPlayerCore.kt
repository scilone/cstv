package com.cstv.app.presentation.player.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

@UnstableApi
@Composable
fun rememberManagedExoPlayer(useOfflineCache: Boolean): ExoPlayer {
    val context = LocalContext.current

    val exoPlayer = remember {
        // Décodeurs FFmpeg (NextLib) préférés pour l'audio : lit EAC3/AC3/DTS
        // même sur les appareils sans décodeur matériel de ces codecs.
        val renderersFactory = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            
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
