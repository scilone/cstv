package com.poc.iptvxtream.presentation.series

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import coil.compose.AsyncImage
import com.poc.iptvxtream.data.local.storage.ResizeMode
import androidx.media3.ui.PlayerView
import com.poc.iptvxtream.presentation.player.applySubtitleStyle
import com.poc.iptvxtream.presentation.player.PlayerTopButton
import com.poc.iptvxtream.presentation.player.TransportButton
import com.poc.iptvxtream.presentation.player.PlayPauseButton
import com.poc.iptvxtream.presentation.player.PlayerBottomAction
import com.poc.iptvxtream.presentation.player.ResolutionBadge
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.SeriesEpisode
import com.poc.iptvxtream.domain.model.computeNextEpisode
import com.poc.iptvxtream.domain.model.computePreviousEpisode
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

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean,
    val mediaTrackGroup: androidx.media3.common.TrackGroup
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SeriesPlayerScreen(
    episode: SeriesEpisode,
    seriesId: Int,
    seriesName: String,
    seriesCover: String?,
    seriesEpisodes: Map<Int, List<SeriesEpisode>>,
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
    var currentResizeMode by remember { mutableStateOf(viewModel.getResizeMode()) }
    var resizeModeNotification by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resizeModeNotification) {
        if (resizeModeNotification != null) {
            delay(2000)
            resizeModeNotification = null
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val componentActivity = remember(context) { context.findActivity() as? androidx.activity.ComponentActivity }
    var isInPipMode by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                componentActivity?.isInPictureInPictureMode == true
            } else {
                false
            }
        )
    }

    DisposableEffect(componentActivity) {
        if (componentActivity == null) return@DisposableEffect onDispose {}
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(isInPipMode) {
        // Le SurfaceView interne au PlayerView ne se relayout pas tout seul
        // au redimensionnement de la fenêtre PIP (bug connu ExoPlayer/Media3) :
        // un cycle invisible/visible force un vrai passage de layout.
        playerViewRef?.let { view ->
            view.visibility = android.view.View.INVISIBLE
            view.post { view.visibility = android.view.View.VISIBLE }
        }
    }

    // Épisode actuellement lu (Phase 59) : initialisé sur celui reçu en
    // navigation, puis remplacé en interne à chaque enchaînement (auto en fin
    // de lecture ou via le bouton « épisode suivant ») sans repasser par la
    // navigation. Toute la logique du player (URL, sauvegarde de position,
    // titre) s'appuie sur cette valeur plutôt que sur le paramètre initial.
    var currentEpisode by remember { mutableStateOf(episode) }

    // Épisode suivant selon la carte des saisons/épisodes de la série ; null en
    // fin de série ou quand la map n'est pas disponible (reprise depuis
    // l'accueil) → ni autoplay ni bouton.
    val nextEpisode = computeNextEpisode(seriesEpisodes, currentEpisode)

    // Épisode précédent (même saison, ou dernier épisode de la saison
    // précédente) ; null en tout début de série → pas de bouton.
    val previousEpisode = computePreviousEpisode(seriesEpisodes, currentEpisode)

    // Feedback visuel transitoire du double-tap avance/recul rapide (10s).
    var seekNotification by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekNotification) {
        if (seekNotification != null) {
            delay(1000)
            seekNotification = null
        }
    }

    // Préférence de pistes mémorisée pour CETTE série (Phase 29, commune à tous
    // les épisodes). Prioritaire sur le fallback global.
    var seriesPref by remember { mutableStateOf<com.poc.iptvxtream.domain.model.TrackPreference?>(null) }
    LaunchedEffect(seriesId) {
        seriesPref = viewModel.getSeriesTrackPreference(seriesId)
    }

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
    var currentPosition by remember { mutableStateOf(episode.resumePositionMs) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Dynamic Tracks States (Audio & Subtitles)
    var availableAudioTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var availableSubtitleTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var showTrackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Prepare & Play Episode. Clé = currentEpisode : rejoué à chaque
    // enchaînement (Phase 59), pas seulement à l'ouverture initiale.
    LaunchedEffect(currentEpisode) {
        isBuffering = true
        playbackError = null

        val url = currentEpisode.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password
        )

        val mediaItem = MediaItem.fromUri(android.net.Uri.parse(url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Seek to saved position if requested
        if (currentEpisode.resumePositionMs > 0) {
            exoPlayer.seekTo(currentEpisode.resumePositionMs)
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

    // Auto-apply preferred languages: préférence de la série d'abord, sinon
    // fallback global (dernière langue utilisée) — Phase 29.
    val applyPreferredLanguages: (List<TrackInfo>, List<TrackInfo>) -> Unit = { audios, subs ->
        val prefAudio = seriesPref?.audioLang ?: viewModel.getPreferredAudio()
        val prefSub = seriesPref?.subtitleLang ?: viewModel.getPreferredSubtitle()

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

    // Enchaînement vers un autre épisode sans quitter le player (Phase 59).
    // finishedCurrent = true : l'épisode courant est allé au bout (STATE_ENDED)
    // → on efface sa position de reprise. Sinon (clic « épisode suivant ») on
    // sauvegarde la position courante si elle est significative. Réinitialise
    // ensuite l'état de lecture et bascule sur le nouvel épisode (déclenche le
    // rechargement via LaunchedEffect(currentEpisode)).
    val goToEpisode: (SeriesEpisode, Boolean) -> Unit = { next, finishedCurrent ->
        val pos = exoPlayer.currentPosition
        val dur = exoPlayer.duration
        if (finishedCurrent || pos <= 0 || dur <= 0 || pos >= dur - 15000L) {
            viewModel.clearPosition(currentEpisode.id)
        } else {
            viewModel.savePosition(currentEpisode, pos, dur, seriesName, seriesCover)
        }
        playbackError = null
        isBuffering = true
        currentPosition = next.resumePositionMs
        duration = 0L
        availableAudioTracks = emptyList()
        availableSubtitleTracks = emptyList()
        showControls = true
        currentEpisode = next
    }

    // Save playback position loop (Runs every 1 second)
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)

                if (currentPosition > 0 && duration > 0) {
                    viewModel.savePosition(currentEpisode, currentPosition, duration, seriesName, seriesCover)
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
                
                if (playbackState == Player.STATE_ENDED) {
                    // Fin d'épisode : enchaîner sur le suivant s'il existe
                    // (Phase 59), sinon comportement historique (fermeture).
                    val next = computeNextEpisode(seriesEpisodes, currentEpisode)
                    if (next != null) {
                        goToEpisode(next, true)
                    } else {
                        viewModel.clearPosition(currentEpisode.id)
                        handleClose()
                    }
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }

        exoPlayer.addListener(listener)

        // Force initial update of tracks
        updateTracksState(exoPlayer.currentTracks)

        onDispose {
            val lastPos = exoPlayer.currentPosition
            val lastDur = exoPlayer.duration
            if (lastPos > 0 && lastDur > 0) {
                if (lastPos >= (lastDur - 15000L)) {
                    viewModel.clearPosition(currentEpisode.id)
                } else {
                    viewModel.savePosition(currentEpisode, lastPos, lastDur, seriesName, seriesCover)
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

    // Full screen capture key events for TV zapping and gesture capture
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            skipBackward()
                            seekNotification = "◀◀ 10s"
                        } else {
                            skipForward()
                            seekNotification = "10s ▶▶"
                        }
                    }
                )
            }
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
                        subtitleView?.applySubtitleStyle(viewModel.getSubtitleStyle())
                        playerViewRef = this
                    }
                },
                update = { view ->
                    view.resizeMode = currentResizeMode.value
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            }
        }

        // Double-tap Seek Notification Overlay
        seekNotification?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Aspect Ratio Change Notification Overlay
        resizeModeNotification?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Playback Error Overlay with Retry
        playbackError?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60F0F13)),
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
                        text = "Erreur de Lecture",
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
                            Text("Réessayer")
                        }

                        OutlinedButton(
                            onClick = handleClose,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Retour")
                        }
                    }
                }
            }
        }

        // Custom Media Controls Overlay (refonte Phase 60)
        if (showControls && playbackError == null && !isInPipMode) {
            val hasMultipleAudio = availableAudioTracks.size > 1
            val hasSubtitles = availableSubtitleTracks.isNotEmpty()
            val episodeLabel = "S%02dE%02d".format(currentEpisode.seasonNum, currentEpisode.episodeNum)

            // Dégradés haut/bas pour lisibilité.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )

            // Barre supérieure : retour (gauche) — PIP + format (droite)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                PlayerTopButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    onClick = handleClose
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isTv) {
                        PlayerTopButton(
                            icon = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            onClick = {
                                val act = componentActivity ?: return@PlayerTopButton
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val builder = android.app.PictureInPictureParams.Builder()
                                    val vs = exoPlayer.videoSize
                                    if (vs.width > 0 && vs.height > 0) {
                                        try {
                                            val ratio = vs.width.toFloat() / vs.height.toFloat()
                                            if (ratio in 0.4184f..2.39f) {
                                                builder.setAspectRatio(android.util.Rational(vs.width, vs.height))
                                            } else {
                                                builder.setAspectRatio(android.util.Rational(16, 9))
                                            }
                                        } catch (e: Exception) {
                                            builder.setAspectRatio(android.util.Rational(16, 9))
                                        }
                                    } else {
                                        builder.setAspectRatio(android.util.Rational(16, 9))
                                    }
                                    act.enterPictureInPictureMode(builder.build())
                                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    @Suppress("DEPRECATION")
                                    act.enterPictureInPictureMode()
                                }
                            }
                        )
                    }
                    PlayerTopButton(
                        icon = Icons.Default.AspectRatio,
                        contentDescription = "Format de l'image",
                        onClick = {
                            val nextMode = when (currentResizeMode) {
                                ResizeMode.FIT -> ResizeMode.FILL
                                ResizeMode.FILL -> ResizeMode.ZOOM
                                ResizeMode.ZOOM -> ResizeMode.FIT
                            }
                            currentResizeMode = nextMode
                            viewModel.setResizeMode(nextMode)
                            resizeModeNotification = "Format : ${when (nextMode) {
                                ResizeMode.FIT -> "Ajuster"
                                ResizeMode.FILL -> "Étirer"
                                ResizeMode.ZOOM -> "Zoom"
                            }}"
                        }
                    )
                }
            }

            // Transport central : reculer / play-pause / avancer (10s)
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                TransportButton(Icons.Default.FastRewind, "Reculer 10s", onClick = { skipBackward() })
                PlayPauseButton(isPlaying = isPlaying, onClick = { togglePlayPause() })
                TransportButton(Icons.Default.FastForward, "Avancer 10s", onClick = { skipForward() })
            }

            // Bloc inférieur : affiche + titre + résolution + progression + actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!seriesCover.isNullOrBlank()) {
                        AsyncImage(
                            model = seriesCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 64.dp, height = 92.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface3)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = seriesName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            ResolutionBadge(width = videoWidth, height = videoHeight)
                        }
                        Text(
                            text = if (currentEpisode.title.isNotBlank()) "$episodeLabel · ${currentEpisode.title}" else episodeLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Barre de progression
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
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
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = formatTime(duration), color = Color.White, fontSize = 12.sp)
                        }

                        // Barre d'actions : Recommencer — Épisode précédent/suivant
                        // (si dispo) — Audio — Sous-titres. Scroll horizontal
                        // pour ne jamais tronquer sur petit écran.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            PlayerBottomAction(
                                icon = Icons.Default.Replay,
                                label = "Recommencer",
                                onClick = {
                                    exoPlayer.seekTo(0)
                                    currentPosition = 0
                                    showControls = true
                                }
                            )
                            if (previousEpisode != null) {
                                PlayerBottomAction(
                                    icon = Icons.Default.SkipPrevious,
                                    label = "Précédent",
                                    onClick = { goToEpisode(previousEpisode, false) }
                                )
                            }
                            if (nextEpisode != null) {
                                PlayerBottomAction(
                                    icon = Icons.Default.SkipNext,
                                    label = "Suivant",
                                    onClick = { goToEpisode(nextEpisode, false) }
                                )
                            }
                            if (hasMultipleAudio) {
                                PlayerBottomAction(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    label = "Audio",
                                    onClick = { showTrackDialog = true }
                                )
                            }
                            if (hasSubtitles) {
                                PlayerBottomAction(
                                    icon = Icons.Default.Subtitles,
                                    label = "Sous-titres",
                                    onClick = { showTrackDialog = true }
                                )
                            }
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
                    viewModel.saveSeriesAudio(seriesId, track.language)
                    seriesPref = (seriesPref ?: com.poc.iptvxtream.domain.model.TrackPreference(null, null))
                        .copy(audioLang = track.language)
                    val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(
                                track.mediaTrackGroup,
                                track.trackIndex
                            )
                        )
                        .build()
                    exoPlayer.trackSelectionParameters = newParams
                    updateTracksState(exoPlayer.currentTracks)
                },
                onSubtitleTrackSelected = { track ->
                    if (track == null) {
                        viewModel.saveSeriesSubtitle(seriesId, "none")
                        seriesPref = (seriesPref ?: com.poc.iptvxtream.domain.model.TrackPreference(null, null))
                            .copy(subtitleLang = "none")
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                    } else {
                        viewModel.saveSeriesSubtitle(seriesId, track.language)
                        seriesPref = (seriesPref ?: com.poc.iptvxtream.domain.model.TrackPreference(null, null))
                            .copy(subtitleLang = track.language)
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

@Composable
private fun TrackSelectionDialog(
    availableAudioTracks: List<TrackInfo>,
    availableSubtitleTracks: List<TrackInfo>,
    onAudioTrackSelected: (TrackInfo) -> Unit,
    onSubtitleTrackSelected: (TrackInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "AUDIO & SOUS-TITRES",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = Surface3,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio Piste
                Text("PISTE AUDIO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableAudioTracks.forEach { track ->
                        val isSelected = track.isSelected
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .onFocusChanged { isFocused = it.isFocused }
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (isFocused) Color(0xFF2C2C35) else if (isSelected) Color(0x33FFB300) else Surface1)
                                .clickable { onAudioTrackSelected(track) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onAudioTrackSelected(track) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${track.label} (${track.language?.uppercase() ?: "Inconnu"})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // Subtitle Piste
                Text("SOUS-TITRES", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isNoneSelected = availableSubtitleTracks.none { it.isSelected }
                    var isNoneFocused by remember { mutableStateOf(false) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .onFocusChanged { isNoneFocused = it.isFocused }
                            .border(
                                width = 1.dp,
                                color = if (isNoneFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(if (isNoneFocused) Color(0xFF2C2C35) else if (isNoneSelected) Color(0x33FFB300) else Surface1)
                            .clickable { onSubtitleTrackSelected(null) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isNoneSelected,
                                onClick = { onSubtitleTrackSelected(null) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aucun",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    availableSubtitleTracks.forEach { track ->
                        val isSelected = track.isSelected
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .onFocusChanged { isFocused = it.isFocused }
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (isFocused) Color(0xFF2C2C35) else if (isSelected) Color(0x33FFB300) else Surface1)
                                .clickable { onSubtitleTrackSelected(track) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSubtitleTrackSelected(track) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${track.label} (${track.language?.uppercase() ?: "Inconnu"})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Fermer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun formatTime(ms: Long): String {
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
