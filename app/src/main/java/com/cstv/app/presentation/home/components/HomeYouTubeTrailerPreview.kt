package com.cstv.app.presentation.home.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.cstv.app.presentation.debug.DebugLog

/** Un player seulement, possédé par la page mobile active et libéré à sa sortie. */
@Composable
internal fun HomeYouTubeTrailerPreview(
    videoId: String,
    muted: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.key(videoId) {
        var player by remember { mutableStateOf<YouTubePlayer?>(null) }
        var playerView by remember { mutableStateOf<YouTubePlayerView?>(null) }
        AndroidView(
            factory = { context ->
                YouTubePlayerView(context).apply {
                    playerView = this
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // Pattern canonique de la lib : la vue observe le lifecycle et
                    // s'initialise via celui-ci. L'init manuelle sans lifecycle est la
                    // cause fréquente d'un onError instantané après onReady (WebView dans
                    // un état incohérent). controls(0) = aperçu sans contrôles ; l'origine
                    // par défaut (https://www.youtube.com) est requise par l'IFrame API.
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)
                    val options = IFramePlayerOptions.Builder()
                        .controls(0)
                        .rel(0)
                        .build()
                    initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            DebugLog.log("F10Trailer", "onReady videoId=$videoId")
                            player = youTubePlayer
                            youTubePlayer.mute()
                            youTubePlayer.loadVideo(videoId, 0f)
                        }

                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            // Boucle : relance en fin de vidéo. Reprend aussi sur une
                            // pause non sollicitée (l'aperçu n'a aucun contrôle play/pause,
                            // seulement le son) : sur certaines WebView l'autoplay muet
                            // passe en PAUSED après ~1 s, ce qui figeait l'aperçu.
                            DebugLog.log("F10Trailer", "onStateChange videoId=$videoId state=$state")
                            when (state) {
                                PlayerConstants.PlayerState.ENDED -> youTubePlayer.loadVideo(videoId, 0f)
                                PlayerConstants.PlayerState.PAUSED -> youTubePlayer.play()
                                else -> Unit
                            }
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            DebugLog.log("F10Trailer", "onError videoId=$videoId error=$error")
                            onPlaybackError()
                        }
                    }, true, options)
                }
            },
            update = {
                if (muted) player?.mute() else player?.unMute()
            },
            modifier = Modifier.fillMaxSize()
        )
        DisposableEffect(Unit) {
            onDispose {
                player?.pause()
                playerView?.let { view ->
                    lifecycleOwner.lifecycle.removeObserver(view)
                    view.release()
                }
            }
        }
    }
}
