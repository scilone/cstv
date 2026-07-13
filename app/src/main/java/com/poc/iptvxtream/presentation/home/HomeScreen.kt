package com.poc.iptvxtream.presentation.home

import com.poc.iptvxtream.presentation.rememberForeverLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.UserInfo
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.model.SeriesStream

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    userInfo: UserInfo,
    isTv: Boolean,
    viewModel: HomeViewModel,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToVod: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPlayResumeWatchingMovie: (PlaybackPosition) -> Unit,
    onPlayResumeWatchingSeries: (PlaybackPosition) -> Unit,
    onPlayLiveStream: (LiveStream, List<LiveStream>) -> Unit,
    onSelectMovieDetail: (VodStream) -> Unit,
    onSelectSeriesDetail: (SeriesStream) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val state by viewModel.state.collectAsState()

    // Refresh home data when entering screen
    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Header (Welcome, Profile Info and Navigation Row)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (isTv) 54.dp else 40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "BIENVENUE, ${userInfo.username.uppercase()}",
                                    fontSize = if (isTv) 20.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Expiration : ${userInfo.expiryDate} | Max connexions : ${userInfo.maxConnections}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Settings Button (replacing Refresh Button)
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.background(Color(0x22FFFFFF), shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = Color.White)
                            }
                        }

                        if (isTv) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Navigation shortcut chips for TV
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusGroup()
                            ) {
                                val buttonModifier = Modifier.weight(1f).height(38.dp)
                                TvButton(onClick = onNavigateToLiveTv, modifier = buttonModifier) {
                                    TvText("LIVE TV", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToVod, modifier = buttonModifier) {
                                    TvText("FILMS VOD", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToSeries, modifier = buttonModifier) {
                                    TvText("SÉRIES", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToFavorites, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText("FAVS", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                                TvButton(onClick = onNavigateToSearch, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText("RECH", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                                TvButton(onClick = onNavigateToSettings, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText("PARAMS", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Section: "Continuer à regarder"
                if (state.resumeWatchingList.isNotEmpty()) {
                    item {
                        HomeSectionRow(title = "Continuer à regarder") {
                            LazyRow(
                                state = rememberForeverLazyListState("home_resume", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.resumeWatchingList) { position ->
                                    HomeResumeWatchingCard(
                                        position = position,
                                        onClick = {
                                            if (position.type == "series") {
                                                onPlayResumeWatchingSeries(position)
                                            } else {
                                                onPlayResumeWatchingMovie(position)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Section: "Favoris"
                if (state.favoritesList.isNotEmpty()) {
                    item {
                        HomeSectionRow(title = "Favoris") {
                            LazyRow(
                                state = rememberForeverLazyListState("home_favorites", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.favoritesList) { fav ->
                                    HomeFavoriteItemCard(
                                        favorite = fav,
                                        onClick = {
                                            when (fav.type) {
                                                "live" -> {
                                                    val stream = LiveStream(
                                                        streamId = fav.id,
                                                        name = fav.name,
                                                        streamIcon = fav.cover,
                                                        epgChannelId = null,
                                                        num = 1,
                                                        categoryId = fav.categoryId
                                                    )
                                                    onPlayLiveStream(stream, listOf(stream))
                                                }
                                                "movie" -> {
                                                    val stream = VodStream(
                                                        streamId = fav.id,
                                                        name = fav.name,
                                                        streamIcon = fav.cover,
                                                        rating = null,
                                                        added = null,
                                                        categoryId = fav.categoryId
                                                    )
                                                    onSelectMovieDetail(stream)
                                                }
                                                "series" -> {
                                                    val stream = SeriesStream(
                                                        seriesId = fav.id,
                                                        name = fav.name,
                                                        cover = fav.cover,
                                                        rating = null,
                                                        added = null,
                                                        categoryId = fav.categoryId
                                                    )
                                                    onSelectSeriesDetail(stream)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Section: "TV" (First Category Live Streams)
                if (state.firstLiveCategory != null && state.firstLiveStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = "TV : ${state.firstLiveCategory!!.categoryName}",
                            onSeeAll = onNavigateToLiveTv
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_livetv", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.firstLiveStreams) { stream ->
                                    HomeLiveTvCard(
                                        stream = stream,
                                        epgProgram = state.epgPrograms[stream.streamId],
                                        onLoadEpg = { viewModel.loadEpgForStream(stream.streamId) },
                                        onClick = { onPlayLiveStream(stream, state.firstLiveStreams) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Section: "Films" (First Category VOD Streams)
                if (state.firstVodCategory != null && state.firstVodStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = "Films : ${state.firstVodCategory!!.categoryName}",
                            onSeeAll = onNavigateToVod
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_vod", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.firstVodStreams) { stream ->
                                    HomeVodMovieCard(
                                        stream = stream,
                                        onClick = { onSelectMovieDetail(stream) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Section: "Séries" (First Category Series Streams)
                if (state.firstSeriesCategory != null && state.firstSeriesStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = "Séries : ${state.firstSeriesCategory!!.categoryName}",
                            onSeeAll = onNavigateToSeries
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_series", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.firstSeriesStreams) { stream ->
                                    HomeSeriesShowCard(
                                        stream = stream,
                                        onClick = { onSelectSeriesDetail(stream) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (onSeeAll != null) {
                Spacer(modifier = Modifier.width(16.dp))
                var isFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onSeeAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF1E1E24),
                        contentColor = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(
                            width = 1.dp,
                            color = if (isFocused) Color.White else Color.DarkGray,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Text(
                        text = "Voir tout",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun HomeResumeWatchingCard(
    position: PlaybackPosition,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (position.durationMs > 0) {
        position.positionMs.toFloat() / position.durationMs.toFloat()
    } else 0f

    Column(
        modifier = Modifier
            .width(220.dp)
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
            text = position.title ?: "Sans titre",
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
private fun HomeFavoriteItemCard(
    favorite: FavoriteItem,
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
                .height(195.dp)
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!favorite.cover.isNullOrBlank()) {
                AsyncImage(
                    model = favorite.cover,
                    contentDescription = favorite.name,
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

            // Type Badge (top left)
            val badgeColor = when (favorite.type) {
                "live" -> Color(0xFFE50914) // Red for live direct
                "movie" -> Color(0xFF0070F3) // Blue for movies
                "series" -> Color(0xFF8A2BE2) // Purple for series
                else -> Color.Gray
            }
            val badgeLabel = when (favorite.type) {
                "live" -> "DIRECT"
                "movie" -> "FILM"
                "series" -> "SÉRIE"
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
private fun HomeLiveTvCard(
    stream: LiveStream,
    epgProgram: com.poc.iptvxtream.domain.model.LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            kotlinx.coroutines.delay(60000)
        }
    }

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
                text = "Pas de programme",
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
private fun HomeEpgProgressBar(
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
private fun HomeVodMovieCard(
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
private fun HomeSeriesShowCard(
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
