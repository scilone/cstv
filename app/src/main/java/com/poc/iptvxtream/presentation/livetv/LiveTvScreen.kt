package com.poc.iptvxtream.presentation.livetv
import com.poc.iptvxtream.presentation.livetv.components.*

import com.poc.iptvxtream.presentation.rememberForeverLazyListState
import com.poc.iptvxtream.presentation.rememberForeverLazyGridState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.BricolageGrotesque
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface2
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.TextSecondary
import com.poc.iptvxtream.presentation.components.CategorySelectorTrigger
import com.poc.iptvxtream.presentation.components.CategorySearchField
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
    val state by viewModel.state.collectAsStateWithLifecycle()

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

    val getScroll: (String) -> Pair<Int, Int> = { viewModel.getScrollPosition(it) }
    val saveScroll: (String, Int, Int) -> Unit = { k, i, o -> viewModel.saveScrollPosition(k, i, o) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent)
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
                onLoadEpg = { viewModel.loadEpgForStream(it) },
                getScroll = getScroll,
                saveScroll = saveScroll
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
                onLoadEpg = { viewModel.loadEpgForStream(it) },
                getScroll = getScroll,
                saveScroll = saveScroll
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
    onLoadEpg: (Int) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    val groupedStreams = remember(filteredStreams) {
        filteredStreams.groupBy { it.categoryId }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Chaînes favorites (Phase 35), section dédiée du mode "Tout", sous
    // "Récemment regardées" — comme sur Films/Séries.
    val favoriteStreams = remember(filteredStreams, favoritesList) {
        val favoriteLiveIds = favoritesList.filter { it.type == "live" }.map { it.id }.toSet()
        filteredStreams.filter { it.streamId in favoriteLiveIds }
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
            val listState = rememberForeverLazyListState("livetv_tv_all_vertical", getScroll, saveScroll)
            LazyColumn(
                state = listState,
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
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }

                // Section 2: Favoris (Phase 35), sous "Récemment regardées"
                if (favoriteStreams.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            streams = favoriteStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = true,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }

                items(actualCategories) { category ->
                    val catStreams = groupedStreams[category.categoryId] ?: emptyList()
                    if (catStreams.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            streams = catStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = true,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll
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
                val gridState = rememberForeverLazyGridState("livetv_tv_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                LazyVerticalGrid(
                    state = gridState,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onLoadEpg: (Int) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    val groupedStreams = remember(filteredStreams) {
        filteredStreams.groupBy { it.categoryId }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Chaînes favorites (Phase 35), section dédiée du mode "Tout", sous
    // "Récemment regardées" — comme sur Films/Séries.
    val favoriteStreams = remember(filteredStreams, favoritesList) {
        val favoriteLiveIds = favoritesList.filter { it.type == "live" }.map { it.id }.toSet()
        filteredStreams.filter { it.streamId in favoriteLiveIds }
    }

    var showCategorySheet by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(state.categories, categorySearchQuery) {
        if (categorySearchQuery.isBlank()) {
            state.categories
        } else {
            state.categories.filter { it.categoryName.contains(categorySearchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sélecteur de catégorie unifié (Phase 56) : pleine largeur, sans bouton
        // "Rafraîchir" (rafraîchissement manuel déplacé dans les Paramètres),
        // espacé du haut, fond neutre transparent comme le reste du layout.
        CategorySelectorTrigger(
            label = state.selectedCategory?.categoryName ?: "Tout",
            onClick = { showCategorySheet = true },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        )

        // Category Modal Bottom Sheet (Opt-In Material 3)
        if (showCategorySheet) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showCategorySheet = false
                    categorySearchQuery = ""
                },
                containerColor = Surface1,
                contentColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Catégories",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = categorySearchQuery,
                        onValueChange = { categorySearchQuery = it },
                        placeholder = { Text("Rechercher une catégorie...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentLavande, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentLavande,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(filteredCategories) { category ->
                            val isSelected = state.selectedCategory?.categoryId == category.categoryId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCategorySelected(category)
                                        showCategorySheet = false
                                        categorySearchQuery = ""
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = category.categoryName,
                                    fontFamily = HankenGrotesk,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (isSelected) AccentLavande else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sélectionné",
                                        tint = AccentLavande,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        if (state.isLoadingStreams || state.isLoadingCategories) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (isAllSelected) {
            // Mode "Tout" : list of horizontal streams sections
            val listState = rememberForeverLazyListState("livetv_mobile_all_vertical", getScroll, saveScroll)
            LazyColumn(
                state = listState,
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
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }

                // Favoris (Phase 35), sous "Récemment regardées"
                if (favoriteStreams.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            streams = favoriteStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = false,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }

                items(actualCategories) { category ->
                    val catStreams = groupedStreams[category.categoryId] ?: emptyList()
                    if (catStreams.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            streams = catStreams,
                            favoritesList = favoritesList,
                            onToggleFavorite = onToggleFavorite,
                            onStreamSelected = onStreamSelected,
                            isTv = false,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            onSeeAll = { onCategorySelected(category) }
                        )
                    }
                }
            }
        } else {
            // Mode "Catégorie spécifique" : recherche + grille verticale 2 colonnes (Phase 56)
            if (isSpecificCategory) {
                CategorySearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = "Rechercher une chaîne...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                val gridState = rememberForeverLazyGridState("livetv_mobile_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredStreams) { stream ->
                        val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                        MobileChannelGridCard(
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

