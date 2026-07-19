package com.cstv.app.presentation.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    Box(
        modifier = modifier
            .height(280.dp)
            .fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = trendingItems[page]
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
