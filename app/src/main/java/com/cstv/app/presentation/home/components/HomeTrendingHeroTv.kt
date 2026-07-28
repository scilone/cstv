package com.cstv.app.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.presentation.components.YouTubeTrailerPreview
import com.cstv.app.presentation.theme.AccentLavande
import kotlinx.coroutines.delay

private const val HERO_PREVIEW_DELAY_MS = 1_500L

@Composable
fun HomeTrendingHeroTv(
    item: TrendingCatalogItem,
    trailerPreview: TrailerPreviewUiState,
    onPreviewRequested: (TrendingCatalogItem) -> Unit,
    onPreviewContextEnded: () -> Unit,
    onPreviewPlaybackFailed: (TrailerMedia) -> Unit,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    val movieId = item.matchedMovie?.streamId
    val seriesId = item.matchedSeries?.seriesId
    val media = if (movieId != null) TrailerMedia.Movie(movieId, item.trendingTitle.tmdbId) else seriesId?.let { TrailerMedia.Series(it, item.trendingTitle.tmdbId) }
    val preview = (trailerPreview as? TrailerPreviewUiState.Playing)?.preview
        ?.takeIf { it.media == media }
    val videoId = (preview?.source as? TrailerSource.YouTube)?.videoId
    var previewVisible by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(hasFocus, item.trendingTitle.tmdbId) {
        if (!hasFocus) {
            onPreviewContextEnded()
            return@LaunchedEffect
        }
        delay(HERO_PREVIEW_DELAY_MS)
        onPreviewRequested(item)
    }
    DisposableEffect(Unit) {
        onDispose { onPreviewContextEnded() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .focusGroup()
            .onFocusChanged { hasFocus = it.hasFocus }
            .border(2.dp, if (hasFocus) AccentLavande else Color.Transparent, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                onPreviewContextEnded()
                if (movieId != null) onMovieClick(movieId) else if (seriesId != null) onSeriesClick(seriesId)
            }
    ) {
        AsyncImage(item.trendingTitle.posterUrl, item.trendingTitle.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (videoId != null && preview != null) {
            YouTubeTrailerPreview(
                videoId = videoId,
                muted = muted,
                posterUrl = item.trendingTitle.posterUrl,
                onRevealed = { previewVisible = true },
                onPlaybackError = { onPreviewPlaybackFailed(preview.media) },
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
        Text(
            text = "TENDANCE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp).background(AccentLavande.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(PaddingValues(horizontal = 9.dp, vertical = 5.dp))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(if (movieId != null) "FILM" else "SÉRIE", color = AccentLavande, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(item.trendingTitle.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            item.trendingTitle.year?.let { Text(it.toString(), color = Color.LightGray, fontSize = 16.sp) }
        }
        if (videoId != null && previewVisible) {
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Activer le son de l'aperçu" else "Couper le son de l'aperçu", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}
