package com.cstv.app.presentation.home
import com.cstv.app.R
import androidx.compose.ui.res.stringResource
import com.cstv.app.presentation.home.components.*

import com.cstv.app.presentation.rememberForeverLazyListState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.components.HistoryRemovalDialog
import androidx.compose.ui.text.font.FontFamily
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.DownloadedItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    userInfo: UserInfo,
    isTv: Boolean,
    viewModel: HomeViewModel,
    activeProfileAvatarId: Int,
    activeProfileName: String,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToVod: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToRecentlyAdded: (Boolean) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfileManagement: () -> Unit,
    onPlayResumeWatchingMovie: (PlaybackPosition) -> Unit,
    onPlayResumeWatchingSeries: (PlaybackPosition) -> Unit,
    onPlayLiveStream: (LiveStream, List<LiveStream>) -> Unit,
    onSelectMovieDetail: (VodStream) -> Unit,
    onSelectSeriesDetail: (SeriesStream) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onPlayDownloadedMovie: (DownloadedItem) -> Unit,
    onPlayDownloadedEpisode: (DownloadedItem) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displayedTopVodStreams = state.popularTopVodStreams ?: state.topVodStreams
    val displayedTopSeriesStreams = state.popularTopSeriesStreams ?: state.topSeriesStreams

    // Section affichée en grille verticale ("Voir tout"). null = accueil normal.
    var expandedSection by remember { mutableStateOf<HomeExpandedSection?>(null) }
    var pendingRemoval by remember { mutableStateOf<PlaybackPosition?>(null) }
    val historySnackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.historyRemovalError) {
        state.historyRemovalError?.let {
            historySnackbarHost.showSnackbar(it)
            viewModel.consumeHistoryRemovalError()
            pendingRemoval = null
        }
    }
    LaunchedEffect(state.resumeWatchingList, pendingRemoval) {
        pendingRemoval?.takeIf { pending -> state.resumeWatchingList.none { it.streamId == pending.streamId } }?.let { pendingRemoval = null }
    }
    LaunchedEffect(state.resumeWatchingList) {
        if (expandedSection == HomeExpandedSection.RESUME && state.resumeWatchingList.isEmpty()) {
            expandedSection = null
        }
    }

    // Refresh home data when entering screen
    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    // Clic sur un média "Continuer à regarder" (repris du bloc de la rangée).
    val handleResumeClick: (PlaybackPosition) -> Unit = { position ->
        if (position.type == "series") {
            onPlayResumeWatchingSeries(position)
        } else {
            onPlayResumeWatchingMovie(position)
        }
    }

    // Clic sur un favori : reconstruit le stream selon son type.
    val handleFavoriteClick: (FavoriteItem) -> Unit = { fav ->
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent)
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            expandedSection?.let { section ->
                HomeExpandedGrid(
                    section = section,
                    resumeList = state.resumeWatchingList,
                    favoritesList = state.favoritesList,
                    topVodStreams = displayedTopVodStreams,
                    recommendedMovies = state.recommendedMovies,
                    recommendedSeries = state.recommendedSeries,
                    onResumeClick = handleResumeClick,
                    onResumeLongClick = { pendingRemoval = it },
                    onFavoriteClick = handleFavoriteClick,
                    onMovieClick = onSelectMovieDetail,
                    onSeriesClick = onSelectSeriesDetail,
                    onBack = { expandedSection = null },
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
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
                            .padding(bottom = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.cstv.app.presentation.profile.ProfileAvatar(
                                avatarId = activeProfileAvatarId,
                                name = activeProfileName,
                                size = if (isTv) 54 else 36,
                                modifier = Modifier
                                    .clickable { onNavigateToProfileManagement() }
                            )
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                if (!isTv) {
                                    Text(
                                        text = "Bonsoir",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentLavande,
                                        letterSpacing = 0.04.sp,
                                        fontFamily = HankenGrotesk
                                    )
                                    Text(
                                        text = activeProfileName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF4F4F7),
                                        letterSpacing = (-0.01).sp,
                                        fontFamily = BricolageGrotesque
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        modifier = Modifier.padding(top = 1.dp)
                                    ) {
                                        Text(
                                            text = userInfo.username.uppercase(),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF7A7A86),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .background(Color(0xFF4A4A54), shape = RoundedCornerShape(1.5.dp))
                                        )
                                        Text(
                                            text = "Exp. ${userInfo.expiryDate}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF7A7A86),
                                            fontFamily = HankenGrotesk
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.home_welcome, userInfo.username.uppercase()),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.home_expiration_connections, userInfo.expiryDate, userInfo.maxConnections),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Settings Button
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Surface3, shape = RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp))
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.home_see_all),
                                    tint = Color(0xFFC7C7D1),
                                    modifier = Modifier.size(19.dp)
                                )
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
                                    TvText(stringResource(R.string.home_nav_live_tv), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToVod, modifier = buttonModifier) {
                                    TvText(stringResource(R.string.home_nav_vod), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToSeries, modifier = buttonModifier) {
                                    TvText(stringResource(R.string.home_nav_series), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                }
                                TvButton(onClick = onNavigateToFavorites, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText(stringResource(R.string.home_nav_favs), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                                TvButton(onClick = onNavigateToSearch, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText(stringResource(R.string.home_nav_search), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                                TvButton(onClick = onNavigateToSettings, modifier = buttonModifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TvText(stringResource(R.string.home_nav_settings), fontWeight = FontWeight.Bold, style = TvTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // NOUVEAU: Hero "Reprendre" ou "Tendances" (Phase F1)
                if (!isTv) {
                    if (state.trendingList.isNotEmpty()) {
                        item {
                            HomeTrendingCarousel(
                                trendingItems = state.trendingList,
                                trailerPreview = state.trailerPreview,
                                onActiveItemChanged = viewModel::selectTrendingPreview,
                                onPreviewContextEnded = viewModel::cancelTrendingPreview,
                                onPreviewPlaybackFailed = viewModel::reportTrailerPlaybackFailure,
                                onMovieClick = { streamId ->
                                    state.trendingList.find { it.matchedMovie?.streamId == streamId }?.matchedMovie?.let {
                                        onSelectMovieDetail(it)
                                    }
                                },
                                onSeriesClick = { seriesId ->
                                    state.trendingList.find { it.matchedSeries?.seriesId == seriesId }?.matchedSeries?.let {
                                        onSelectSeriesDetail(it)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    } else if (state.resumeWatchingList.isNotEmpty()) {
                        item {
                            HomeHeroCard(
                                position = state.resumeWatchingList.first(),
                                onClick = { handleResumeClick(state.resumeWatchingList.first()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                } else if (state.trendingList.isNotEmpty()) {
                    // TV : rangée poster focusable au D-pad (le pager mobile n'est
                    // pas navigable au D-pad). Repli implicite sur les autres
                    // sections si la liste est vide (pas de hero sur TV).
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_trending),
                            isTv = isTv,
                            onSeeAll = null
                        ) {
                            HomeTrendingRowTv(
                                trendingItems = state.trendingList,
                                onMovieClick = { streamId ->
                                    state.trendingList.find { it.matchedMovie?.streamId == streamId }?.matchedMovie?.let {
                                        onSelectMovieDetail(it)
                                    }
                                },
                                onSeriesClick = { seriesId ->
                                    state.trendingList.find { it.matchedSeries?.seriesId == seriesId }?.matchedSeries?.let {
                                        onSelectSeriesDetail(it)
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. Section: "Continuer à regarder"
                if (state.resumeWatchingList.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_resume),
                            isTv = isTv,
                            onSeeAll = { expandedSection = HomeExpandedSection.RESUME }
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_resume", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.resumeWatchingList) { position ->
                                    HomeResumeWatchingCard(
                                        position = position,
                                        onClick = { handleResumeClick(position) },
                                        onLongClick = { pendingRemoval = position },
                                        isTv = isTv
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Section: "Favoris"
                if (state.favoritesList.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_favorites),
                            isTv = isTv,
                            onSeeAll = { expandedSection = HomeExpandedSection.FAVORITES }
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_favorites", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.favoritesList) { fav ->
                                    HomeFavoriteItemCard(
                                        favorite = fav,
                                        onClick = { handleFavoriteClick(fav) }
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
                            title = stringResource(R.string.home_section_tv),
                            isTv = isTv,
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
                                        onClick = { onPlayLiveStream(stream, state.firstLiveStreams) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Section: "Films" (Latest additions VOD Streams)
                if (state.firstVodStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_section_vod),
                            isTv = isTv,
                            onSeeAll = { onNavigateToRecentlyAdded(false) }
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

                // 6. Section: Top 10 Films (Trending/Top Rated)
                if (displayedTopVodStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_top_movies),
                            isTv = isTv,
                            onSeeAll = { expandedSection = HomeExpandedSection.TOP_MOVIES }
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_top_movies", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                itemsIndexed(displayedTopVodStreams) { index, stream ->
                                    HomeVodMovieCard(
                                        stream = stream,
                                        onClick = { onSelectMovieDetail(stream) },
                                        rank = index + 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Section F-6: "Films recommandés pour vous"
                if (state.recommendedMovies.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_recommended_movies),
                            isTv = isTv,
                            onSeeAll = { expandedSection = HomeExpandedSection.RECOMMENDED_MOVIES }
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_reco_movies", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.recommendedMovies) { stream ->
                                    HomeVodMovieCard(
                                        stream = stream,
                                        onClick = { onSelectMovieDetail(stream) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Section: "Séries" (Latest additions Series Streams)
                if (state.firstSeriesStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_section_series),
                            isTv = isTv,
                            onSeeAll = { onNavigateToRecentlyAdded(true) }
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

                // 9. Section: Top 10 Séries
                if (displayedTopSeriesStreams.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_top_series),
                            isTv = isTv,
                            onSeeAll = null
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_top_series", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                itemsIndexed(displayedTopSeriesStreams) { index, stream ->
                                    HomeSeriesShowCard(
                                        stream = stream,
                                        onClick = { onSelectSeriesDetail(stream) },
                                        rank = index + 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 10. Section F-6: "Séries recommandées pour vous"
                if (state.recommendedSeries.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_recommended_series),
                            isTv = isTv,
                            onSeeAll = { expandedSection = HomeExpandedSection.RECOMMENDED_SERIES }
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_reco_series", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.recommendedSeries) { stream ->
                                    HomeSeriesShowCard(
                                        stream = stream,
                                        onClick = { onSelectSeriesDetail(stream) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 11. F15: les téléchargements terminés restent un raccourci de lecture,
                // leur gestion complète est conservée dans DownloadsScreen.
                if (state.downloadedItems.isNotEmpty()) {
                    item {
                        HomeSectionRow(
                            title = stringResource(R.string.home_section_downloads),
                            isTv = isTv,
                            onSeeAll = onNavigateToDownloads
                        ) {
                            LazyRow(
                                state = rememberForeverLazyListState("home_downloads", { viewModel.getScrollPosition(it) }, { k, i, o -> viewModel.saveScrollPosition(k, i, o) }),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().focusGroup()
                            ) {
                                items(state.downloadedItems, key = { it.contentId }) { item ->
                                    HomeDownloadCard(
                                        item = item,
                                        isTv = isTv,
                                        onClick = {
                                            if (item.type == DownloadedItem.TYPE_MOVIE) {
                                                onPlayDownloadedMovie(item)
                                            } else {
                                                onPlayDownloadedEpisode(item)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        SnackbarHost(hostState = historySnackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
        pendingRemoval?.let { position ->
            HistoryRemovalDialog(
                contentName = position.title.orEmpty(),
                isTv = isTv,
                isRemoving = state.isRemovingHistory,
                onConfirm = { viewModel.removeFromContinueWatching(position) },
                onDismiss = { if (!state.isRemovingHistory) pendingRemoval = null }
            )
        }
    }
}

// Section de l'accueil affichable en grille verticale via "Voir tout".
private enum class HomeExpandedSection { RESUME, FAVORITES, TOP_MOVIES, RECOMMENDED_MOVIES, RECOMMENDED_SERIES }

@Composable
private fun HomeExpandedGrid(
    section: HomeExpandedSection,
    resumeList: List<PlaybackPosition>,
    favoritesList: List<FavoriteItem>,
    topVodStreams: List<VodStream>,
    recommendedMovies: List<VodStream>,
    recommendedSeries: List<SeriesStream>,
    onResumeClick: (PlaybackPosition) -> Unit,
    onResumeLongClick: (PlaybackPosition) -> Unit,
    onFavoriteClick: (FavoriteItem) -> Unit,
    onMovieClick: (VodStream) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (section) {
        HomeExpandedSection.RESUME -> stringResource(R.string.home_resume)
        HomeExpandedSection.FAVORITES -> stringResource(R.string.home_favorites)
        HomeExpandedSection.TOP_MOVIES -> stringResource(R.string.home_top_movies)
        HomeExpandedSection.RECOMMENDED_MOVIES -> stringResource(R.string.home_recommended_movies)
        HomeExpandedSection.RECOMMENDED_SERIES -> stringResource(R.string.home_recommended_series)
    }
    val count = when (section) {
        HomeExpandedSection.RESUME -> resumeList.size
        HomeExpandedSection.FAVORITES -> favoritesList.size
        HomeExpandedSection.TOP_MOVIES -> topVodStreams.size
        HomeExpandedSection.RECOMMENDED_MOVIES -> recommendedMovies.size
        HomeExpandedSection.RECOMMENDED_SERIES -> recommendedSeries.size
    }
    // "Continuer à regarder" = vignettes paysage -> 2 colonnes ;
    // Les autres = affiches -> 3 colonnes.
    val columns = if (section == HomeExpandedSection.RESUME) 2 else 3

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "$title ($count)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (section) {
                HomeExpandedSection.RESUME -> gridItems(resumeList) { position ->
                    HomeResumeWatchingCard(
                        position = position,
                        onClick = { onResumeClick(position) },
                        onLongClick = { onResumeLongClick(position) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HomeExpandedSection.FAVORITES -> gridItems(favoritesList) { fav ->
                    HomeFavoriteItemCard(
                        favorite = fav,
                        onClick = { onFavoriteClick(fav) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HomeExpandedSection.TOP_MOVIES -> gridItemsIndexed(topVodStreams) { index, stream ->
                    HomeVodMovieCard(
                        stream = stream,
                        onClick = { onMovieClick(stream) },
                        rank = index + 1
                    )
                }
                HomeExpandedSection.RECOMMENDED_MOVIES -> gridItems(recommendedMovies) { stream ->
                    HomeVodMovieCard(
                        stream = stream,
                        onClick = { onMovieClick(stream) }
                    )
                }
                HomeExpandedSection.RECOMMENDED_SERIES -> gridItems(recommendedSeries) { stream ->
                    HomeSeriesShowCard(
                        stream = stream,
                        onClick = { onSeriesClick(stream) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    title: String,
    isTv: Boolean,
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
                fontFamily = BricolageGrotesque,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).sp,
                color = Color(0xFFF4F4F7)
            )
            if (onSeeAll != null) {
                Spacer(modifier = Modifier.weight(1f))
                if (isTv) {
                    // TV : bouton focusable au D-pad (contraste WCAG AA dans les deux états).
                    var isFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onSeeAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFocused) AccentLavande else Surface3,
                            contentColor = if (isFocused) Color.Black else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                    ) {
                        Text(
                            text = stringResource(R.string.home_see_all),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Mobile (Phase 55) : lien texte discret en accent, façon maquette.
                    Text(
                        text = stringResource(R.string.home_see_all),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentLavande,
                        fontFamily = HankenGrotesk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSeeAll() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        content()
    }
}
