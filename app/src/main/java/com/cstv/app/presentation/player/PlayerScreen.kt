package com.cstv.app.presentation.player
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.presentation.theme.Surface3
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.VideoSize
import com.cstv.app.data.local.storage.ResizeMode
import com.cstv.app.presentation.livetv.LiveTvViewModel
import com.cstv.app.presentation.player.core.KeepScreenOnEffect
import com.cstv.app.presentation.player.core.PlayerOverlayHost
import com.cstv.app.presentation.player.core.PlayerOverlayGradients
import com.cstv.app.presentation.player.core.PlayerOverlayTopBar
import com.cstv.app.presentation.player.core.enterPictureInPicture
import com.cstv.app.presentation.player.core.rememberPlaybackEngineController
import com.cstv.app.presentation.player.core.rememberPipState
import com.cstv.app.presentation.player.core.ExoPlaybackRecoveryEngine
import com.cstv.app.presentation.player.core.PlaybackErrorHandling
import com.cstv.app.presentation.player.core.PlaybackRecoveryCoordinator
import com.cstv.app.presentation.player.core.PlaybackRecoverySession
import com.cstv.app.presentation.player.core.PlaybackRestoreState
import com.cstv.app.presentation.player.core.RecoveryOutcome
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.player.core.PlayerKeyIntent
import com.cstv.app.presentation.player.core.resolveMediaKeyIntent
import com.cstv.app.presentation.player.core.resolveTvDpadIntent
import com.cstv.app.presentation.components.PlaybackLockConflictDialog
import com.cstv.app.presentation.components.PlaybackTakenOverOverlay
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.presentation.player.core.PlaybackFailureType

/** T23 : même valeur que `LiveTvRepositoryImpl.LIVE_KIND` (kind stocké sur `media_refs`). */
private const val PLAYBACK_REPAIR_KIND = "live"
private const val QUALITY_ADJUSTMENT_NOTIFICATION_MS = 2_500L

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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerScreen(
    initialStream: LiveStream,
    streamsList: List<LiveStream>,
    credentials: Credentials,
    isTv: Boolean,
    viewModel: LiveTvViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onStreamChanged: (LiveStream) -> Unit = {}
) {
    val context = LocalContext.current

    val engineController = rememberPlaybackEngineController(useOfflineCache = false)
    val exoPlayer = engineController.player
    // T23 : `null` si le repository n'est pas injecté (tests/anciens call sites du ViewModel) —
    // la réparation ne se déclenche simplement pas, comportement pré-T23 inchangé.
    val recoveryEngine = remember(engineController) { ExoPlaybackRecoveryEngine(engineController) }
    val recoveryCoordinator = remember(recoveryEngine, viewModel.playbackRepairRepository) {
        viewModel.playbackRepairRepository?.let { PlaybackRecoveryCoordinator(recoveryEngine, it) }
    }
    val recoveryScope = rememberCoroutineScope()
    // R3/R4/R6/R7 : possède le job/génération de réparation et la remise à zéro entre deux
    // chaînes, extrait du Composable (voir PlaybackRecoverySession).
    val recoverySession = remember(recoveryScope, recoveryEngine, recoveryCoordinator) {
        PlaybackRecoverySession(recoveryScope, recoveryEngine, recoveryCoordinator)
    }
    // R11 : distingue l'échec T23 (message + bouton Réessayer seul) d'une erreur générique
    // (message + Réessayer + Retour, comportement pré-T23 inchangé).
    var isDeviceUnrepairable by remember { mutableStateOf(false) }
    val lockUi by viewModel.playbackLockUiState.collectAsStateWithLifecycle()
    val currentLockUi by rememberUpdatedState(lockUi)
    LaunchedEffect(Unit) { viewModel.beginPlaybackLock() }
    LaunchedEffect(lockUi.takenOverBy) { if (lockUi.takenOverBy != null) exoPlayer.stop() }

    var isPlayerVisible by remember { mutableStateOf(true) }
    var showChannelList by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableStateOf(viewModel.getResizeMode()) }
    var resizeModeNotification by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resizeModeNotification) {
        if (resizeModeNotification != null) {
            delay(2000)
            resizeModeNotification = null
        }
    }

    val handleClose = {
        isPlayerVisible = false
        viewModel.releasePlaybackLock()
        exoPlayer.stop()
        exoPlayer.clearVideoSurface()
        onClose()
    }

    androidx.activity.compose.BackHandler {
        if (showChannelList) {
            showChannelList = false
        } else {
            handleClose()
        }
    }
    
    KeepScreenOnEffect()

    // Recherche par streamId (identité stable) et non par égalité structurelle :
    // une chaîne ouverte depuis "Récemment regardées" est reconstruite depuis
    // RecentlyWatchedLiveEntity (epgChannelId = null, num/categoryId éventuellement
    // différents), donc jamais structurellement égale à sa version dans streamsList
    // -> indexOf renvoyait -1, coerceAtLeast(0) forçait l'index 0 et lançait
    // toujours la première chaîne de la liste. En cas d'absence (-1), le fallback
    // ?: initialStream de la ligne suivante lit bien la chaîne cliquée.
    var activeStreamsList by remember { mutableStateOf(streamsList) }
    var currentStreamIndex by remember {
        mutableStateOf(activeStreamsList.indexOfFirst { it.streamId == initialStream.streamId })
    }
    val logicalStream = remember(currentStreamIndex, activeStreamsList) { activeStreamsList.getOrNull(currentStreamIndex) ?: initialStream }
    var selectedQualityStream by remember { mutableStateOf<LiveStream?>(null) }
    var qualityVariants by remember { mutableStateOf<List<LiveVariant>>(emptyList()) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var qualityAdjustmentNotification by remember { mutableStateOf<String?>(null) }
    var recoveryMovedToQuality by remember { mutableStateOf(false) }
    var preparedQualityGeneration by remember { mutableLongStateOf(0L) }
    val currentStream = selectedQualityStream ?: logicalStream

    LaunchedEffect(currentStream) {
        onStreamChanged(currentStream)
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }
    // Le live n'a pas de transport à l'écran, mais la touche média Play/Pause de
    // la télécommande doit fonctionner : l'état sert à garder l'overlay ouvert
    // tant que le flux est en pause.
    var isPlaying by remember { mutableStateOf(true) }
    // Relance l'auto-masquage à chaque appui : sans ça, l'overlay se referme en
    // pleine navigation D-pad et le focus retombe sur la vidéo.
    var keyInteractionTick by remember { mutableStateOf(0) }
    // Focus TV : la surface vidéo garde les touches tant que l'overlay est
    // masqué ; dès qu'il s'affiche, le premier bouton de la barre les reçoit et
    // les flèches ne servent plus qu'à circuler entre les options.
    val videoFocus = rememberTvInitialFocus(isTv = isTv, ready = !showOverlay, targetKey = showOverlay)
    val controlsFocus = rememberTvInitialFocus(isTv = isTv, ready = showOverlay, targetKey = showOverlay)
    var streamExtension by remember { mutableStateOf("m3u8") } // Default to m3u8
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    fun switchQuality(stream: LiveStream, automatic: Boolean) {
        selectedQualityStream = stream
        streamExtension = "m3u8"
        viewModel.resetLiveQualityMeasurement()
        if (automatic) qualityAdjustmentNotification = context.getString(R.string.player_quality_adjusted)
    }

    LaunchedEffect(logicalStream.streamId) {
        selectedQualityStream = null
        val qualityState = viewModel.startLiveQualitySession(logicalStream)
        qualityVariants = qualityState.variants
        selectedQualityStream = qualityState.selectedStream.takeIf { it.streamId != logicalStream.streamId }
        preparedQualityGeneration = qualityState.generation
    }

    LaunchedEffect(qualityAdjustmentNotification) {
        if (qualityAdjustmentNotification != null) {
            delay(QUALITY_ADJUSTMENT_NOTIFICATION_MS)
            qualityAdjustmentNotification = null
        }
    }

    // EPG « en cours + suivant » informatif (Phase 60) : rechargé à chaque
    // changement de chaîne. Aucune interaction (pas de timeshift, hors specs).
    val playerEpg by viewModel.playerEpg.collectAsStateWithLifecycle()
    LaunchedEffect(currentStream.streamId) {
        viewModel.loadPlayerEpg(currentStream.streamId)
    }
    // Horloge (secondes) rafraîchie chaque seconde pour animer la jauge EPG et
    // l'heure courante affichée, sans dépendre d'un recompose externe.
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(1000)
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val componentActivity = remember(context) { context.findActivity() as? androidx.activity.ComponentActivity }
    val isInPipMode = rememberPipState(playerViewRef)
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) showChannelList = false
    }

    // Prepare and play the stream whenever the stream index or extension changes
    LaunchedEffect(currentStream, streamExtension, lockUi.canStartPlayback) {
        if (!lockUi.canStartPlayback) {
            // Retour utilisateur du 2026-08-18 : un changement de chaîne/qualité pendant que le
            // verrou est relâché (ex. pause transitoire côté `onPlayWhenReadyChanged`) restait
            // bloqué indéfiniment — rien ne redemandait le verrou tant que l'utilisateur ne
            // rejouait pas manuellement. Cet effet redémarre déjà sur `lockUi.canStartPlayback` :
            // redemander ici suffit à le relancer automatiquement avec `currentStream` toujours à
            // jour dès que le verrou revient.
            viewModel.resumePlaybackLock()
            return@LaunchedEffect
        }
        isBuffering = true
        isDeviceUnrepairable = false
        playbackError = null

        // T23 : (ré)initialise la session pour cette chaîne — reconstruit explicitement le plan
        // mémorisé, ou DEFAULT s'il n'y en a pas (R1, R3, R4) — sans effet si aucun repository
        // n'est injecté ou si rien n'est mémorisé.
        recoverySession.forTarget(PLAYBACK_REPAIR_KIND, currentStream.streamId)

        val url = currentStream.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password,
            extension = streamExtension
        )

        val mediaItem = MediaItem.fromUri(url)
        engineController.setMediaItem(mediaItem)
        preparedQualityGeneration = viewModel.liveQualityGeneration()
        engineController.player.prepare()
        engineController.player.playWhenReady = true
    }

    // Player Event Listener
    DisposableEffect(exoPlayer, preparedQualityGeneration) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_READY -> {
                        viewModel.onLiveQualityReady(preparedQualityGeneration)
                    }
                    Player.STATE_BUFFERING -> viewModel.onLiveQualityBufferingStarted(preparedQualityGeneration)
                        ?.let { switchQuality(it, automatic = true) }
                }
                if (playbackState != Player.STATE_BUFFERING) viewModel.onLiveQualityBufferingEnded()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
                if (!playWhenReady) viewModel.pausePlaybackLock()
                else if (!currentLockUi.canStartPlayback) { exoPlayer.pause(); viewModel.resumePlaybackLock() }
            }

            override fun onPlayerError(error: PlaybackException) {
                // T23-R8 : la qualification (décodage vs réseau/live-window/inconnu) précède tout
                // changement de source — le repli m3u8→ts ne s'exécute plus qu'après coup, dans la
                // branche NotDecoder ci-dessous, jamais avant une éventuelle réparation.
                val handling = recoverySession.handleError(
                    error = error,
                    // Direct : pas de position à restaurer, reprise au live edge (§8.4) — le point
                    // d'extension F41 (tampon) n'existe pas encore (tâche 6).
                    restore = PlaybackRestoreState(positionMs = androidx.media3.common.C.TIME_UNSET, playWhenReady = true, speed = 1f),
                    exhaustionListener = com.cstv.app.presentation.player.core.PlaybackRecoveryExhaustionListener { _, _ ->
                        viewModel.onLiveQualityFailure(preparedQualityGeneration)?.let {
                            recoveryMovedToQuality = true
                            switchQuality(it, automatic = true)
                        }
                    }
                ) { outcome ->
                    when (outcome) {
                        is RecoveryOutcome.Stable -> isBuffering = false
                        RecoveryOutcome.DecoderExhausted -> {
                            isBuffering = false
                            if (recoveryMovedToQuality) {
                                recoveryMovedToQuality = false
                                isBuffering = true
                            } else {
                                isDeviceUnrepairable = true
                                playbackError = context.getString(R.string.player_unrepairable_message)
                            }
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
                        // Preserve the established m3u8→ts repair on this variant before F40 moves on.
                        if (streamExtension == "m3u8") {
                            streamExtension = "ts"
                            return
                        }
                        if (handling.type == PlaybackFailureType.NETWORK_SOURCE) {
                            val next = viewModel.onLiveQualityFailure(preparedQualityGeneration)
                            if (next != null) {
                                switchQuality(next, automatic = true)
                                return
                            }
                        }
                        isBuffering = false
                        playbackError = if (currentLockUi.failOpen) context.getString(R.string.playback_lock_possible_limit) else "Impossible de charger le flux vidéo (${error.localizedMessage})"
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
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

        onDispose {
            exoPlayer.removeListener(listener)
            viewModel.releasePlaybackLock()
        }
    }

    lockUi.conflict?.let { holder ->
        PlaybackLockConflictDialog(holder, onDismiss = { viewModel.cancelPlaybackLockRequest(); handleClose() }, onTakeOver = viewModel::takeOverPlaybackLock)
    }
    lockUi.takenOverBy?.let { holder -> PlaybackTakenOverOverlay(holder.deviceName, onResume = viewModel::resumePlaybackLock, onDismiss = handleClose) }

    fun zapNext() {
        if (activeStreamsList.isNotEmpty()) {
            selectedQualityStream = null
            currentStreamIndex = (currentStreamIndex + 1) % activeStreamsList.size
            showOverlay = true
            streamExtension = "m3u8" // Reset extension to default on zap
        }
    }

    fun zapPrev() {
        if (activeStreamsList.isNotEmpty()) {
            selectedQualityStream = null
            currentStreamIndex = if (currentStreamIndex - 1 < 0) activeStreamsList.size - 1 else currentStreamIndex - 1
            showOverlay = true
            streamExtension = "m3u8" // Reset extension to default on zap
        }
    }

    fun togglePlayPause() {
        // Un flux live reste « pausable » côté ExoPlayer (le tampon se fige) :
        // la reprise repart du direct via la logique de verrou existante.
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        showOverlay = true
    }

    fun runKeyIntent(intent: PlayerKeyIntent): Boolean = when (intent) {
        PlayerKeyIntent.PlayPause -> { togglePlayPause(); true }
        PlayerKeyIntent.Play -> { exoPlayer.play(); showOverlay = true; true }
        PlayerKeyIntent.Pause -> { exoPlayer.pause(); showOverlay = true; true }
        // Un flux live n'est pas seekable : les touches d'avance et de retour
        // sont consommées sans effet plutôt que de déplacer le focus.
        PlayerKeyIntent.FastForward, PlayerKeyIntent.Rewind -> { showOverlay = true; true }
        PlayerKeyIntent.Next -> { zapNext(); true }
        PlayerKeyIntent.Previous -> { zapPrev(); true }
        PlayerKeyIntent.Stop -> { handleClose(); true }
        PlayerKeyIntent.RevealControls -> { showOverlay = true; true }
    }

    // Full screen capture key events for TV zapping and gesture capture for Mobile swipe zapping
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Phase preview : les touches média et le pad TV sont arbitrés avant
            // les boutons de l'overlay et le tiroir de chaînes.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyInteractionTick++
                val mediaIntent = resolveMediaKeyIntent(keyEvent.key)
                if (mediaIntent != null) return@onPreviewKeyEvent runKeyIntent(mediaIntent)
                if (!isTv) return@onPreviewKeyEvent false
                // Le tiroir de chaînes compte comme des contrôles visibles : le
                // pad doit y circuler, pas zapper derrière.
                val dpadIntent = resolveTvDpadIntent(keyEvent.key, showOverlay || showChannelList)
                if (dpadIntent != null) runKeyIntent(dpadIntent) else false
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.Back -> {
                            if (showChannelList) {
                                showChannelList = false
                                true
                            } else {
                                handleClose()
                                true
                            }
                        }
                        // Sur TV, les flèches restent au système de focus : elles
                        // ont déjà été arbitrées en phase preview.
                        isTv -> {
                            showOverlay = true
                            false
                        }
                        keyEvent.key == Key.DirectionUp -> {
                            if (showChannelList) {
                                false
                            } else {
                                zapPrev()
                                true
                            }
                        }
                        keyEvent.key == Key.DirectionDown -> {
                            if (showChannelList) {
                                false
                            } else {
                                zapNext()
                                true
                            }
                        }
                        else -> {
                            showOverlay = true
                            false
                        }
                    }
                } else false
            }
            .tvInitialFocusTarget(videoFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showOverlay = !showOverlay
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -100f) {
                            zapNext()
                        } else if (totalDragY > 100f) {
                            zapPrev()
                        }
                    },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
        // Video Player View
        if (isPlayerVisible) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // We render our own beautiful native Compose overlay
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
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

        // Custom Overlay UI (Auto-hides) — refonte Phase 60. Pas de transport
        // central sur le live (flux non seekable) : la jauge reflète l'EPG et
        // n'est pas interactive.
        if (playbackError == null) PlayerOverlayHost(
            isVisible = showOverlay,
            isInPipMode = isInPipMode,
            isAutoHideBlocked = showChannelList,
            isPlaying = isPlaying,
            interactionKey = keyInteractionTick,
            onVisibilityChanged = { showOverlay = it }
        ) {
            val epgCurrent = playerEpg?.current
            val epgNext = playerEpg?.next
            val epgFraction = epgCurrent?.let {
                val total = it.endTimestamp - it.startTimestamp
                if (total <= 0) 0f else ((nowSec - it.startTimestamp).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } ?: 0f
            val hmFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
            val hmsFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }

            PlayerOverlayGradients(topHeight = 120.dp, bottomHeight = 200.dp)

            // Barre supérieure : retour (gauche) — PIP + format (droite)
            PlayerOverlayTopBar {
                PlayerTopButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    onClick = handleClose,
                    // Pas de transport central sur le live : le focus initial du
                    // pad se pose sur le premier bouton de la barre.
                    modifier = if (isTv) Modifier.tvInitialFocusTarget(controlsFocus) else Modifier
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
                            resizeModeNotification = "Format : ${when (nextMode) {
                                ResizeMode.FIT -> "Ajuster"
                                ResizeMode.FILL -> "Étirer"
                                ResizeMode.ZOOM -> "Zoom"
                            }}"
                        }
                    )
                }
            }

            // Bloc inférieur : logo + chaîne + résolution + EPG (informatif) + Chaînes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 78.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentStream.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = currentStream.streamIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        } else {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentStream.name,
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

                        // EPG en cours + suivant (informatif, aucune interaction)
                        if (epgCurrent != null) {
                            Text(
                                text = "${epgCurrent.formattedTimeRange()}  ${epgCurrent.title}",
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (epgNext != null) {
                            Text(
                                text = "Suivant : ${epgNext.formattedTimeRange()} ${epgNext.title}",
                                color = Color(0xB3FFFFFF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Jauge de progression EPG (lecture seule)
                        if (epgCurrent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = hmsFormat.format(java.util.Date(nowSec * 1000L)),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x55FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(epgFraction)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Text(
                                    text = hmFormat.format(java.util.Date(epgCurrent.endTimestamp * 1000L)),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Retour utilisateur du 2026-08-18 : « Qualité » à côté de « Chaînes »,
                        // pas en dessous — une seule barre d'actions, plus jamais deux rangées.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeStreamsList.isNotEmpty()) {
                                PlayerBottomAction(
                                    icon = Icons.Default.Tv,
                                    label = "Chaînes",
                                    onClick = { showChannelList = true }
                                )
                            }
                            if (qualityVariants.size > 1) {
                                PlayerBottomAction(
                                    icon = Icons.Default.AspectRatio,
                                    label = stringResource(R.string.player_quality_action_label),
                                    onClick = { showQualitySelector = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showQualitySelector) {
            QualitySelectorSheet(
                options = qualityVariants.map { variant ->
                    QualityOption(variant, variant.stream.streamId == currentStream.streamId)
                },
                onSelect = { option ->
                    switchQuality(viewModel.selectLiveQuality(option.variant), automatic = false)
                    showQualitySelector = false
                },
                onDismiss = { showQualitySelector = false },
                isTv = isTv,
                isSwitching = isBuffering
            )
        }

        qualityAdjustmentNotification?.let { message ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Surface3
            ) { Text(message, color = Color.White) }
        }

        // Channel List Drawer Overlay
        if (showChannelList && activeStreamsList.isNotEmpty() && !isInPipMode) {
            val liveTvState by viewModel.state.collectAsStateWithLifecycle()

            val initialListCategory = remember { LiveCategory("initial", "Liste de zapping", 0) }
            var selectedDrawerCategory by remember { mutableStateOf(initialListCategory) }

            val dropdownCategories = remember(liveTvState.categories) {
                // « Tout » est écarté : « Liste de zapping » joue déjà ce rôle
                // ici (les chaînes réellement zappables), les deux entrées côte
                // à côte n'étaient pas distinguables.
                listOf(initialListCategory) + liveTvState.categories.filterNot { it.categoryId == "all" }
            }

            val drawerStreams = remember(selectedDrawerCategory, liveTvState.streams, streamsList) {
                if (selectedDrawerCategory.categoryId == "initial") {
                    streamsList
                } else {
                    liveTvState.streams
                }
            }

            val listState = rememberLazyListState()
            val currentItemFocusRequester = remember { FocusRequester() }

            // Scrim: un clic en dehors du tiroir le ferme (le tiroir absorbe ses propres taps ci-dessous)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showChannelList = false })
                    }
            )

            // Auto-scroll vers la chaîne active à l'ouverture, puis lui donne le focus D-pad
            // pour que la première pression haut/bas navigue directement dans la liste.
            LaunchedEffect(showChannelList, selectedDrawerCategory, drawerStreams) {
                val isInitial = selectedDrawerCategory.categoryId == "initial"
                if (isInitial && currentStreamIndex >= 0) {
                    listState.animateScrollToItem(currentStreamIndex)
                    runCatching { currentItemFocusRequester.requestFocus() }
                } else if (!isInitial && drawerStreams.isNotEmpty()) {
                    val matchingIndex = drawerStreams.indexOfFirst { it.streamId == currentStream.streamId }
                    if (matchingIndex >= 0) {
                        listState.animateScrollToItem(matchingIndex)
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xF20F0F13)) // Semi-transparent dark background
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {}) // Absorb tap events so they don't toggle overlay
                    }
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Chaînes TV",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${drawerStreams.size} chaînes",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = { showChannelList = false }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer la liste", tint = Color.LightGray)
                        }
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF), modifier = Modifier.padding(bottom = 8.dp))

                    // Category Dropdown Selection
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Surface(
                            onClick = { dropdownExpanded = true },
                            color = Color(0x15FFFFFF),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x10FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedDrawerCategory.categoryName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .width(296.dp)
                                .background(Color(0xFF16161C))
                        ) {
                            dropdownCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = category.categoryName,
                                            color = if (category.categoryId == selectedDrawerCategory.categoryId) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (category.categoryId == selectedDrawerCategory.categoryId) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedDrawerCategory = category
                                        if (category.categoryId != "initial") {
                                            viewModel.selectCategory(category)
                                        }
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Channel List
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(drawerStreams) { index, stream ->
                            val isCurrent = if (selectedDrawerCategory.categoryId == "initial") {
                                index == currentStreamIndex
                            } else {
                                stream.streamId == currentStream.streamId
                            }
                            var isFocused by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(if (isCurrent) Modifier.focusRequester(currentItemFocusRequester) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .background(
                                        when {
                                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            isFocused -> Color(0x1EFFFFFF)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (selectedDrawerCategory.categoryId != "initial") {
                                            activeStreamsList = drawerStreams
                                        } else {
                                            activeStreamsList = streamsList
                                        }
                                        selectedQualityStream = null
                                        val newIndex = activeStreamsList.indexOfFirst { it.streamId == stream.streamId }
                                        currentStreamIndex = if (newIndex != -1) newIndex else 0
                                        streamExtension = "m3u8"
                                        showChannelList = false
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Logo
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E1E24)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!stream.streamIcon.isNullOrBlank()) {
                                            AsyncImage(
                                                model = stream.streamIcon,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CH ${stream.num}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stream.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
