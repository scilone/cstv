package com.cstv.app.presentation.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.publishFrom
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
import com.cstv.app.presentation.components.tvFocusHighlight
import com.cstv.app.presentation.components.YouTubeTrailerPreview
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HERO_PREVIEW_DELAY_MS = 1_500L

/** Tranche laissée visible des tendances voisines de part et d'autre. */
internal val TV_HERO_PEEK = 72.dp

/**
 * Échelle des slides voisines. À 1, la tendance suivante a exactement la taille
 * de la carte principale : c'est le focus, et non la taille, qui désigne la
 * carte active.
 */
private const val TV_HERO_SIDE_SCALE = 1f

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

    // Couche avant du focus TV (F23) : la Hero ne défile pas via un pivot
    // (TvPivotScroll) — sa position est déjà définitive dès que la carte
    // courante du pager est mesurée et que le pager a le focus. On republie
    // à chaque changement de page une fois le défilement du pager terminé.
    val tvFocusSelector = LocalTvFocusSelector.current
    var currentCardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(tvFocusSelector, hasFocus, pagerState.currentPage, pagerState.isScrollInProgress) {
        if (tvFocusSelector != null && hasFocus && !pagerState.isScrollInProgress) {
            tvFocusSelector.publishFrom(currentCardCoordinates, cornerRadius = 16.dp)
        }
    }

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
        // Aligné à gauche sur les rangées de l'accueil : seule la tendance
        // suivante dépasse, à droite.
        contentPadding = PaddingValues(start = 0.dp, end = TV_HERO_PEEK),
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
                // Capturé après le padding, juste avant le `clip(shape)` de
                // HomeTrendingSlideTv : ce sont les bounds de la surface
                // effectivement visible/clippée, pas celles de la zone de page
                // du pager (Review F23, Majeur R4).
                .then(
                    if (isCurrent) {
                        Modifier.onGloballyPositioned { currentCardCoordinates = it }
                    } else {
                        Modifier
                    }
                )
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
    val shape = RoundedCornerShape(16.dp)
    val imageUrl = item.trendingTitle.landscapeImageUrl()

    Box(
        modifier = modifier
            .clip(shape)
            .tvFocusHighlight(borderAlpha > 0.5f, shape)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = item.trendingTitle.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (videoId != null && preview != null) {
            // Sur TV l'aperçu est sonore d'emblée : la télécommande porte déjà
            // le volume, un contrôle Mute dédié n'apporte rien.
            YouTubeTrailerPreview(
                videoId = videoId,
                muted = false,
                posterUrl = imageUrl,
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

    }
}
