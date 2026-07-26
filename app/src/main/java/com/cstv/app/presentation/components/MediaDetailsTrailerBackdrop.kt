package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerSource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val DETAILS_TRAILER_DELAY_MS = 5_000L

/** Décor non interactif : la fiche garde toujours la priorité tactile et D-Pad. */
@Composable
fun MediaDetailsTrailerBackdrop(
    media: TrailerMedia,
    state: TrailerPreviewUiState,
    posterUrl: String?,
    onContextReady: (TrailerMedia) -> Unit,
    onContextEnded: () -> Unit,
    onPlaybackFailed: (TrailerMedia) -> Unit,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleStarted = event == Lifecycle.Event.ON_START || lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_STOP) onContextEnded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media, lifecycleStarted) {
        if (!lifecycleStarted) return@LaunchedEffect
        delay(DETAILS_TRAILER_DELAY_MS)
        if (lifecycleStarted) onContextReady(media)
    }
    DisposableEffect(media) { onDispose(onContextEnded) }
    Box(modifier) {
        val preview = (state as? TrailerPreviewUiState.Playing)?.preview
        val source = preview?.source as? TrailerSource.YouTube
        if (preview?.media == media && source != null) {
            YouTubeTrailerPreview(
                videoId = source.videoId,
                muted = muted,
                posterUrl = posterUrl,
                onPlaybackError = { onPlaybackFailed(media) },
                modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        }
    }
}
