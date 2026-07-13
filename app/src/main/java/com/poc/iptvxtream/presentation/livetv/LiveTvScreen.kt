package com.poc.iptvxtream.presentation.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveStream
import kotlinx.coroutines.delay

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel,
    favoritesList: List<FavoriteItem>,
    isTv: Boolean,
    onStreamSelected: (LiveStream, List<LiveStream>) -> Unit,
    onToggleFavorite: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    
    // Reset search query when the selected category changes
    LaunchedEffect(state.selectedCategory) {
        searchQuery = ""
    }

    val selectedCategory = state.selectedCategory
    val selectedCategoryName = selectedCategory?.categoryName ?: ""
    val isSpecificCategory = selectedCategory != null && 
            selectedCategory.categoryId != "0" && 
            selectedCategory.categoryId != "all" && 
            !selectedCategoryName.equals("Tout", ignoreCase = true) && 
            !selectedCategoryName.equals("All", ignoreCase = true)

    val filteredStreams = remember(state.streams, searchQuery) {
        if (searchQuery.isBlank()) {
            state.streams
        } else {
            state.streams.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Refresh recently watched when returning from the player (the ViewModel outlives this screen)
    LaunchedEffect(Unit) {
        viewModel.loadRecentlyWatched()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
    ) {
        if (isTv) {
            TvLayout(
                state = state,
                onCategorySelected = { viewModel.selectCategory(it) },
                onStreamSelected = { stream -> onStreamSelected(stream, filteredStreams) },
                onRefresh = { viewModel.loadCategories(forceRefresh = true) },
                favoritesList = favoritesList,
                onToggleFavorite = onToggleFavorite,
                filteredStreams = filteredStreams,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                epgPrograms = state.epgPrograms,
                onLoadEpg = { viewModel.loadEpgForStream(it) }
            )
        } else {
            MobileLayout(
                state = state,
                onCategorySelected = { viewModel.selectCategory(it) },
                onStreamSelected = { stream -> onStreamSelected(stream, filteredStreams) },
                onRefresh = { viewModel.loadCategories(forceRefresh = true) },
                favoritesList = favoritesList,
                onToggleFavorite = onToggleFavorite,
                filteredStreams = filteredStreams,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                epgPrograms = state.epgPrograms,
                onLoadEpg = { viewModel.loadEpgForStream(it) }
            )
        }
    }
}

@Composable
private fun TvLayout(
    state: LiveTvState,
    onCategorySelected: (LiveCategory) -> Unit,
    onStreamSelected: (LiveStream) -> Unit,
    onRefresh: () -> Unit,
    favoritesList: List<FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    filteredStreams: List<LiveStream>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    val groupedStreams = remember(filteredStreams) {
        filteredStreams.groupBy { it.categoryId }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top categories filter row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).focusGroup()
            ) {
                items(state.categories) { category ->
                    val isSelected = state.selectedCategory?.categoryId == category.categoryId
                    CategoryFilterChip(
                        category = category,
                        isSelected = isSelected,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = Color.White)
            }
        }

        if (state.isLoadingStreams || state.isLoadingCategories) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (isAllSelected) {
            // Mode "Tout" : vertical categories list of horizontal rows
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Récemment regardées (if not empty)
                if (state.recentlyWatched.isNotEmpty()) {
                    item {
                        RecentlyWatchedRow(
                            streams = state.recentlyWatched,
                            onStreamSelected = onStreamSelected,
                            isTv = true,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg
                        )
                    }
                }

                items(actualCategories) { category ->
                    val catStreams = groupedStreams[category.categoryId] ?: emptyList()
                    if (catStreams.isNotEmpty()) {
                        CategorySectionRow(
                            title = category.categoryName,
                            streams = catStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = true,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg
                        )
                    }
                }
            }
        } else {
            // Mode "Catégorie spécifique" : Search & Vertical Grid
            Text(
                text = state.selectedCategory?.categoryName?.uppercase() ?: "DIRECT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isSpecificCategory) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Rechercher une chaîne...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            if (filteredStreams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "Aucune chaîne dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusGroup()
                ) {
                    items(filteredStreams) { stream ->
                        val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                        StreamTvCard(
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
}

@Composable
private fun MobileLayout(
    state: LiveTvState,
    onCategorySelected: (LiveCategory) -> Unit,
    onStreamSelected: (LiveStream) -> Unit,
    onRefresh: () -> Unit,
    favoritesList: List<FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    filteredStreams: List<LiveStream>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    val groupedStreams = remember(filteredStreams) {
        filteredStreams.groupBy { it.categoryId }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top categories row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16161D))
                .padding(vertical = 4.dp, horizontal = 12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.categories) { category ->
                    val isSelected = state.selectedCategory?.categoryId == category.categoryId
                    CategoryFilterChip(
                        category = category,
                        isSelected = isSelected,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = Color.White)
            }
        }

        if (state.isLoadingStreams || state.isLoadingCategories) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (isAllSelected) {
            // Mode "Tout" : list of horizontal streams sections
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Récemment regardées
                if (state.recentlyWatched.isNotEmpty()) {
                    item {
                        RecentlyWatchedRow(
                            streams = state.recentlyWatched,
                            onStreamSelected = onStreamSelected,
                            isTv = false,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg
                        )
                    }
                }

                items(actualCategories) { category ->
                    val catStreams = groupedStreams[category.categoryId] ?: emptyList()
                    if (catStreams.isNotEmpty()) {
                        CategorySectionRow(
                            title = category.categoryName,
                            streams = catStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = false,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg
                        )
                    }
                }
            }
        } else {
            // Mode "Catégorie spécifique" : Search & Vertical List
            if (isSpecificCategory) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Rechercher une chaîne...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (filteredStreams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "Aucune chaîne dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStreams) { stream ->
                        val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                        val epgProgram = epgPrograms[stream.streamId]

                        LaunchedEffect(stream.streamId) {
                            while (true) {
                                onLoadEpg(stream.streamId)
                                delay(60000)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStreamSelected(stream) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
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
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CH ${stream.num}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stream.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (epgProgram != null) {
                                        Text(
                                            text = epgProgram.title,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
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
                                    }
                                }

                                IconButton(onClick = { onToggleFavorite(stream) }) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favori",
                                        tint = if (isFav) Color.Yellow else Color.DarkGray
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionRow(
    title: String,
    streams: List<LiveStream>,
    favoritesList: List<FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit
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

        LazyRow(
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
private fun MobileStreamCard(
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
private fun CategoryFilterChip(
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
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EpgProgressBar(
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
private fun RecentlyWatchedRow(
    streams: List<LiveStream>,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit
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

        LazyRow(
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
private fun RecentlyWatchedTvItem(
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
                }
            }
        }
    }
}

@Composable
private fun StreamTvCard(
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
