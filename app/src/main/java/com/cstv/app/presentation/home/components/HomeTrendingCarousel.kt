package com.cstv.app.presentation.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.components.TrailerPreviewUiState
import kotlinx.coroutines.delay

/**
 * Nombre de répétitions de la liste exposées au pager pour simuler une boucle
 * sans fin. Le pager ne sait pas boucler nativement : on lui présente la liste
 * un grand nombre de fois et on ramène l'index par modulo. En démarrant au
 * milieu, l'utilisateur atteindrait la borne après des centaines de swipes.
 */
private const val CAROUSEL_LOOPS = 1000

/** Largeur de la tranche laissée visible de chaque slide voisine. */
internal val CAROUSEL_PEEK = 20.dp

/** Échelle des slides voisines ; la slide courante reste à 1. */
private const val CAROUSEL_SIDE_SCALE = 0.88f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrendingCarousel(
    trendingItems: List<TrendingCatalogItem>,
    trailerPreview: TrailerPreviewUiState,
    onActiveItemChanged: (TrendingCatalogItem?) -> Unit,
    onPreviewContextEnded: () -> Unit,
    onPreviewPlaybackFailed: (TrailerMedia) -> Unit,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemCount = trendingItems.size
    // Une seule tendance ne boucle pas : répéter l'unique slide n'apporterait
    // rien et laisserait croire à un défilement possible.
    val loops = if (itemCount > 1) CAROUSEL_LOOPS else 1
    val pageCount = itemCount * loops
    // Démarrage aligné sur un début de cycle, pour que la première slide
    // affichée soit bien la première tendance.
    val initialPage = if (itemCount > 1) (pageCount / 2) - (pageCount / 2) % itemCount else 0

    val pagerState = rememberPagerState(pageCount = { pageCount })
    // `rememberPagerState` fige sa page initiale à la première composition, or
    // la liste arrive après le chargement réseau : le recentrage doit donc être
    // fait explicitement dès que la taille de la liste est connue.
    LaunchedEffect(itemCount) {
        if (itemCount > 0) pagerState.scrollToPage(initialPage)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activeItem = trendingItems.getOrNull(pagerState.currentPage.floorModOrZero(itemCount))
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var pageStableForPreview by remember(activeItem?.trendingTitle?.canonicalId) { mutableStateOf(false) }

    // [Asymmetry Note (m2)]
    // Mobile carousel uses a double-gated system:
    // 1. Immediately request the trailer metadata (onActiveItemChanged) when the card starts stabilizing
    //    so TMDB/YouTube resolution runs in background while the user waits.
    // 2. Gate the actual WebView composition with pageStableForPreview, which only becomes true after
    //    1.5 seconds of continuous stability.
    // This is different from TV where the request itself is delayed and the WebView is not double-gated.
    LaunchedEffect(activeItem?.trendingTitle?.canonicalId, pagerState.isScrollInProgress, lifecycleStarted) {
        pageStableForPreview = false
        // [m3] End the previous context immediately upon any card/scroll/lifecycle change
        // to prevent obsolete state (e.g. state.Playing) from carrying over.
        onPreviewContextEnded()
        if (!TrailerPreviewDwellPolicy.isEligible(activeItem, pagerState.isScrollInProgress, lifecycleStarted)) {
            return@LaunchedEffect
        }
        // [M1] Instantly notify VM about active item change so resolution starts immediately.
        onActiveItemChanged(activeItem)
        // Delay WebView instantiation until we are sure user has settled on this card (Dwell Time).
        delay(TRAILER_PREVIEW_DWELL_MS)
        pageStableForPreview = true
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

    Box(
        modifier = modifier
            .height(470.dp)
            .fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            // Laisse dépasser une tranche des slides voisines de chaque côté.
            contentPadding = PaddingValues(horizontal = CAROUSEL_PEEK),
            // Les voisines ne sont que partiellement dans le viewport : sans
            // cette marge de composition, elles apparaîtraient vides au début
            // du geste, le temps que leur affiche se charge.
            beyondBoundsPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // trendingItems peut rétrécir entre deux recompositions (ex. filtre
            // catégorie masquée recalculé après un premier chargement) alors que
            // pagerState.currentPage garde un index désormais hors bornes ->
            // IndexOutOfBoundsException à la composition si on utilise [page] sans
            // garde-fou (crash "après un moment", hors de tout try/catch de fetch).
            val item = trendingItems.getOrNull(page.floorModOrZero(itemCount)) ?: return@HorizontalPager
            val streamId = item.matchedMovie?.streamId ?: item.matchedSeries?.seriesId ?: 0
            val isMovie = item.matchedMovie != null
            val isActive = page == pagerState.currentPage
            val preview = (trailerPreview as? TrailerPreviewUiState.Playing)?.preview
            val activeCatalogId = item.matchedMovie?.streamId ?: item.matchedSeries?.seriesId
            val videoId = preview
                ?.takeIf {
                    isActive && pageStableForPreview &&
                        it.media.catalogId == activeCatalogId &&
                        ((isMovie && it.media is TrailerMedia.Movie) ||
                            (!isMovie && it.media is TrailerMedia.Series))
                }
                ?.source
                ?.let { source -> (source as? TrailerSource.YouTube)?.videoId }
            var muted by remember(videoId) { mutableStateOf(true) }
            // Vrai une fois l'aperçu réellement révélé (poster de cover retiré) :
            // gate le bouton son pour qu'il apparaisse AVEC la vidéo, pas pendant
            // la phase de cover.
            var previewVisible by remember(videoId) { mutableStateOf(false) }

            // Position relative à la slide courante, en pages : 0 au centre,
            // -1 pour la voisine de droite, +1 pour celle de gauche. Suit le
            // geste en continu, d'où la transition progressive — la slide qui
            // part rétrécit pendant que celle qui arrive grandit.
            val relativeOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            val scale = lerp(CAROUSEL_SIDE_SCALE, 1f, 1f - relativeOffset.absoluteValue)

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        // Chaque voisine se réduit vers le bord d'écran qui la
                        // borde, et non vers son centre : autrement elle recule
                        // d'autant que la réduction, ce qui consomme la tranche
                        // laissée visible par `contentPadding` et la fait
                        // pratiquement disparaître. Le pivot glisse avec le
                        // geste, donc sans rupture au passage par le centre.
                        transformOrigin = TransformOrigin(0.5f + 0.5f * relativeOffset, 0.5f)
                    }
                    .padding(horizontal = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Poster backdrop
                    AsyncImage(
                        model = item.trendingTitle.posterUrl,
                        contentDescription = item.trendingTitle.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (videoId != null) {
                        com.cstv.app.presentation.components.YouTubeTrailerPreview(
                            videoId = videoId,
                            muted = muted,
                            onPlaybackError = { onPreviewPlaybackFailed(preview.media) },
                            posterUrl = item.trendingTitle.posterUrl,
                            onRevealed = { previewVisible = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // The navigation target deliberately sits over the embedded WebView;
                    // otherwise the player consumes taps intended to open the detail page.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                onPreviewContextEnded()
                                if (isMovie) onMovieClick(streamId) else onSeriesClick(streamId)
                            }
                    )

                    // 2. Cinematic overlay gradient (scrim)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // 3. Badge type de média (haut-gauche), style neutre "sans
                    // couleur" — cohérent avec HomeFavoriteItemCard.
                    HomeMediaTypeBadge(
                        label = if (isMovie) "FILM" else "SÉRIE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )

                    // 4. Titre + année accolée (bas)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = item.trendingTitle.year?.let { year -> "${item.trendingTitle.title} · $year" }
                                ?: item.trendingTitle.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (videoId != null && previewVisible) {
                        IconButton(
                            onClick = { muted = !muted },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = .55f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (muted) "Activer le son de l'aperçu" else "Couper le son de l'aperçu",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Index dans la liste correspondant à une page virtuelle du carrousel infini.
 * Renvoie 0 sur une liste vide plutôt que de lever une division par zéro.
 */
private fun Int.floorModOrZero(size: Int): Int = if (size <= 0) 0 else Math.floorMod(this, size)
