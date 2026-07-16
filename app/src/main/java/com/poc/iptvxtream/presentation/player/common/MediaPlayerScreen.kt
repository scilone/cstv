package com.poc.iptvxtream.presentation.player.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.poc.iptvxtream.R
import com.poc.iptvxtream.domain.model.SubtitleStyle
import com.poc.iptvxtream.domain.model.TrackPreference
import com.poc.iptvxtream.presentation.player.applySubtitleStyle
import com.poc.iptvxtream.presentation.theme.PlayerScrim
import com.poc.iptvxtream.presentation.theme.ScrimMedium
import com.poc.iptvxtream.presentation.theme.WhiteOverlay20
import com.poc.iptvxtream.presentation.theme.WhiteOverlay25
import kotlinx.coroutines.delay

/**
 * Briques communes des players VOD et Séries (audit #6). Les deux écrans
 * partageaient ~850 lignes quasi identiques : setup/release ExoPlayer,
 * overlay de contrôles (titre, timeline, play/pause, ±10s, fermeture),
 * sélection de pistes audio/sous-titres avec persistance (Phase 29),
 * style des sous-titres, sauvegarde périodique de la position.
 * Chaque écran reste un wrapper fin qui fournit l'URL, les métadonnées et
 * les callbacks de persistance propres à son média.
 *
 * Le player live (PlayerScreen) garde sa propre structure (zapping, EPG,
 * pas de timeline) et ne réutilise que [findActivity]/[formatTime].
 */

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

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean,
    val mediaTrackGroup: androidx.media3.common.TrackGroup
)

/**
 * Écran player complet pour un média à la demande (film ou épisode).
 *
 * @param mediaUrl URL directe du flux.
 * @param initialPositionMs position de reprise (0 = depuis le début).
 * @param title titre affiché dans le panneau haut.
 * @param subtitleLine seconde ligne du panneau haut (genre ou numéro d'épisode).
 * @param subtitleStyle style des sous-titres (SettingsManager).
 * @param trackPreference préférence de pistes mémorisée pour ce média
 *   (Phase 29), prioritaire sur les fallbacks globaux ; null tant que non
 *   chargée. Le wrapper la met à jour quand l'utilisateur choisit une piste.
 * @param globalPreferredAudio/globalPreferredSubtitle fallbacks globaux
 *   "dernière langue utilisée".
 * @param showLoadingLabel true pour afficher le texte "Chargement du flux…"
 *   sous le spinner (comportement historique du player VOD ; le player
 *   séries n'affichait que le spinner — divergence d'origine préservée).
 * @param onSavePosition persistance périodique (1 s de boucle, le repository
 *   throttle) et à la fermeture.
 * @param onClearPosition position supprimée (lecture terminée ou < 15 s de la fin).
 * @param onAudioSelected/onSubtitleSelected persistance du choix de piste
 *   ("none" = sous-titres désactivés).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    mediaUrl: String,
    initialPositionMs: Long,
    title: String,
    subtitleLine: String,
    subtitleStyle: SubtitleStyle,
    trackPreference: TrackPreference?,
    globalPreferredAudio: () -> String?,
    globalPreferredSubtitle: () -> String?,
    showLoadingLabel: Boolean,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onClearPosition: () -> Unit,
    onAudioSelected: (language: String?) -> Unit,
    onSubtitleSelected: (language: String?) -> Unit,
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

    // Prevent screen lock during playback
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableStateOf(initialPositionMs) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Dynamic Tracks States (Audio & Subtitles)
    var availableAudioTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var availableSubtitleTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var showTrackDialog by remember { mutableStateOf(false) }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Prepare and play
    LaunchedEffect(mediaUrl) {
        isBuffering = true
        playbackError = null

        val mediaItem = MediaItem.fromUri(android.net.Uri.parse(mediaUrl))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Seek to saved position if requested
        if (initialPositionMs > 0) {
            exoPlayer.seekTo(initialPositionMs)
        }

        exoPlayer.playWhenReady = true
    }

    // Function to rebuild list of available tracks
    val updateTracksState: (Tracks) -> Unit = { tracks ->
        val audios = mutableListOf<TrackInfo>()
        val subtitles = mutableListOf<TrackInfo>()

        for (gIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[gIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIndex in 0 until group.length) {
                    val format = group.getTrackFormat(tIndex)
                    val label = format.label ?: format.language ?: "Langue ${audios.size + 1}"
                    audios.add(
                        TrackInfo(
                            groupIndex = gIndex,
                            trackIndex = tIndex,
                            language = format.language,
                            label = label,
                            isSelected = group.isTrackSelected(tIndex),
                            mediaTrackGroup = group.mediaTrackGroup
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIndex in 0 until group.length) {
                    val format = group.getTrackFormat(tIndex)
                    val label = format.label ?: format.language ?: "Sous-titre ${subtitles.size + 1}"
                    subtitles.add(
                        TrackInfo(
                            groupIndex = gIndex,
                            trackIndex = tIndex,
                            language = format.language,
                            label = label,
                            isSelected = group.isTrackSelected(tIndex),
                            mediaTrackGroup = group.mediaTrackGroup
                        )
                    )
                }
            }
        }
        availableAudioTracks = audios
        availableSubtitleTracks = subtitles
    }

    // Auto-apply preferred languages : préférence mémorisée pour CE média
    // d'abord (Phase 29), sinon fallback global "dernière langue utilisée".
    val applyPreferredLanguages: (List<TrackInfo>, List<TrackInfo>) -> Unit = { audios, subs ->
        val prefAudio = trackPreference?.audioLang ?: globalPreferredAudio()
        val prefSub = trackPreference?.subtitleLang ?: globalPreferredSubtitle()

        var updatedParams = exoPlayer.trackSelectionParameters.buildUpon()

        // Apply preferred audio
        if (!prefAudio.isNullOrBlank()) {
            val matchingAudio = audios.find { it.language?.equals(prefAudio, ignoreCase = true) == true }
            if (matchingAudio != null && !matchingAudio.isSelected) {
                updatedParams = updatedParams.setOverrideForType(
                    TrackSelectionOverride(
                        matchingAudio.mediaTrackGroup,
                        matchingAudio.trackIndex
                    )
                )
            }
        }

        // Apply preferred subtitle
        if (prefSub != null) {
            if (prefSub == "none" || prefSub.isBlank()) {
                updatedParams = updatedParams
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                val matchingSub = subs.find { it.language?.equals(prefSub, ignoreCase = true) == true }
                if (matchingSub != null) {
                    updatedParams = updatedParams
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                matchingSub.mediaTrackGroup,
                                matchingSub.trackIndex
                            )
                        )
                }
            }
        }
        exoPlayer.trackSelectionParameters = updatedParams.build()
    }

    // Position & Duration Tracking Loop (Runs every 1 second)
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)

                if (currentPosition > 0 && duration > 0) {
                    onSavePosition(currentPosition, duration)
                }
            }
            delay(1000)
        }
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }

                // If media completes (reaches end), delete saved position
                if (playbackState == Player.STATE_ENDED) {
                    onClearPosition()
                    handleClose()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "Impossible de charger le flux vidéo (${error.localizedMessage})"
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksState(tracks)
            }
        }

        exoPlayer.addListener(listener)

        // Force initial update of tracks
        updateTracksState(exoPlayer.currentTracks)

        onDispose {
            val lastPos = exoPlayer.currentPosition
            val lastDur = exoPlayer.duration
            if (lastPos > 0 && lastDur > 0) {
                // If we are within 15 seconds of the end, consider completed
                if (lastPos >= (lastDur - 15000L)) {
                    onClearPosition()
                } else {
                    onSavePosition(lastPos, lastDur)
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto-apply preferences as soon as tracks are first discovered
    LaunchedEffect(availableAudioTracks, availableSubtitleTracks) {
        if (availableAudioTracks.isNotEmpty() || availableSubtitleTracks.isNotEmpty()) {
            applyPreferredLanguages(availableAudioTracks, availableSubtitleTracks)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        showControls = true
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

    // Full screen capture key events for TV and gesture capture
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
        // Android Video View
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
                        subtitleView?.applySubtitleStyle(subtitleStyle)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Buffering Indicator
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showLoadingLabel) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.player_loading), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                }
            }
        }

        // Playback Error Overlay with Retry
        playbackError?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlayerScrim),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.player_error_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMsg,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
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
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.player_retry))
                        }

                        OutlinedButton(
                            onClick = handleClose,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.player_back))
                        }
                    }
                }
            }
        }

        // Custom Media Controls Overlay
        if (showControls && playbackError == null) {
            // Top Panel: Title and Exit/Settings Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScrimMedium)
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleLine,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Display Track Selector button if there are alternative audios or any subtitles
                        val hasMultipleAudio = availableAudioTracks.size > 1
                        val hasSubtitles = availableSubtitleTracks.isNotEmpty()

                        if (hasMultipleAudio || hasSubtitles) {
                            IconButton(
                                onClick = { showTrackDialog = true },
                                modifier = Modifier.background(WhiteOverlay25, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Pistes Audio & Sous-titres", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        IconButton(
                            onClick = handleClose,
                            modifier = Modifier.background(WhiteOverlay25, shape = RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.player_close), tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Panel: Timeline and Play/Skip Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScrimMedium)
                    .padding(24.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Timeline Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = {
                                exoPlayer.seekTo(it.toLong())
                                currentPosition = it.toLong()
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
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
                            fontSize = 12.sp
                        )
                    }

                    // Control Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { skipBackward() }) {
                            Text(stringResource(R.string.player_rewind_10), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { togglePlayPause() },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(WhiteOverlay20, shape = RoundedCornerShape(27.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPlaying) {
                                    // Pause icon absent from core Material icons, drawn manually
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 8.dp, height = 26.dp)
                                                .background(Color.White, RoundedCornerShape(2.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(width = 8.dp, height = 26.dp)
                                                .background(Color.White, RoundedCornerShape(2.dp))
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { skipForward() }) {
                            Text(stringResource(R.string.player_forward_10), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Track Selection Dialog Menu
        if (showTrackDialog) {
            TrackSelectionDialog(
                availableAudioTracks = availableAudioTracks,
                availableSubtitleTracks = availableSubtitleTracks,
                onAudioTrackSelected = { track ->
                    onAudioSelected(track.language)
                    val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(
                                track.mediaTrackGroup,
                                track.trackIndex
                            )
                        )
                        .build()
                    exoPlayer.trackSelectionParameters = newParams
                    // Refresh tracks list state
                    updateTracksState(exoPlayer.currentTracks)
                },
                onSubtitleTrackSelected = { track ->
                    if (track == null) {
                        onSubtitleSelected("none")
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                    } else {
                        onSubtitleSelected(track.language)
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    track.mediaTrackGroup,
                                    track.trackIndex
                                )
                            )
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                    }
                    updateTracksState(exoPlayer.currentTracks)
                },
                onDismiss = { showTrackDialog = false }
            )
        }
    }
}
