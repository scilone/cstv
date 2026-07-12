package com.poc.iptvxtream.presentation.series

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.SeriesEpisode
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun SeriesPlayerScreen(
    episode: SeriesEpisode,
    seriesName: String,
    seriesCover: String?,
    credentials: Credentials,
    isTv: Boolean,
    viewModel: SeriesViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    var isPlayerVisible by remember { mutableStateOf(true) }

    val handleClose = {
        isPlayerVisible = false
        exoPlayer.stop()
        exoPlayer.clearVideoSurface()
        onClose()
    }

    androidx.activity.compose.BackHandler {
        handleClose()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableStateOf(episode.resumePositionMs) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Prepare & Play Episode
    LaunchedEffect(episode) {
        isBuffering = true
        playbackError = null
        
        val url = episode.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password
        )

        val mediaItem = MediaItem.fromUri(android.net.Uri.parse(url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        if (episode.resumePositionMs > 0) {
            exoPlayer.seekTo(episode.resumePositionMs)
        }
        
        exoPlayer.playWhenReady = true
    }

    // Save playback position loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                
                if (currentPosition > 0 && duration > 0) {
                    viewModel.savePosition(episode, currentPosition, duration, seriesName, seriesCover)
                }
            }
            delay(1000)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
                
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.clearPosition(episode.id)
                    handleClose()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "Erreur de lecture de l'épisode. Code codec non pris en charge."
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            val lastPos = exoPlayer.currentPosition
            val lastDur = exoPlayer.duration
            if (lastPos > 0 && lastDur > 0) {
                if (lastPos >= (lastDur - 15000L)) {
                    viewModel.clearPosition(episode.id)
                } else {
                    viewModel.savePosition(episode, lastPos, lastDur, seriesName, seriesCover)
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun skipForward() {
        val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
        exoPlayer.seekTo(newPos)
        currentPosition = newPos
        showControls = true
    }

    fun skipBackward() {
        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
        exoPlayer.seekTo(newPos)
        currentPosition = newPos
        showControls = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        showControls = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            skipBackward()
                            true
                        }
                        Key.DirectionRight -> {
                            skipForward()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            togglePlayPause()
                            true
                        }
                        Key.Back -> {
                            handleClose()
                            true
                        }
                        else -> {
                            showControls = true
                            false
                        }
                    }
                } else false
            }
            .focusable()
            .clickable { showControls = !showControls }
    ) {
        if (isPlayerVisible) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Prevent screen lock during playback
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val window = activity?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            }
        }

        playbackError?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60F0F13)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Impossible de lire cet épisode", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMsg, color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Réessayer")
                        }
                        OutlinedButton(onClick = handleClose, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Text("Retour")
                        }
                    }
                }
            }
        }

        if (showControls && playbackError == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(episode.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Épisode ${episode.episodeNum}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = handleClose,
                        modifier = Modifier.background(Color(0x40FFFFFF), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(24.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { fraction ->
                                val targetPos = (fraction * duration).toLong()
                                exoPlayer.seekTo(targetPos)
                                currentPosition = targetPos
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = ::skipBackward) {
                            Text("◄◄ 10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FloatingActionButton(
                            onClick = ::togglePlayPause,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = ::skipForward) {
                            Text("10s ►►", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isTv) {
                        Text(
                            text = "▲/▼ : Afficher contrôles  |  ◄/► : Reculer/Avancer de 10s  |  OK : Play/Pause",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val hr = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
