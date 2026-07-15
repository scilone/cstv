package com.poc.iptvxtream.presentation.home.components
import com.poc.iptvxtream.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.model.SeriesStream

@Composable
fun HomeResumeWatchingCard(
    position: PlaybackPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(220.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (position.durationMs > 0) {
        position.positionMs.toFloat() / position.durationMs.toFloat()
    } else 0f

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .background(Color(0xFF0F0F13))
        ) {
            if (!position.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = position.coverUrl,
                    contentDescription = position.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(36.dp))
                }
            }

            // Dark semi-transparent gradient at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                            startY = 60f
                        )
                    )
            )

            // Play Icon Overlay in center
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .background(Color(0x80000000), shape = RoundedCornerShape(18.dp))
                    .padding(6.dp)
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .align(Alignment.BottomCenter)
            )
        }

        // Title
        Text(
            text = position.title ?: stringResource(R.string.home_no_title),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}

@Composable
fun HomeFavoriteItemCard(
    favorite: FavoriteItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(130.dp)
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!favorite.cover.isNullOrBlank()) {
                AsyncImage(
                    model = favorite.cover,
                    contentDescription = favorite.name,
                    // Les logos de chaînes Live TV (Phase 34) sont au format carré :
                    // les faire tenir entièrement (Fit) plutôt que les rogner (Crop),
                    // qui reste adapté aux affiches films/séries déjà proches du 2:3.
                    contentScale = if (favorite.type == "live") ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Type Badge (top left)
            val badgeColor = when (favorite.type) {
                "live" -> Color(0xFFE50914) // Red for live direct
                "movie" -> Color(0xFF0070F3) // Blue for movies
                "series" -> Color(0xFF8A2BE2) // Purple for series
                else -> Color.Gray
            }
            val badgeLabel = when (favorite.type) {
                "live" -> stringResource(R.string.home_live_badge)
                "movie" -> stringResource(R.string.home_movie_badge)
                "series" -> stringResource(R.string.home_series_badge)
                else -> "FAV"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeLabel,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title
        Text(
            text = favorite.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HomeLiveTvCard(
    stream: LiveStream,
    epgProgram: com.poc.iptvxtream.domain.model.LiveEpgProgram?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Phase 42 : le polling EPG (toutes les 60s) est centralisé dans
    // HomeViewModel (un seul ticker pour toute la rangée) au lieu d'une
    // boucle par carte visible ; cette carte ne fait plus que lire
    // state.epgPrograms.
    Column(
        modifier = Modifier
            .width(140.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!stream.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = stream.streamIcon,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = stream.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Channel number badge (bottom left)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "CH ${stream.num}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stream.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )

        if (epgProgram != null) {
            Text(
                text = epgProgram.title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                textAlign = TextAlign.Center
            )
            HomeEpgProgressBar(
                program = epgProgram,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
            )
        } else {
            Text(
                text = stringResource(R.string.home_no_program),
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HomeEpgProgressBar(
    program: com.poc.iptvxtream.domain.model.LiveEpgProgram,
    modifier: Modifier = Modifier
) {
    // Recomputed periodically so the bar keeps advancing while the program plays
    var progress by remember(program) { mutableStateOf(program.getProgressFraction()) }
    LaunchedEffect(program) {
        while (true) {
            progress = program.getProgressFraction()
            kotlinx.coroutines.delay(30_000)
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.DarkGray,
        modifier = modifier
    )
}

@Composable
fun HomeVodMovieCard(
    stream: VodStream,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp) // standard 2:3 ratio
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!stream.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = stream.streamIcon,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Rating Badge (top right)
            val cleanRating = stream.rating?.trim()
            if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(cleanRating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Title
        Text(
            text = stream.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HomeSeriesShowCard(
    stream: SeriesStream,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp) // standard 2:3 ratio
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!stream.cover.isNullOrBlank()) {
                AsyncImage(
                    model = stream.cover,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Rating Badge (top right)
            val cleanRating = stream.rating?.trim()
            if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(cleanRating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Title
        Text(
            text = stream.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
    }
}
