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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.TrendingCatalogItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrendingCarousel(
    trendingItems: List<TrendingCatalogItem>,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { trendingItems.size })

    // Recale sur la première page si la liste rétrécit sous la page courante
    // (ex. recalcul du filtre catégorie masquée) pour éviter une page vide.
    LaunchedEffect(trendingItems.size) {
        if (trendingItems.isNotEmpty() && pagerState.currentPage >= trendingItems.size) {
            pagerState.scrollToPage(0)
        }
    }

    Box(
        modifier = modifier
            .height(280.dp)
            .fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // trendingItems peut rétrécir entre deux recompositions (ex. filtre
            // catégorie masquée recalculé après un premier chargement) alors que
            // pagerState.currentPage garde un index désormais hors bornes ->
            // IndexOutOfBoundsException à la composition si on utilise [page] sans
            // garde-fou (crash "après un moment", hors de tout try/catch de fetch).
            val item = trendingItems.getOrNull(page) ?: return@HorizontalPager
            val streamId = item.matchedMovie?.streamId ?: item.matchedSeries?.seriesId ?: 0
            val isMovie = item.matchedMovie != null

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .clickable {
                        if (isMovie) {
                            onMovieClick(streamId)
                        } else {
                            onSeriesClick(streamId)
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Poster backdrop
                    AsyncImage(
                        model = item.trendingTitle.posterUrl,
                        contentDescription = item.trendingTitle.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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

                    // 3. Badge "Tendance" and Content info
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top-left badge "Tendance"
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "★ TENDANCE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Bottom text metadata
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = item.trendingTitle.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                              ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isMovie) "FILM" else "SÉRIE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                item.trendingTitle.year?.let { year ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = year,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Page indicator dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(trendingItems.size) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Gray.copy(alpha = 0.5f)
                            }
                        )
                )
            }
        }
    }
}

/**
 * Variante TV du carrousel tendances : une rangée horizontale de vignettes
 * poster focusables au D-pad (le HorizontalPager mobile n'est pas navigable au
 * D-pad). Cohérente avec HomeVodMovieCard (même ratio 2:3, même bordure de
 * focus). Clic → détail Film/Série existant.
 */
@Composable
fun HomeTrendingRowTv(
    trendingItems: List<TrendingCatalogItem>,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(trendingItems) { item ->
            HomeTrendingPosterCardTv(
                item = item,
                onClick = {
                    val movieId = item.matchedMovie?.streamId
                    val seriesId = item.matchedSeries?.seriesId
                    when {
                        movieId != null -> onMovieClick(movieId)
                        seriesId != null -> onSeriesClick(seriesId)
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeTrendingPosterCardTv(
    item: TrendingCatalogItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(195.dp) // ratio 2:3, aligné sur HomeVodMovieCard
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.trendingTitle.posterUrl,
            contentDescription = item.trendingTitle.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Scrim bas pour lisibilité du titre
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Badge "Tendance" (haut-gauche)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "★ TENDANCE",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Titre (bas)
        Text(
            text = item.trendingTitle.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}
