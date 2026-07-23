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
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

/** Un player seulement, possédé par la page mobile active et libéré à sa sortie. */
@Composable
internal fun HomeYouTubeTrailerPreview(
    videoId: String,
    muted: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(videoId) {
        var player by remember { mutableStateOf<YouTubePlayer?>(null) }
        var playerView by remember { mutableStateOf<YouTubePlayerView?>(null) }
        AndroidView(
            factory = { context ->
                YouTubePlayerView(context).apply {
                    playerView = this
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    enableAutomaticInitialization = false
                    initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                            youTubePlayer.mute()
                            youTubePlayer.loadVideo(videoId, 0f)
                        }

                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            if (state == PlayerConstants.PlayerState.ENDED) youTubePlayer.loadVideo(videoId, 0f)
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            onPlaybackError()
                        }
                    }, true)
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
                playerView?.release()
            }
        }
    }
}
