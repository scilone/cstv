package com.poc.iptvxtream.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.presentation.favorites.FavoritesViewModel

// Type de média dont on affiche la liste complète (grille verticale) après
// un clic sur "Voir tout". null = vue combinée (rangées horizontales).
private enum class SearchExpandedType { LIVE, VOD, SERIES }

@Composable
fun SearchScreen(
    viewModel: FavoritesViewModel,
    isTv: Boolean,
    onPlayLive: (LiveStream) -> Unit,
    onSelectMovie: (VodStream) -> Unit,
    onSelectSeries: (SeriesStream) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Vue "Voir tout" : on ne montre qu'un type de média en grille verticale.
    var expandedType by remember { mutableStateOf<SearchExpandedType?>(null) }

    // Toute nouvelle saisie recasse la vue développée pour revenir aux rangées.
    LaunchedEffect(state.searchQuery) { expandedType = null }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header & Search Field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = { if (expandedType != null) expandedType = null else onBack() },
                    modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Rechercher des chaînes, films, séries...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.searchQuery.trim().isBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Saisissez un mot-clé pour lancer la recherche locale.", color = Color.Gray, fontSize = 14.sp)
                }
            } else if (state.isSearching) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.searchResult.isEmpty) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucun résultat trouvé pour « ${state.searchQuery} ».", color = Color.Gray, fontSize = 14.sp)
                }
            } else if (expandedType != null) {
                // --- Vue développée : un seul type, grille verticale ---
                SearchExpandedGrid(
                    type = expandedType!!,
                    result = state.searchResult,
                    onPlayLive = onPlayLive,
                    onSelectMovie = onSelectMovie,
                    onSelectSeries = onSelectSeries,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                // --- Vue combinée : rangées horizontales par type ---
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    // 1. Live TV Results Row
                    if (state.searchResult.liveResults.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SearchSectionHeader(
                                    title = "Chaînes en direct",
                                    count = state.searchResult.liveResults.size,
                                    onSeeAll = { expandedType = SearchExpandedType.LIVE }
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    items(state.searchResult.liveResults) { stream ->
                                        SearchCardItem(
                                            name = stream.name,
                                            cover = stream.streamIcon,
                                            isLive = true,
                                            onClick = { onPlayLive(stream) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. VOD Movie Results Row
                    if (state.searchResult.vodResults.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SearchSectionHeader(
                                    title = "Films VOD",
                                    count = state.searchResult.vodResults.size,
                                    onSeeAll = { expandedType = SearchExpandedType.VOD }
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    items(state.searchResult.vodResults) { stream ->
                                        SearchCardItem(
                                            name = stream.name,
                                            cover = stream.streamIcon,
                                            isLive = false,
                                            onClick = { onSelectMovie(stream) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Series Results Row
                    if (state.searchResult.seriesResults.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SearchSectionHeader(
                                    title = "Séries",
                                    count = state.searchResult.seriesResults.size,
                                    onSeeAll = { expandedType = SearchExpandedType.SERIES }
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    items(state.searchResult.seriesResults) { stream ->
                                        SearchCardItem(
                                            name = stream.name,
                                            cover = stream.cover,
                                            isLive = false,
                                            onClick = { onSelectSeries(stream) }
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
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    onSeeAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(16.dp))
        var isFocused by remember { mutableStateOf(false) }
        Button(
            onClick = onSeeAll,
            // Même contraste que la Home : repos = blanc sur #1E1E24,
            // focus = noir sur Purple80 #D0BCFF.
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFocused) Color(0xFFD0BCFF) else Color(0xFF1E1E24),
                contentColor = if (isFocused) Color.Black else Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
                .height(28.dp)
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Text(
                text = "Voir tout ($count)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchExpandedGrid(
    type: SearchExpandedType,
    result: com.poc.iptvxtream.domain.model.SearchResult,
    onPlayLive: (LiveStream) -> Unit,
    onSelectMovie: (VodStream) -> Unit,
    onSelectSeries: (SeriesStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (type) {
        SearchExpandedType.LIVE -> "Chaînes en direct"
        SearchExpandedType.VOD -> "Films VOD"
        SearchExpandedType.SERIES -> "Séries"
    }
    val count = when (type) {
        SearchExpandedType.LIVE -> result.liveResults.size
        SearchExpandedType.VOD -> result.vodResults.size
        SearchExpandedType.SERIES -> result.seriesResults.size
    }
    // Chaînes = vignette paysage -> 2 colonnes ; posters -> 3 colonnes.
    val columns = if (type == SearchExpandedType.LIVE) 2 else 3

    Column(modifier = modifier) {
        Text(
            text = "$title ($count)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (type) {
                SearchExpandedType.LIVE -> items(result.liveResults) { stream ->
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.streamIcon,
                        isLive = true,
                        onClick = { onPlayLive(stream) }
                    )
                }
                SearchExpandedType.VOD -> items(result.vodResults) { stream ->
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.streamIcon,
                        isLive = false,
                        onClick = { onSelectMovie(stream) }
                    )
                }
                SearchExpandedType.SERIES -> items(result.seriesResults) { stream ->
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.cover,
                        isLive = false,
                        onClick = { onSelectSeries(stream) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchGridCard(
    name: String,
    cover: String?,
    isLive: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                .then(
                    if (isLive) Modifier.aspectRatio(16f / 9f) else Modifier.aspectRatio(2f / 3f)
                )
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = name,
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

        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
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
private fun SearchCardItem(
    name: String,
    cover: String?,
    isLive: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

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
            .background(Color(0xFF1E1E24))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLive) Modifier.height(80.dp) else Modifier.aspectRatio(2f / 3f)
                )
                .background(Color(0xFF0F0F13)),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = name,
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

        Text(
            text = name,
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
