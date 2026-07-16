package com.poc.iptvxtream.presentation.player

import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.poc.iptvxtream.presentation.theme.Surface3
import kotlinx.coroutines.delay
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

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
fun PlayerScreen(
    initialStream: LiveStream,
    streamsList: List<LiveStream>,
    credentials: Credentials,
    isTv: Boolean,
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
    var currentStreamIndex by remember {
        mutableStateOf(streamsList.indexOfFirst { it.streamId == initialStream.streamId })
    }
    val currentStream = remember(currentStreamIndex) { streamsList.getOrNull(currentStreamIndex) ?: initialStream }

    LaunchedEffect(currentStream) {
        onStreamChanged(currentStream)
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }
    var streamExtension by remember { mutableStateOf("m3u8") } // Default to m3u8

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
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun zapNext() {
        if (streamsList.isNotEmpty()) {
            currentStreamIndex = (currentStreamIndex + 1) % streamsList.size
            showOverlay = true
            streamExtension = "m3u8" // Reset extension to default on zap
        }
    }

    fun zapPrev() {
        if (streamsList.isNotEmpty()) {
            currentStreamIndex = if (currentStreamIndex - 1 < 0) streamsList.size - 1 else currentStreamIndex - 1
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chargement du flux...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
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

        // Custom Overlay UI (Auto-hides)
        if (showOverlay && playbackError == null) {
            // Top Bar: Close Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x80000000))
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CardDefaults.shape)
                                .background(Surface3),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentStream.streamIcon.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentStream.streamIcon,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CH ${currentStream.num} - ${currentStream.name}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Flux : ${streamExtension.uppercase()}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (streamsList.isNotEmpty()) {
                            IconButton(
                                onClick = { showChannelList = !showChannelList },
                                modifier = Modifier.background(Color(0x40FFFFFF), shape = CardDefaults.shape)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Liste des chaînes", tint = Color.White)
                            }
                        }

                        IconButton(
                            onClick = handleClose,
                            modifier = Modifier.background(Color(0x40FFFFFF), shape = CardDefaults.shape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Panel: Info Prompt help on TV / Mobile
            if (!isTv) {
                // Mobile zapping prompt help
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x99000000))
                    ) {
                        Text(
                            text = "Glissez vers le haut / bas pour zapper",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                // TV zapping prompt help
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x99000000))
                    ) {
                        Text(
                            text = "Utilisez ▲ / ▼ de la télécommande pour zapper",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Channel List Drawer Overlay
        if (showChannelList && streamsList.isNotEmpty()) {
            val listState = rememberLazyListState()
            
            // Auto-scroll to the active channel when opened
            LaunchedEffect(showChannelList) {
                if (currentStreamIndex >= 0) {
                    listState.animateScrollToItem(currentStreamIndex)
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
                                text = "${streamsList.size} chaînes",
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

                    HorizontalDivider(color = Color(0x20FFFFFF), modifier = Modifier.padding(bottom = 12.dp))

                    // Channel List
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(streamsList) { index, stream ->
                            val isCurrent = index == currentStreamIndex
                            var isFocused by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
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
                                        currentStreamIndex = index
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
