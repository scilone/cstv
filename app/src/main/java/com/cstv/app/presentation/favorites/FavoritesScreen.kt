package com.cstv.app.presentation.favorites
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cstv.app.presentation.components.tvFocusHighlight
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.cstv.app.presentation.components.tvLongPressActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.cstv.app.presentation.components.tvPivotItem
import com.cstv.app.presentation.components.rememberTvRowFocusEntry
import com.cstv.app.presentation.components.tvRowFocusEntry
import com.cstv.app.presentation.components.tvRowFocusEntryTarget
import com.cstv.app.presentation.components.tvPivotSection
import com.cstv.app.presentation.components.tvPivotHorizontalEndSpacer
import com.cstv.app.presentation.components.tvPivotVerticalEndSpacer
import com.cstv.app.presentation.components.tvPivotVerticalStartReserve
import com.cstv.app.presentation.components.LocalTvPivotViewport
import com.cstv.app.presentation.components.rememberTvPivotViewport
import com.cstv.app.presentation.components.tvPivotViewport
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    isTv: Boolean,
    onPlayLive: (Int, String) -> Unit, // (streamId, categoryId) -> Play Live stream directly
    onSelectMovie: (Int, String) -> Unit, // (streamId, categoryId) -> Show Movie details
    onSelectSeries: (Int, String) -> Unit, // (seriesId, categoryId) -> Show Series details
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Phase 41 : state.favorites vient d'un Flow Room observé en continu
    // (FavoritesViewModel.init), plus besoin de reload manuel à l'entrée écran.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playbackSnackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val favoriteRemovedMessage = stringResource(R.string.catalog_favorite_removed)
    // Cet écran ne liste que des favoris : l'appui long n'y retire donc que
    // des favoris, il n'en ajoute jamais — au contraire des listes catalogue
    // (retour PO, hotfix).
    val removeFavorite: (FavoriteItem) -> Unit = { item ->
        viewModel.toggleFavorite(item.id, item.type, item.name, item.cover, item.categoryId)
        snackbarScope.launch {
            playbackSnackbarHostState.currentSnackbarData?.dismiss()
            playbackSnackbarHostState.showSnackbar(favoriteRemovedMessage)
        }
    }

    LaunchedEffect(state.playbackError) {
        state.playbackError?.let { message ->
            playbackSnackbarHostState.showSnackbar(message)
            viewModel.consumePlaybackError()
        }
    }

    val liveFavorites = remember(state.favorites) { state.favorites.filter { it.type == "live" } }
    val movieFavorites = remember(state.favorites) { state.favorites.filter { it.type == "movie" } }
    val seriesFavorites = remember(state.favorites) { state.favorites.filter { it.type == "series" } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface1)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.favorites_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
            }

            if (state.favorites.isEmpty() && !state.isLoadingFavorites) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.favorites_empty), color = Color.Gray, fontSize = 15.sp)
                }
            } else {
                val listState = rememberLazyListState()
                // Conteneur du pivot : il donne aux rangées leur distance exacte
                // jusqu'à l'ancre, dans les deux sens, plutôt qu'une foulée
                // estimée sur la hauteur de la rangée de départ (B22).
                val pivotViewport = rememberTvPivotViewport()
                CompositionLocalProvider(LocalTvPivotViewport provides pivotViewport) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                        .tvPivotViewport(pivotViewport)
                ) {
                    tvPivotVerticalStartReserve(isTv)
                    // 1. Live TV Favorites row
                    if (liveFavorites.isNotEmpty()) {
                        item(key = "favorites_live") {
                            FavoritesCategoryRow(
                                categoryKey = "favorites_live",
                                title = stringResource(R.string.favorites_category_live),
                                itemsList = liveFavorites,
                                isTv = isTv,
                                onClick = { onPlayLive(it.id, it.categoryId) },
                                onLongClick = removeFavorite,
                                sectionListState = listState
                            )
                        }
                    }

                    // 2. VOD / Movies Favorites row
                    if (movieFavorites.isNotEmpty()) {
                        item(key = "favorites_vod") {
                            FavoritesCategoryRow(
                                categoryKey = "favorites_vod",
                                title = stringResource(R.string.favorites_category_vod),
                                itemsList = movieFavorites,
                                isTv = isTv,
                                onClick = { onSelectMovie(it.id, it.categoryId) },
                                onLongClick = removeFavorite,
                                sectionListState = listState
                            )
                        }
                    }

                    // 3. Series Favorites row
                    if (seriesFavorites.isNotEmpty()) {
                        item(key = "favorites_series") {
                            FavoritesCategoryRow(
                                categoryKey = "favorites_series",
                                title = stringResource(R.string.favorites_category_series),
                                itemsList = seriesFavorites,
                                isTv = isTv,
                                onClick = { onSelectSeries(it.id, it.categoryId) },
                                onLongClick = removeFavorite,
                                sectionListState = listState
                            )
                        }
                    }
                    tvPivotVerticalEndSpacer(isTv)
                }
                }
            }
        }
        SnackbarHost(
            hostState = playbackSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun FavoritesCategoryRow(
    categoryKey: String,
    title: String,
    itemsList: List<FavoriteItem>,
    isTv: Boolean,
    onClick: (FavoriteItem) -> Unit,
    onLongClick: (FavoriteItem) -> Unit,
    sectionListState: LazyListState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvPivotSection(isTv, sectionListState, categoryKey)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )

        val rowState = rememberLazyListState()
        val rowEntry = rememberTvRowFocusEntry()
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().tvRowFocusEntry(isTv, rowEntry).focusGroup()
        ) {
            itemsIndexed(itemsList) { index, item ->
                Box(modifier = Modifier.tvPivotItem(isTv, rowState, index)
                    .tvRowFocusEntryTarget(isTv, rowEntry, rowState, index)) {
                    FavoriteCardItem(
                        item = item,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                        isTv = isTv
                    )
                }
            }
            tvPivotHorizontalEndSpacer(isTv)
        }
    }
}

@Composable
private fun FavoriteCardItem(
    item: FavoriteItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isTv: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLive = item.type == "live"
    val longClickLabel = stringResource(R.string.catalog_favorite_toggle_label)

    Column(
        modifier = Modifier
            .width(if (isLive) 120.dp else 110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .tvLongPressActions(isTv, onClick, onLongClick, longClickLabel)
            .background(Surface3)
    ) {
        // Thumbnail Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLive) Modifier.height(80.dp) else Modifier.aspectRatio(2f / 3f)
                )
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!item.cover.isNullOrBlank()) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.name,
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            // F39 : jamais pour une chaîne (item.versionLabel reste nul sur la
            // branche `live`, hors périmètre F39).
            val versionLabel = item.versionLabel
            if (versionLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(versionLabel, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Title
        Text(
            text = item.name,
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
