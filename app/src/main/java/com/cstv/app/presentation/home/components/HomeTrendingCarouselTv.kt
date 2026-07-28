package com.cstv.app.presentation.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.presentation.components.YouTubeTrailerPreview
import com.cstv.app.presentation.theme.AccentLavande
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HERO_PREVIEW_DELAY_MS = 1_500L

/** Tranche laissée visible des tendances voisines de part et d'autre. */
private val TV_HERO_PEEK = 72.dp

/** Échelle des slides voisines ; la slide courante reste à 1. */
private const val TV_HERO_SIDE_SCALE = 0.86f

/**
 * Carrousel de tendances de l'accueil TV.
 *
 * Un seul nœud focusable : la carte courante reste la carte principale, et
 * Gauche/Droite font défiler les tendances au lieu de déplacer le focus. Sur la
 * première slide, Gauche n'est pas consommée, ce qui laisse le système donner le
 * focus à la barre latérale de navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrendingCarouselTv(
    trendingItems: List<TrendingCatalogItem>,
    trailerPreview: TrailerPreviewUiState,
    onPreviewRequested: (TrendingCatalogItem) -> Unit,
    onPreviewContextEnded: () -> Unit,
    onPreviewPlaybackFailed: (TrailerMedia) -> Unit,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trendingItems.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { trendingItems.size })
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    val currentItem = trendingItems.getOrNull(pagerState.currentPage)
    val previewEligible = hasFocus && lifecycleStarted && !pagerState.isScrollInProgress

    // La clé inclut la slide courante : changer de tendance annule l'aperçu en
    // cours et repart d'une temporisation complète.
    LaunchedEffect(previewEligible, currentItem?.trendingTitle?.tmdbId) {
        if (!previewEligible || currentItem == null) {
            onPreviewContextEnded()
            return@LaunchedEffect
        }
        delay(HERO_PREVIEW_DELAY_MS)
        onPreviewRequested(currentItem)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> lifecycleStarted = true
                Lifecycle.Event.ON_STOP -> lifecycleStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onPreviewContextEnded()
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = TV_HERO_PEEK),
        beyondBoundsPageCount = 1,
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        // Bord gauche non consommé : c'est la sortie vers la
                        // barre latérale de navigation.
                        if (pagerState.currentPage <= 0) return@onKeyEvent false
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        true
                    }
                    Key.DirectionRight -> {
                        if (pagerState.currentPage >= trendingItems.lastIndex) return@onKeyEvent false
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        val item = trendingItems.getOrNull(pagerState.currentPage)
                            ?: return@onKeyEvent false
                        onPreviewContextEnded()
                        val movieId = item.matchedMovie?.streamId
                        val seriesId = item.matchedSeries?.seriesId
                        when {
                            movieId != null -> onMovieClick(movieId)
                            seriesId != null -> onSeriesClick(seriesId)
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    else -> false
                }
            }
            // Un unique nœud focusable : la carte courante reste la carte
            // principale et le D-pad pilote le défilement, pas le focus.
            .focusable()
    ) { page ->
        val item = trendingItems.getOrNull(page) ?: return@HorizontalPager
        val isCurrent = page == pagerState.currentPage
        val relativeOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .coerceIn(-1f, 1f)
        val scale = lerp(TV_HERO_SIDE_SCALE, 1f, 1f - relativeOffset.absoluteValue)
        val borderAlpha by animateFloatAsState(
            if (isCurrent && hasFocus) 1f else 0f,
            label = "tvHeroFocusBorder"
        )

        HomeTrendingSlideTv(
            item = item,
            borderAlpha = borderAlpha,
            trailerPreview = trailerPreview,
            previewEnabled = isCurrent && previewEligible,
            onPreviewPlaybackFailed = onPreviewPlaybackFailed,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // Pivot glissant : chaque voisine se réduit vers le bord
                    // d'écran qui la borde, sinon elle recule et la tranche
                    // laissée visible par `contentPadding` disparaît.
                    transformOrigin = TransformOrigin(0.5f + 0.5f * relativeOffset, 0.5f)
                }
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun HomeTrendingSlideTv(
    item: TrendingCatalogItem,
    borderAlpha: Float,
    trailerPreview: TrailerPreviewUiState,
    previewEnabled: Boolean,
    onPreviewPlaybackFailed: (TrailerMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    val movieId = item.matchedMovie?.streamId
    val seriesId = item.matchedSeries?.seriesId
    val media = when {
        movieId != null -> TrailerMedia.Movie(movieId, item.trendingTitle.tmdbId)
        seriesId != null -> TrailerMedia.Series(seriesId, item.trendingTitle.tmdbId)
        else -> null
    }
    val preview = (trailerPreview as? TrailerPreviewUiState.Playing)?.preview
        ?.takeIf { previewEnabled && it.media == media }
    val videoId = (preview?.source as? TrailerSource.YouTube)?.videoId
    var muted by remember(videoId) { mutableStateOf(true) }
    var previewVisible by remember(videoId) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val imageUrl = item.trendingTitle.landscapeImageUrl()

    Box(
        modifier = modifier
            .clip(shape)
            .border(3.dp, AccentLavande.copy(alpha = borderAlpha), shape)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = item.trendingTitle.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (videoId != null && preview != null) {
            YouTubeTrailerPreview(
                videoId = videoId,
                muted = muted,
                posterUrl = imageUrl,
                onRevealed = { previewVisible = true },
                onPlaybackError = { onPreviewPlaybackFailed(preview.media) },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Même badge que le carrousel mobile : le type de média porte
        // l'étiquette, il n'y a plus de badge « TENDANCE » propriétaire.
        HomeMediaTypeBadge(
            label = if (movieId != null) "FILM" else "SÉRIE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
        )

        Text(
            text = item.trendingTitle.year?.let { "${item.trendingTitle.title} · $it" }
                ?: item.trendingTitle.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        )

        if (videoId != null && previewVisible) {
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (muted) "Activer le son de l'aperçu" else "Couper le son de l'aperçu",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
