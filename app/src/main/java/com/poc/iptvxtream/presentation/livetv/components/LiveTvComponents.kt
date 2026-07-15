package com.poc.iptvxtream.presentation.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.presentation.rememberForeverLazyListState
import kotlinx.coroutines.delay

@Composable
fun CategorySectionRow(
    categoryId: String,
    title: String,
    streams: List<LiveStream>,
    favoritesList: List<FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )

        val rowState = rememberForeverLazyListState("livetv_row_${categoryId}", getScroll, saveScroll)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(streams) { stream ->
                val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                if (isTv) {
                    StreamTvCard(
                        stream = stream,
                        isFavorite = isFav,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onToggleFavorite = { onToggleFavorite(stream) },
                        onClick = { onStreamSelected(stream) }
                    )
                } else {
                    MobileStreamCard(
                        stream = stream,
                        isFavorite = isFav,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onToggleFavorite = { onToggleFavorite(stream) },
                        onClick = { onStreamSelected(stream) }
                    )
                }
            }
        }
    }
}

@Composable
fun MobileStreamCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        modifier = Modifier
            .width(150.dp)
            // Hauteur fixe (Phase 33) : idem, une chaîne sans EPG résolue ne
            // doit pas produire une tuile plus petite dans la rangée horizontale.
            .height(180.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0F13)),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stream.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (epgProgram != null) {
                Text(
                    text = epgProgram.title,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                EpgProgressBar(
                    program = epgProgram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                )
                Text(
                    text = epgProgram.formattedTimeRange(),
                    color = Color.Gray,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(
                    text = "CH ${stream.num}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color.Yellow else Color.DarkGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFilterChip(
    category: LiveCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> Color(0xFF2C2C35)
                    else -> Color(0xFF2A2A35)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = category.categoryName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EpgProgressBar(
    program: LiveEpgProgram,
    modifier: Modifier = Modifier
) {
    // Recomputed periodically so the bar keeps advancing while the program plays
    var progress by remember(program) { mutableStateOf(program.getProgressFraction()) }
    LaunchedEffect(program) {
        while (true) {
            progress = program.getProgressFraction()
            delay(30_000)
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
fun RecentlyWatchedRow(
    streams: List<LiveStream>,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "RÉCEMMENT REGARDÉES",
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )

        val rowState = rememberForeverLazyListState("livetv_recently_watched", getScroll, saveScroll)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(streams) { stream ->
                RecentlyWatchedTvItem(
                    stream = stream,
                    epgProgram = epgPrograms[stream.streamId],
                    onLoadEpg = { onLoadEpg(stream.streamId) },
                    onClick = { onStreamSelected(stream) }
                )
            }
        }
    }
}

@Composable
fun RecentlyWatchedTvItem(
    stream: LiveStream,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Color(0xFF1E1E24)
        ),
        modifier = Modifier
            .width(180.dp)
            .height(72.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp).fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0F13)),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CH ${stream.num} ${stream.name}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (epgProgram != null) {
                    Text(
                        text = epgProgram.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    EpgProgressBar(
                        program = epgProgram,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = epgProgram.formattedTimeRange(),
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StreamTvCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Color(0xFF1E1E24)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp).fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F13)),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CH ${stream.num} ${stream.name}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (epgProgram != null) {
                    Text(
                        text = epgProgram.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    EpgProgressBar(
                        program = epgProgram,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                    )
                    Text(
                        text = epgProgram.formattedTimeRange(),
                        color = Color.Gray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "Aucune information de programme",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Favorite Star on focus or favorite status
            if (isFocused || isFavorite) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color.Yellow else Color.DarkGray
                    )
                }
            }
        }
    }
}
