package com.poc.iptvxtream.presentation.player

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
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.presentation.theme.Surface3
import kotlinx.coroutines.delay
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.VideoSize
import com.poc.iptvxtream.data.local.storage.ResizeMode
import com.poc.iptvxtream.presentation.livetv.LiveTvViewModel

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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

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
    
    // Prevent screen lock during playback
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
    val currentStream = remember(currentStreamIndex, activeStreamsList) { activeStreamsList.getOrNull(currentStreamIndex) ?: initialStream }

    LaunchedEffect(currentStream) {
        onStreamChanged(currentStream)
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }
    var streamExtension by remember { mutableStateOf("m3u8") } // Default to m3u8
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

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
        if (isInPipMode) {
            showChannelList = false
        }
        // Le SurfaceView interne au PlayerView ne se relayout pas tout seul
        // au redimensionnement de la fenêtre PIP (bug connu ExoPlayer/Media3) :
        // un cycle invisible/visible force un vrai passage de layout.
        playerViewRef?.let { view ->
            view.visibility = android.view.View.INVISIBLE
            view.post { view.visibility = android.view.View.VISIBLE }
        }
    }

    // Auto-hide overlay after 5 seconds of inactivity
    LaunchedEffect(showOverlay, showChannelList) {
        if (showOverlay && !showChannelList) {
            delay(5000)
            showOverlay = false
        }
    }

    // Prepare and play the stream whenever the stream index or extension changes
    LaunchedEffect(currentStream, streamExtension) {
        isBuffering = true
        playbackError = null
        
        val url = currentStream.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password,
            extension = streamExtension
        )

        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                // If m3u8 fails, fallback once to TS format
                if (streamExtension == "m3u8") {
                    streamExtension = "ts"
                } else {
                    isBuffering = false
                    playbackError = "Impossible de charger le flux vidéo (${error.localizedMessage})"
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun zapNext() {
        if (activeStreamsList.isNotEmpty()) {
            currentStreamIndex = (currentStreamIndex + 1) % activeStreamsList.size
            showOverlay = true
            streamExtension = "m3u8" // Reset extension to default on zap
        }
    }

    fun zapPrev() {
        if (activeStreamsList.isNotEmpty()) {
            currentStreamIndex = if (currentStreamIndex - 1 < 0) activeStreamsList.size - 1 else currentStreamIndex - 1
            showOverlay = true
            streamExtension = "m3u8" // Reset extension to default on zap
        }
    }

    // Full screen capture key events for TV zapping and gesture capture for Mobile swipe zapping
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (showChannelList) {
                                false
                            } else {
                                zapPrev()
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (showChannelList) {
                                false
                            } else {
                                zapNext()
                                true
                            }
                        }
                        Key.Back -> {
                            if (showChannelList) {
                                showChannelList = false
                                true
                            } else {
                                handleClose()
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
                    Text("Chargement du flux...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

        // Custom Overlay UI (Auto-hides) — refonte Phase 60. Pas de transport
        // central sur le live (flux non seekable) : la jauge reflète l'EPG et
        // n'est pas interactive.
        if (showOverlay && playbackError == null && !isInPipMode) {
            val epgCurrent = playerEpg?.current
            val epgNext = playerEpg?.next
            val epgFraction = epgCurrent?.let {
                val total = it.endTimestamp - it.startTimestamp
                if (total <= 0) 0f else ((nowSec - it.startTimestamp).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } ?: 0f
            val hmFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
            val hmsFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }

            // Dégradés haut/bas.
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
                    .height(200.dp)
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

                        // Barre d'actions : « Chaînes » (ouvre le tiroir de zapping)
                        if (activeStreamsList.isNotEmpty()) {
                            PlayerBottomAction(
                                icon = Icons.Default.Tv,
                                label = "Chaînes",
                                onClick = { showChannelList = true }
                            )
                        }
                    }
                }
            }
        }

        // Channel List Drawer Overlay
        if (showChannelList && activeStreamsList.isNotEmpty() && !isInPipMode) {
            val liveTvState by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (viewModel.state.value.categories.isEmpty()) {
                    viewModel.loadCategories()
                }
            }

            val initialListCategory = remember { LiveCategory("initial", "Liste de zapping", 0) }
            var selectedDrawerCategory by remember { mutableStateOf(initialListCategory) }

            val dropdownCategories = remember(liveTvState.categories) {
                listOf(initialListCategory) + liveTvState.categories
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
