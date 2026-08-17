package com.cstv.app.presentation.vod
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.cstv.app.data.download.OfflineDownloadUtil
import com.cstv.app.domain.model.DownloadedItem
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import com.cstv.app.data.local.storage.ResizeMode
import androidx.media3.ui.PlayerView
import com.cstv.app.presentation.player.applySubtitleStyle
import com.cstv.app.presentation.player.PlayerTopButton
import com.cstv.app.presentation.player.TransportButton
import com.cstv.app.presentation.player.PlayPauseButton
import com.cstv.app.presentation.player.PlayerBottomAction
import com.cstv.app.presentation.player.PlayerCoverAction
import com.cstv.app.presentation.player.ResolutionBadge
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.player.core.KeepScreenOnEffect
import com.cstv.app.presentation.player.core.PlayerKeyIntent
import com.cstv.app.presentation.player.core.resolveMediaKeyIntent
import com.cstv.app.presentation.player.core.resolveTvDpadIntent
import com.cstv.app.presentation.player.core.PlayerOverlayHost
import com.cstv.app.presentation.player.core.PlayerOverlayGradients
import com.cstv.app.presentation.player.core.PlayerOverlayTopBar
import com.cstv.app.presentation.player.core.PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH
import com.cstv.app.presentation.player.core.TrackPlayerPosition
import com.cstv.app.presentation.player.core.enterPictureInPicture
import com.cstv.app.presentation.player.core.playbackProgressState
import com.cstv.app.presentation.player.core.rememberPlaybackEngineController
import com.cstv.app.presentation.player.core.rememberPipState
import com.cstv.app.presentation.player.core.ExoPlaybackRecoveryEngine
import com.cstv.app.presentation.player.core.PlaybackErrorHandling
import com.cstv.app.presentation.player.core.PlaybackRecoveryCoordinator
import com.cstv.app.presentation.player.core.PlaybackRecoverySession
import com.cstv.app.presentation.player.core.PlaybackRestoreState
import com.cstv.app.presentation.player.core.RecoveryOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.VodDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cstv.app.presentation.components.PlaybackLockConflictDialog
import com.cstv.app.presentation.components.PlaybackTakenOverOverlay

/** T23 : même valeur que `MediaType.MOVIE.value` (kind stocké sur `media_refs`). */
private const val PLAYBACK_REPAIR_KIND = "movie"

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
    val mediaTrackGroup: androidx.media3.common.TrackGroup,
    val isSupported: Boolean = true
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VodPlayerScreen(
    details: VodDetails,
    initialPositionMs: Long,
    credentials: Credentials,
    isTv: Boolean,
    viewModel: VodViewModel,
    onClose: () -> Boolean,
    canOpenDetails: Boolean,
    onOpenDetails: () -> Boolean,
    /** F39 §8.6 : `false` pour le lecteur hors ligne (contenu téléchargé), qui n'a qu'une seule
     *  version présente sur l'appareil — aucune requête DAO, aucun bouton (§7.3). */
    versionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val engineController = rememberPlaybackEngineController(useOfflineCache = true)
    val exoPlayer = engineController.player
    // T23 : `null` si le repository n'est pas injecté (tests/anciens call sites du ViewModel) —
    // la réparation ne se déclenche simplement pas, comportement pré-T23 inchangé.
    val recoveryEngine = remember(engineController) { ExoPlaybackRecoveryEngine(engineController) }
    val recoveryCoordinator = remember(recoveryEngine, viewModel.playbackRepairRepository) {
        viewModel.playbackRepairRepository?.let { PlaybackRecoveryCoordinator(recoveryEngine, it) }
    }
    val recoveryScope = rememberCoroutineScope()
    // R3/R4/R6/R7 : possède le job/génération de réparation et la remise à zéro entre deux
    // films, extrait du Composable (voir PlaybackRecoverySession).
    val recoverySession = remember(recoveryScope, recoveryEngine, recoveryCoordinator) {
        PlaybackRecoverySession(recoveryScope, recoveryEngine, recoveryCoordinator)
    }
    // R11 : distingue l'échec T23 (message + bouton Réessayer seul) d'une erreur générique
    // (message + Réessayer + Retour, comportement pré-T23 inchangé).
    var isDeviceUnrepairable by remember { mutableStateOf(false) }
    val lockUi by viewModel.playbackLockUiState.collectAsStateWithLifecycle()
    val currentLockUi by rememberUpdatedState(lockUi)
    LaunchedEffect(details.streamId) { viewModel.beginPlaybackLock(DownloadedItem.movieContentId(details.streamId)) }
    LaunchedEffect(lockUi.takenOverBy) { if (lockUi.takenOverBy != null) exoPlayer.stop() }

    var isPlayerVisible by remember { mutableStateOf(true) }
    var isLeaving by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableStateOf(viewModel.getResizeMode()) }
    var overlayNotification by remember { mutableStateOf<String?>(null) }
    val coverFocusRequester = remember { FocusRequester() }

    LaunchedEffect(overlayNotification) {
        if (overlayNotification != null) {
            delay(2000)
            overlayNotification = null
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val componentActivity = remember(context) { context.findActivity() as? androidx.activity.ComponentActivity }
    val isInPipMode = rememberPipState(playerViewRef)

    // Préférence de pistes mémorisée pour CE film (Phase 29). Préchargée une
    // fois ; prioritaire sur le fallback global "dernière langue utilisée".
    var moviePref by remember { mutableStateOf<com.cstv.app.domain.model.TrackPreference?>(null) }
    LaunchedEffect(details.streamId) {
        moviePref = viewModel.getMovieTrackPreference(details.streamId)
    }

    // F39 §8.5/§8.6 : versions candidates de ce film et bascule transactionnelle.
    var availableVersions by remember { mutableStateOf(emptyList<com.cstv.app.domain.model.VodStream>()) }
    // La version réellement en cours peut diverger de `details.streamId` après une bascule
    // réussie : le titre/l'affiche restent ceux de l'œuvre (inchangés entre versions), seule la
    // source de lecture change (§8.5).
    var currentVersionStreamId by remember(details.streamId) { mutableStateOf(details.streamId) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var isSwitchingVersion by remember { mutableStateOf(false) }
    val versionSwitchScope = rememberCoroutineScope()
    val versionSwitchController = remember(engineController) {
        com.cstv.app.presentation.player.core.MediaVersionSwitchController(
            com.cstv.app.presentation.player.core.ExoMediaVersionSwitchEngine(engineController)
        )
    }
    LaunchedEffect(details.streamId, versionsEnabled) {
        currentVersionStreamId = details.streamId
        availableVersions = if (versionsEnabled) viewModel.getMovieVersions(details.streamId) else emptyList()
    }
    val rollbackMessage = stringResource(R.string.player_version_rollback_message)

    val onSelectVersion: (com.cstv.app.domain.model.VodStream) -> Unit = selectVersion@{ target ->
        if (isSwitchingVersion || target.streamId == currentVersionStreamId) return@selectVersion
        isSwitchingVersion = true
        versionSwitchScope.launch {
            val targetExtension = viewModel.getMovieContainerExtension(target.streamId) ?: "mp4"
            val previousExtension = viewModel.getMovieContainerExtension(currentVersionStreamId) ?: details.containerExtension
            val previousTarget = com.cstv.app.presentation.player.core.PlayableVersionTarget(
                buildMoviePlayUrl(currentVersionStreamId, previousExtension, credentials)
            )
            val nextTarget = com.cstv.app.presentation.player.core.PlayableVersionTarget(
                buildMoviePlayUrl(target.streamId, targetExtension, credentials)
            )
            val result = versionSwitchController.switchTo(previousTarget, nextTarget)
            isSwitchingVersion = false
            when (result) {
                com.cstv.app.presentation.player.core.MediaVersionSwitchResult.Switched -> {
                    currentVersionStreamId = target.streamId
                    showVersionDialog = false
                }
                com.cstv.app.presentation.player.core.MediaVersionSwitchResult.RolledBack -> {
                    overlayNotification = rollbackMessage
                    showVersionDialog = false
                }
                com.cstv.app.presentation.player.core.MediaVersionSwitchResult.Superseded -> Unit
            }
        }
    }

    val handleClose = {
        if (!isLeaving) {
            isLeaving = true
            isPlayerVisible = false
            viewModel.releasePlaybackLock()
            exoPlayer.stop()
            exoPlayer.clearVideoSurface()
            if (!onClose()) {
                isLeaving = false
                isPlayerVisible = true
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    val handleOpenDetails = {
        if (isLeaving) {
            Unit
        } else if (!canOpenDetails) {
            overlayNotification = context.getString(R.string.player_details_unavailable)
        } else {
            isLeaving = true
            isPlayerVisible = false
            exoPlayer.stop()
            exoPlayer.clearVideoSurface()
            if (!onOpenDetails()) {
                isLeaving = false
                isPlayerVisible = true
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    androidx.activity.compose.BackHandler {
        handleClose()
    }

    KeepScreenOnEffect()

    var isPlaying by remember { mutableStateOf(true) }
    var cloudPlaybackStarted by remember(details.streamId) { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableStateOf(initialPositionMs) }
    var duration by remember { mutableStateOf(0L) }
    val playbackProgress = playbackProgressState(currentPosition, duration)
    var showControls by remember { mutableStateOf(true) }
    // Relance l'auto-masquage à chaque appui : sans ça, l'overlay se referme en
    // pleine navigation D-pad et le focus retombe sur la vidéo.
    var keyInteractionTick by remember { mutableStateOf(0) }
    // Focus TV : la surface vidéo garde les touches tant que l'overlay est
    // masqué ; dès qu'il s'affiche, la barre de progression les reçoit et les
    // flèches servent uniquement à circuler entre les options.
    val videoFocus = rememberTvInitialFocus(isTv = isTv, ready = !showControls, targetKey = showControls)
    // La barre n'est focalisable qu'une fois la durée connue (`enabled`) : sans
    // cette condition, les tentatives bornées s'épuiseraient avant qu'elle existe.
    val controlsFocus = rememberTvInitialFocus(
        isTv = isTv,
        ready = showControls && playbackProgress.isReady,
        targetKey = showControls
    )
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Dynamic Tracks States (Audio & Subtitles)
    var availableAudioTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var availableSubtitleTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var showTrackDialog by remember { mutableStateOf(false) }

    // Prepare and play VOD
    // Une pause relâche le verrou de lecture (F37) : `canStartPlayback` repasse
    // à false puis à true à la reprise. Sans ce garde-fou, l'effet rejouerait sa
    // préparation complète et le `seekTo(initialPositionMs)` renverrait la
    // lecture à la position d'ouverture au lieu de la position de pause.
    var playbackPrepared by remember(details.streamId) { mutableStateOf(false) }
    LaunchedEffect(details.streamId, lockUi.canStartPlayback) {
        if (!lockUi.canStartPlayback) return@LaunchedEffect
        if (playbackPrepared) {
            exoPlayer.playWhenReady = true
            return@LaunchedEffect
        }
        playbackPrepared = true
        isBuffering = true
        isDeviceUnrepairable = false
        playbackError = null

        // T23 : (ré)initialise la session pour ce film — reconstruit explicitement le plan
        // mémorisé, ou DEFAULT s'il n'y en a pas (R1, R3, R4) — sans effet si aucun repository
        // n'est injecté ou si rien n'est mémorisé.
        recoverySession.forTarget(PLAYBACK_REPAIR_KIND, details.streamId)

        val url = details.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password
        )

        // customCacheKey stable (indépendant des identifiants dans l'URL) pour
        // que la lecture retrouve le fichier téléchargé dans le cache.
        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.parse(url))
            .setCustomCacheKey(DownloadedItem.movieContentId(details.streamId))
            .build()
        engineController.setMediaItem(mediaItem)
        engineController.player.prepare()

        // Seek to saved position if requested
        if (initialPositionMs > 0) {
            engineController.player.seekTo(initialPositionMs)
        }

        engineController.player.playWhenReady = true
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
                            mediaTrackGroup = group.mediaTrackGroup,
                            isSupported = group.isTrackSupported(tIndex)
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
                            mediaTrackGroup = group.mediaTrackGroup,
                            isSupported = group.isTrackSupported(tIndex)
                        )
                    )
                }
            }
        }
        availableAudioTracks = audios
        availableSubtitleTracks = subtitles
    }

    // Auto-apply preferred languages from SettingsManager
    val applyPreferredLanguages: (List<TrackInfo>, List<TrackInfo>) -> Unit = { audios, subs ->
        // Priorité à la préférence mémorisée pour ce film ; sinon fallback sur
        // la dernière langue utilisée globalement (comportement d'avant Phase 29).
        val prefAudio = moviePref?.audioLang ?: viewModel.getPreferredAudio()
        val prefSub = moviePref?.subtitleLang ?: viewModel.getPreferredSubtitle()

        var updatedParams = exoPlayer.trackSelectionParameters.buildUpon()

        // Apply preferred audio — ne réapplique l'override que si AUCUNE piste
        // de cette langue n'est déjà sélectionnée (sinon, quand plusieurs pistes
        // partagent la même langue, ce recalcul reviendrait toujours sur la
        // première d'entre elles et écraserait un choix manuel explicite de la
        // 2e/3e piste, ce recalcul étant redéclenché après chaque sélection car
        // onXxxTrackSelected appelle updateTracksState qui remet à jour la state
        // observée par ce LaunchedEffect).
        if (!prefAudio.isNullOrBlank()) {
            val alreadySelected = audios.any { it.language?.equals(prefAudio, ignoreCase = true) == true && it.isSelected }
            val matchingAudio = audios.find { it.language?.equals(prefAudio, ignoreCase = true) == true && it.isSupported }
            if (matchingAudio != null && !alreadySelected) {
                updatedParams = updatedParams.setOverrideForType(
                    TrackSelectionOverride(
                        matchingAudio.mediaTrackGroup,
                        matchingAudio.trackIndex
                    )
                )
            }
        }

        // Apply preferred subtitle — même précaution que pour l'audio ci-dessus.
        if (prefSub != null) {
            if (prefSub == "none" || prefSub.isBlank()) {
                updatedParams = updatedParams
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                val alreadySelected = subs.any { it.language?.equals(prefSub, ignoreCase = true) == true && it.isSelected }
                val matchingSub = subs.find { it.language?.equals(prefSub, ignoreCase = true) == true && it.isSupported }
                if (matchingSub != null && !alreadySelected) {
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

    TrackPlayerPosition(
        exoPlayer = exoPlayer,
        contentKey = details.streamId,
        isPlaying = isPlaying,
        onPositionChanged = { positionMs, durationMs ->
            currentPosition = positionMs
            duration = durationMs
        },
        onPeriodicSave = { positionMs, durationMs ->
            viewModel.savePosition(details.streamId, positionMs, durationMs, details)
        },
        onTrackerDispose = { positionMs, durationMs ->
            if (positionMs > 0L && durationMs > 0L) {
                if (positionMs >= durationMs - 15_000L) {
                    viewModel.clearPosition(details.streamId)
                } else {
                    viewModel.savePosition(details.streamId, positionMs, durationMs, details)
                }
            }
        }
    )

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
                
                // If film completes (reaches end), delete saved position
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.markPlaybackForCloud()
                    cloudPlaybackStarted = false
                    viewModel.clearPosition(details.streamId)
                    handleClose()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) viewModel.pausePlaybackLock()
                else if (!currentLockUi.canStartPlayback) { exoPlayer.pause(); viewModel.resumePlaybackLock() }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                when {
                    playing && !cloudPlaybackStarted -> {
                        cloudPlaybackStarted = true
                        viewModel.markPlaybackForCloud()
                    }
                    !playing && cloudPlaybackStarted -> viewModel.markPlaybackForCloud()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val restorePositionMs = PlaybackRecoveryCoordinator.vodRestorePositionMs(
                    positionBeforeErrorMs = currentPosition,
                    durationMs = duration
                )
                val handling = recoverySession.handleError(
                    error = error,
                    restore = PlaybackRestoreState(positionMs = restorePositionMs, playWhenReady = true, speed = 1f)
                ) { outcome ->
                    when (outcome) {
                        is RecoveryOutcome.Stable -> isBuffering = false
                        RecoveryOutcome.DecoderExhausted -> {
                            isBuffering = false
                            isDeviceUnrepairable = true
                            playbackError = context.getString(R.string.player_unrepairable_message)
                        }
                        is RecoveryOutcome.Aborted -> {
                            isBuffering = false
                            playbackError = if (currentLockUi.failOpen) context.getString(R.string.playback_lock_possible_limit) else context.getString(R.string.player_generic_error)
                        }
                    }
                }

                when (handling) {
                    PlaybackErrorHandling.AlreadyRepairing -> Unit
                    PlaybackErrorHandling.RepairStarted -> isBuffering = true
                    is PlaybackErrorHandling.NotDecoder -> {
                        isBuffering = false
                        playbackError = if (currentLockUi.failOpen) context.getString(R.string.playback_lock_possible_limit) else "Impossible de charger le flux vidéo (${error.localizedMessage})"
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksState(tracks)
                // T23-R1 : applique la piste du profil mémorisé dès que les pistes du nouveau
                // média sont connues (impossible avant, Media3 n'expose les TrackGroup qu'ici).
                recoverySession.applyPendingTrackSelectionOfInitialPlan()
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
            exoPlayer.removeListener(listener)
            viewModel.releasePlaybackLock()
        }
    }

    lockUi.conflict?.let { holder -> PlaybackLockConflictDialog(holder, onDismiss = { viewModel.cancelPlaybackLockRequest(); handleClose() }, onTakeOver = viewModel::takeOverPlaybackLock) }
    lockUi.takenOverBy?.let { holder -> PlaybackTakenOverOverlay(holder.deviceName, onResume = viewModel::resumePlaybackLock, onDismiss = { handleClose() }) }

    // Auto-apply preferences as soon as tracks are first discovered
    LaunchedEffect(availableAudioTracks, availableSubtitleTracks) {
        if (availableAudioTracks.isNotEmpty() || availableSubtitleTracks.isNotEmpty()) {
            applyPreferredLanguages(availableAudioTracks, availableSubtitleTracks)
        }
    }

    fun togglePlayPause() {
        // `isPlaying` est aussi mis à jour de façon synchrone ici : le listener
        // ExoPlayer (onIsPlayingChanged) le fera aussi, mais son callback est
        // asynchrone (poste sur le looper). Sans cette valeur synchrone, le
        // LaunchedEffect d'auto-masquage de PlayerOverlayHost pouvait démarrer
        // avec l'ancien `isPlaying` (encore `true` juste après une pause) et
        // programmer un masquage à 5 s avant que le listener ne corrige l'état
        // — l'overlay se refermait alors en pleine pause.
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
        } else {
            exoPlayer.play()
            isPlaying = true
        }
        showControls = true
    }

    // `revealControls` est laissé à false par le double-tap mobile : ouvrir le
    // HUD au premier saut empêcherait les suivants, la zone de double-tap étant
    // alors recouverte par l'overlay.
    fun skipForward(revealControls: Boolean = true) {
        val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
        exoPlayer.seekTo(newPos)
        currentPosition = newPos
        // Le repère de position rend le message unique à chaque saut : réémettre
        // un libellé identique ne relancerait pas le minuteur d'effacement.
        if (revealControls) showControls = true else overlayNotification = "10s ▶▶ · ${formatTime(newPos)}"
    }

    fun skipBackward(revealControls: Boolean = true) {
        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
        exoPlayer.seekTo(newPos)
        currentPosition = newPos
        if (revealControls) showControls = true else overlayNotification = "◀◀ 10s · ${formatTime(newPos)}"
    }

    fun runKeyIntent(intent: PlayerKeyIntent): Boolean = when (intent) {
        PlayerKeyIntent.PlayPause -> { togglePlayPause(); true }
        PlayerKeyIntent.Play -> { exoPlayer.play(); showControls = true; true }
        PlayerKeyIntent.Pause -> { exoPlayer.pause(); showControls = true; true }
        PlayerKeyIntent.FastForward -> { skipForward(); true }
        PlayerKeyIntent.Rewind -> { skipBackward(); true }
        // Un film n'a ni suivant ni précédent : la touche est consommée pour ne
        // pas dégénérer en déplacement de focus inattendu.
        PlayerKeyIntent.Next, PlayerKeyIntent.Previous -> { showControls = true; true }
        PlayerKeyIntent.Stop -> { handleClose(); true }
        PlayerKeyIntent.RevealControls -> { showControls = true; true }
    }

    // Full screen capture key events for TV zapping and gesture capture
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Phase preview : les touches média et le pad TV sont arbitrés avant
            // le bouton focalisé, sinon l'overlay capterait tout.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyInteractionTick++
                val mediaIntent = resolveMediaKeyIntent(keyEvent.key)
                if (mediaIntent != null) return@onPreviewKeyEvent runKeyIntent(mediaIntent)
                if (!isTv) return@onPreviewKeyEvent false
                val dpadIntent = resolveTvDpadIntent(keyEvent.key, showControls)
                if (dpadIntent != null) runKeyIntent(dpadIntent) else false
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.Back -> {
                            handleClose()
                            true
                        }
                        // Sur TV, les flèches restent au système de focus : elles
                        // ont déjà été arbitrées en phase preview.
                        isTv -> {
                            showControls = true
                            false
                        }
                        keyEvent.key == Key.DirectionLeft -> {
                            skipBackward()
                            true
                        }
                        keyEvent.key == Key.DirectionRight -> {
                            skipForward()
                            true
                        }
                        keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter -> {
                            togglePlayPause()
                            true
                        }
                        else -> {
                            showControls = true
                            false
                        }
                    }
                } else false
            }
            .tvInitialFocusTarget(videoFocus)
            .focusable()
            .then(
                if (isTv) {
                    // `clickable` répond aussi à la touche OK (Entrée/DPadCenter)
                    // sur son relâchement (KeyUp) : notre gestion custom du pad ne
                    // consomme que le KeyDown (pause + ouverture du HUD), donc le
                    // KeyUp qui suit tombait dans ce `clickable` et refermait le
                    // HUD aussitôt (`!showControls`). `pointerInput` ne réagit
                    // qu'aux événements pointeur (télécommande air mouse / souris),
                    // jamais aux touches — même pattern que le lecteur Live.
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    }
                } else {
                    // Mobile : simple tap = bascule du HUD ; double-tap sur une
                    // moitié d'écran = saut de 10 s dans ce sens, à condition que
                    // le HUD soit masqué (sinon le geste viserait ses contrôles).
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                if (showControls) {
                                    showControls = false
                                } else if (offset.x >= size.width / 2f) {
                                    skipForward(revealControls = false)
                                } else {
                                    skipBackward(revealControls = false)
                                }
                            }
                        )
                    }
                }
            )
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.player_loading_stream), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        overlayNotification?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = msg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        // R11 : titre dédié pour l'échec T23, distinct des erreurs génériques.
                        text = stringResource(if (isDeviceUnrepairable) R.string.player_unrepairable_title else R.string.player_error_title),
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
                                if (isDeviceUnrepairable) {
                                    // T23-R7 : « Réessayer » relance la séquence complète depuis le
                                    // début — remet le moteur au plan DEFAULT plutôt que de
                                    // simplement rejouer la dernière configuration en échec.
                                    isDeviceUnrepairable = false
                                    recoverySession.prepareRetryFromScratch()
                                    engineController.player.prepare()
                                    engineController.player.playWhenReady = true
                                } else {
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.player_retry))
                        }

                        // R11 : la décision étape 2 (§4) ne prévoit qu'un bouton « Réessayer » pour
                        // l'échec T23 — « Retour » reste réservé aux erreurs génériques existantes.
                        if (!isDeviceUnrepairable) {
                            OutlinedButton(
                                onClick = handleClose,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.common_back))
                            }
                        }
                    }
                }
            }
        }

        // Custom Media Controls Overlay (refonte Phase 60)
        if (playbackError == null) PlayerOverlayHost(
            isVisible = showControls,
            isInPipMode = isInPipMode,
            isAutoHideBlocked = showTrackDialog,
            isPlaying = isPlaying,
            interactionKey = keyInteractionTick,
            onVisibilityChanged = { showControls = it }
        ) {
            val hasMultipleAudio = availableAudioTracks.size > 1
            val hasSubtitles = availableSubtitleTracks.isNotEmpty()

            PlayerOverlayGradients(topHeight = 120.dp, bottomHeight = 220.dp)

            // Barre supérieure : retour (gauche) — PIP + format (droite)
            PlayerOverlayTopBar {
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
                            onClick = { enterPictureInPicture(componentActivity, exoPlayer.videoSize) }
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
                            overlayNotification = "Format : ${when (nextMode) {
                                ResizeMode.FIT -> "Ajuster"
                                ResizeMode.FILL -> "Étirer"
                                ResizeMode.ZOOM -> "Zoom"
                            }}"
                        }
                    )
                }
            }

            // Transport central : reculer / play-pause / avancer (10s). Mobile
            // uniquement — sur TV la barre de progression porte à elle seule
            // avance / recul (gauche-droite) et play-pause (OK), et ces trois
            // boutons masquaient l'image.
            if (!isTv) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    TransportButton(Icons.Default.FastRewind, "Reculer 10s", onClick = { skipBackward() })
                    PlayPauseButton(isPlaying = isPlaying, onClick = { togglePlayPause() })
                    TransportButton(Icons.Default.FastForward, "Avancer 10s", onClick = { skipForward() })
                }
            }

            // Bloc inférieur : affiche + titre + résolution + progression + actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerCoverAction(
                        coverUrl = details.coverBig,
                        contentDescription = stringResource(R.string.player_open_details),
                        isAvailable = canOpenDetails,
                        unavailableContentDescription = stringResource(R.string.player_details_unavailable),
                        onClick = handleOpenDetails,
                        modifier = Modifier.focusRequester(coverFocusRequester)
                    )
                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = details.name,
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
                        details.displayGenre?.let { genre ->
                            Text(
                                text = genre,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Barre de progression
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (playbackProgress.isReady) {
                                Text(
                                    text = formatTime(playbackProgress.positionMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.widthIn(min = PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH))
                            }
                            Slider(
                                value = playbackProgress.positionMs.toFloat(),
                                onValueChange = {
                                    exoPlayer.seekTo(it.toLong())
                                    currentPosition = it.toLong()
                                },
                                valueRange = 0f..playbackProgress.sliderRangeEnd,
                                enabled = playbackProgress.isReady,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                // Barre focalisée au pad : gauche/droite pilotent
                                // la lecture plutôt que le focus, OK bascule
                                // lecture/pause. En preview, pour passer avant le
                                // pas par défaut du Slider.
                                modifier = Modifier
                                    .weight(1f)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (!isTv || keyEvent.type != KeyEventType.KeyDown) {
                                            false
                                        } else when (keyEvent.key) {
                                            Key.DirectionLeft -> { skipBackward(); true }
                                            Key.DirectionRight -> { skipForward(); true }
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                togglePlayPause(); true
                                            }
                                            else -> false
                                        }
                                    }
                                    .then(
                                        // Cible du focus initial dès l'ouverture de
                                        // l'overlay ; le haut mène à la jaquette
                                        // (gauche/droite étant confisquées).
                                        if (isTv) {
                                            Modifier
                                                .tvInitialFocusTarget(controlsFocus)
                                                .focusProperties { up = coverFocusRequester }
                                        } else Modifier
                                    )
                            )
                            if (playbackProgress.isReady) {
                                // Décompte plutôt que durée totale : le préfixe
                                // « - » signale qu'il s'agit du temps restant.
                                Text(
                                    text = "-${formatTime(playbackProgress.remainingMs)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.widthIn(min = PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH))
                            }
                        }

                        // Barre d'actions (scroll horizontal : jamais tronquée)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            if (versionsEnabled && availableVersions.size > 1) {
                                PlayerBottomAction(
                                    icon = Icons.Default.SwapHoriz,
                                    label = stringResource(R.string.player_version_action_label),
                                    onClick = { showVersionDialog = true }
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
                    viewModel.saveMovieAudio(details.streamId, track.language)
                    moviePref = (moviePref ?: com.cstv.app.domain.model.TrackPreference(null, null))
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
                    // Refresh tracks list state
                    updateTracksState(exoPlayer.currentTracks)
                },
                onSubtitleTrackSelected = { track ->
                    if (track == null) {
                        viewModel.saveMovieSubtitle(details.streamId, "none")
                        moviePref = (moviePref ?: com.cstv.app.domain.model.TrackPreference(null, null))
                            .copy(subtitleLang = "none")
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                    } else {
                        viewModel.saveMovieSubtitle(details.streamId, track.language)
                        moviePref = (moviePref ?: com.cstv.app.domain.model.TrackPreference(null, null))
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

        // F39 §8.5 : sélecteur de versions.
        if (showVersionDialog) {
            com.cstv.app.presentation.player.VersionSelectorSheet(
                options = availableVersions.map { version ->
                    com.cstv.app.presentation.player.VersionOption(
                        id = version.streamId,
                        label = com.cstv.app.domain.model.mediaVersionBadges(version.languageTag, version.qualityTag)
                            .takeIf { it.isNotEmpty() }?.joinToString(" · ")
                            ?: version.name,
                        isActive = version.streamId == currentVersionStreamId
                    )
                },
                isSwitching = isSwitchingVersion,
                onSelect = { option -> availableVersions.firstOrNull { it.streamId == option.id }?.let(onSelectVersion) },
                onDismiss = { if (!isSwitchingVersion) showVersionDialog = false }
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
                Text(stringResource(R.string.player_audio_track_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableAudioTracks.forEach { track ->
                        val isSelected = track.isSelected
                        val isSupported = track.isSupported
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .onFocusChanged { if (isSupported) isFocused = it.isFocused }
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (isFocused) Color(0xFF2C2C35) else if (isSelected) Color(0x33FFB300) else Surface1)
                                .then(if (isSupported) Modifier.clickable { onAudioTrackSelected(track) } else Modifier)
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { if (isSupported) onAudioTrackSelected(track) },
                                    enabled = isSupported,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${track.label} (${track.language?.uppercase() ?: "Inconnu"})" + if (isSupported) "" else " (non supporté)",
                                    color = if (isSupported) Color.White else Color.Gray.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // Subtitle Piste
                Text(stringResource(R.string.player_subtitles_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        val isSupported = track.isSupported
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .onFocusChanged { if (isSupported) isFocused = it.isFocused }
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (isFocused) Color(0xFF2C2C35) else if (isSelected) Color(0x33FFB300) else Surface1)
                                .then(if (isSupported) Modifier.clickable { onSubtitleTrackSelected(track) } else Modifier)
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { if (isSupported) onSubtitleTrackSelected(track) },
                                    enabled = isSupported,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${track.label} (${track.language?.uppercase() ?: "Inconnu"})" + if (isSupported) "" else " (non supporté)",
                                    color = if (isSupported) Color.White else Color.Gray.copy(alpha = 0.5f),
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
                Text(stringResource(R.string.player_close), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * F39 §8.6 : URL Xtream d'une version candidate — même format que
 * [VodDetails.getPlayUrl]/[com.cstv.app.domain.model.VodStream.getPlayUrl], réutilisé sans
 * instance intermédiaire puisque seuls `streamId` et l'extension diffèrent d'une version à l'autre.
 */
private fun buildMoviePlayUrl(streamId: Int, containerExtension: String, credentials: Credentials): String {
    val cleanBase = credentials.baseUrl.trim().removeSuffix("/")
    val cleanExtension = containerExtension.trim().removePrefix(".")
    return "$cleanBase/movie/${credentials.username}/${credentials.password}/$streamId.$cleanExtension"
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
