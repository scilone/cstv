package com.poc.iptvxtream.presentation.favorites
import com.poc.iptvxtream.R
import androidx.compose.ui.res.stringResource

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
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.WhiteOverlay20

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
                    modifier = Modifier.background(WhiteOverlay20, shape = RoundedCornerShape(12.dp))
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Live TV Favorites row
                    if (liveFavorites.isNotEmpty()) {
                        item {
                            FavoritesCategoryRow(
                                title = stringResource(R.string.favorites_category_live),
                                itemsList = liveFavorites,
                                isTv = isTv,
                                onClick = { onPlayLive(it.id, it.categoryId) }
                            )
                        }
                    }

                    // 2. VOD / Movies Favorites row
                    if (movieFavorites.isNotEmpty()) {
                        item {
                            FavoritesCategoryRow(
                                title = stringResource(R.string.favorites_category_vod),
                                itemsList = movieFavorites,
                                isTv = isTv,
                                onClick = { onSelectMovie(it.id, it.categoryId) }
                            )
                        }
                    }

                    // 3. Series Favorites row
                    if (seriesFavorites.isNotEmpty()) {
                        item {
                            FavoritesCategoryRow(
                                title = stringResource(R.string.favorites_category_series),
                                itemsList = seriesFavorites,
                                isTv = isTv,
                                onClick = { onSelectSeries(it.id, it.categoryId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesCategoryRow(
    title: String,
    itemsList: List<FavoriteItem>,
    isTv: Boolean,
    onClick: (FavoriteItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(itemsList) { item ->
                FavoriteCardItem(item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun FavoriteCardItem(
    item: FavoriteItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLive = item.type == "live"

    Column(
        modifier = Modifier
            .width(if (isLive) 120.dp else 110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
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
