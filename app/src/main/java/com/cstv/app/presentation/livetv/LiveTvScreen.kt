package com.cstv.app.presentation.livetv
import com.cstv.app.R
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import com.cstv.app.presentation.livetv.components.*
import com.cstv.app.presentation.components.HistoryRemovalDialog

import com.cstv.app.presentation.rememberForeverLazyListState
import com.cstv.app.presentation.rememberForeverLazyGridState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.cstv.app.presentation.components.CatalogUnavailableState
import com.cstv.app.presentation.components.OfflineBanner
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
import kotlinx.coroutines.flow.map
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
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.TextSecondary
import com.cstv.app.presentation.components.CategoryFilterSheet
import com.cstv.app.presentation.components.CategorySheetEntry
import com.cstv.app.presentation.components.CategorySelectorTrigger
import com.cstv.app.presentation.components.CategorySearchField
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
    var pendingRemoval by remember { mutableStateOf<LiveStream?>(null) }
    val historySnackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.historyRemovalError) {
        state.historyRemovalError?.let { historySnackbarHost.showSnackbar(it); viewModel.consumeHistoryRemovalError(); pendingRemoval = null }
    }
    LaunchedEffect(state.recentlyWatched, pendingRemoval) {
        pendingRemoval?.takeIf { pending -> state.recentlyWatched.none { it.streamId == pending.streamId } }?.let { pendingRemoval = null }
    }

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

    val pagedStreams = remember(viewModel.pagedStreams, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.pagedStreams
        } else {
            viewModel.pagedStreams.map { pagingData ->
                pagingData.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }.collectAsLazyPagingItems()

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
                // Un flux Live exige toujours le serveur : hors ligne, la sélection
                // n'ouvre pas le player, elle affiche le motif du refus.
                onStreamSelected = { stream -> viewModel.requestPlayback { onStreamSelected(stream, filteredStreams) } },
                onRefresh = { viewModel.refresh() },
                favoritesList = favoritesList,
                onToggleFavorite = onToggleFavorite,
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                epgPrograms = state.epgPrograms,
                onLoadEpg = { viewModel.loadEpgForStream(it) },
                onHistoryRemove = { pendingRemoval = it },
                getScroll = getScroll,
                saveScroll = saveScroll
            )
        } else {
            MobileLayout(
                state = state,
                onCategorySelected = { viewModel.selectCategory(it) },
                // Un flux Live exige toujours le serveur : hors ligne, la sélection
                // n'ouvre pas le player, elle affiche le motif du refus.
                onStreamSelected = { stream -> viewModel.requestPlayback { onStreamSelected(stream, filteredStreams) } },
                onRefresh = { viewModel.refresh() },
                favoritesList = favoritesList,
                onToggleFavorite = onToggleFavorite,
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                epgPrograms = state.epgPrograms,
                onLoadEpg = { viewModel.loadEpgForStream(it) },
                onHistoryRemove = { pendingRemoval = it },
                getScroll = getScroll,
                saveScroll = saveScroll
            )
        }
        SnackbarHost(historySnackbarHost, Modifier.align(Alignment.BottomCenter))
        pendingRemoval?.let { stream -> HistoryRemovalDialog(stream.name, isTv, state.isRemovingHistory, { viewModel.removeRecentlyWatched(stream) }, { if (!state.isRemovingHistory) pendingRemoval = null }) }
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
    pagedStreams: androidx.paging.compose.LazyPagingItems<LiveStream>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    onHistoryRemove: (LiveStream) -> Unit,
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
        // Bannière hors ligne : discrète, non bloquante, jamais en remplacement
        // du contenu (règle « une donnée ancienne reste consultable »).
        OfflineBanner(status = state.catalogStatus, onRetry = onRefresh)

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

        if (!state.catalogStatus.isComplete && state.catalogStatus.isOffline && state.streams.isEmpty()) {
            // Uniquement sans cache ET sans réseau : ne doit jamais se
            // substituer à une liste simplement filtrée à vide.
            CatalogUnavailableState(onRetry = onRefresh, isRetrying = state.catalogStatus.isSyncing)
        } else if (state.isLoadingStreams || state.isLoadingCategories) {
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
                            onLongClick = onHistoryRemove,
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
                    placeholder = { Text(stringResource(R.string.livetv_search_placeholder), color = Color.Gray, fontSize = 13.sp) },
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

            if (pagedStreams.itemCount == 0) {
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
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
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
    pagedStreams: androidx.paging.compose.LazyPagingItems<LiveStream>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    onHistoryRemove: (LiveStream) -> Unit,
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
        // Bannière hors ligne : discrète, non bloquante, jamais en remplacement
        // du contenu (règle « une donnée ancienne reste consultable »).
        OfflineBanner(status = state.catalogStatus, onRetry = onRefresh)

        // Sélecteur de catégorie unifié (Phase 56) : pleine largeur, sans bouton
        // "Rafraîchir" (rafraîchissement manuel déplacé dans les Paramètres),
        // espacé du haut, fond neutre transparent comme le reste du layout.
        CategorySelectorTrigger(
            label = state.selectedCategory?.categoryName ?: "Tout",
            onClick = { showCategorySheet = true },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        )

        // Bottom sheet de catégorie partagée, iso-maquette (compteurs = cache local ;
        // "Tout" = somme de toutes les catégories).
        if (showCategorySheet) {
            val totalCount = state.categoryCounts.values.sum().takeIf { it > 0 }
            CategoryFilterSheet(
                entries = filteredCategories.map { category ->
                    CategorySheetEntry(
                        id = category.categoryId,
                        label = category.categoryName,
                        count = if (category.categoryId == "all") totalCount
                        else state.categoryCounts[category.categoryId]
                    )
                },
                selectedId = state.selectedCategory?.categoryId,
                searchQuery = categorySearchQuery,
                onSearchQueryChange = { categorySearchQuery = it },
                onSelect = { categoryId ->
                    state.categories.find { it.categoryId == categoryId }?.let(onCategorySelected)
                    showCategorySheet = false
                    categorySearchQuery = ""
                },
                onDismiss = {
                    showCategorySheet = false
                    categorySearchQuery = ""
                }
            )
        }

        if (!state.catalogStatus.isComplete && state.catalogStatus.isOffline && state.streams.isEmpty()) {
            // Uniquement sans cache ET sans réseau : ne doit jamais se
            // substituer à une liste simplement filtrée à vide.
            CatalogUnavailableState(onRetry = onRefresh, isRetrying = state.catalogStatus.isSyncing)
        } else if (state.isLoadingStreams || state.isLoadingCategories) {
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
                            onLongClick = onHistoryRemove,
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
                    // Espace champ->liste (10) légèrement plus petit que dropdown->champ (14).
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                )
            }

            if (pagedStreams.itemCount == 0) {
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
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
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
}
