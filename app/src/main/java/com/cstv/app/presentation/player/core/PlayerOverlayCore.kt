package com.cstv.app.presentation.player.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PlayerOverlayHost(
    isVisible: Boolean,
    isInPipMode: Boolean,
    isAutoHideBlocked: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    autoHideDelayMs: Long = 5000L,
    isPlaying: Boolean = true,
    /**
     * Toute valeur qui change à chaque interaction (appui télécommande, clic).
     * Relance le compte à rebours : sans elle, l'overlay se referme au bout de
     * 5 s en pleine navigation D-pad et le focus retombe sur la vidéo.
     */
    interactionKey: Any = Unit,
    content: @Composable BoxScope.() -> Unit
) {
    LaunchedEffect(isVisible, isPlaying, isInPipMode, isAutoHideBlocked, interactionKey) {
        if (isVisible && isPlaying && !isInPipMode && !isAutoHideBlocked) {
            delay(autoHideDelayMs)
            onVisibilityChanged(false)
        }
    }

    if (isVisible && !isInPipMode) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun BoxScope.PlayerOverlayGradients(
    topHeight: Dp,
    bottomHeight: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(topHeight)
            .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
            .align(Alignment.TopCenter)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bottomHeight)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .align(Alignment.BottomCenter)
    )
}

@Composable
fun BoxScope.PlayerOverlayTopBar(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .padding(16.dp),
        content = content
    )
}
