package com.cstv.app.presentation.vod

import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import com.cstv.app.presentation.rememberForeverLazyListState
import com.cstv.app.presentation.rememberForeverLazyGridState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cstv.app.presentation.components.tvFocusHighlight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.map
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodStream
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
import com.cstv.app.presentation.home.components.HomeVodMovieCard
import com.cstv.app.presentation.components.HistoryRemovalDialog
import com.cstv.app.presentation.components.historyItemActions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun VodScreen(
    viewModel: VodViewModel,
    isTv: Boolean,
    favoritesList: List<FavoriteItem>,
    onMovieSelected: (VodStream) -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<com.cstv.app.domain.model.PlaybackPosition?>(null) }
    val historySnackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.historyRemovalError) {
        state.historyRemovalError?.let { historySnackbarHost.showSnackbar(it); viewModel.consumeHistoryRemovalError(); pendingRemoval = null }
    }
    LaunchedEffect(state.resumeMovies, pendingRemoval) {
        pendingRemoval?.takeIf { pending -> state.resumeMovies.none { it.streamId == pending.streamId } }?.let { pendingRemoval = null }
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
                onMovieSelected = onMovieSelected,
                onRefresh = { viewModel.refresh() },
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                favoritesList = favoritesList,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                onHistoryRemove = { stream -> pendingRemoval = state.resumeMovies.firstOrNull { it.streamId == stream.streamId } },
                getScroll = getScroll,
                saveScroll = saveScroll
            )
        } else {
            MobileLayout(
                state = state,
                onCategorySelected = { viewModel.selectCategory(it) },
                onMovieSelected = onMovieSelected,
                onRefresh = { viewModel.refresh() },
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                favoritesList = favoritesList,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                onHistoryRemove = { stream -> pendingRemoval = state.resumeMovies.firstOrNull { it.streamId == stream.streamId } },
                onNavigateToFavorites = onNavigateToFavorites,
                getScroll = getScroll,
                saveScroll = saveScroll
            )
        }
        SnackbarHost(historySnackbarHost, Modifier.align(Alignment.BottomCenter))
        pendingRemoval?.let { position -> HistoryRemovalDialog(position.title.orEmpty(), isTv, state.isRemovingHistory, { viewModel.removeFromContinueWatching(position) }, { if (!state.isRemovingHistory) pendingRemoval = null }) }
    }
}

@Composable
private fun TvLayout(
    state: VodState,
    onCategorySelected: (VodCategory) -> Unit,
    onMovieSelected: (VodStream) -> Unit,
    onRefresh: () -> Unit,
    filteredStreams: List<VodStream>,
    pagedStreams: androidx.paging.compose.LazyPagingItems<VodStream>,
    favoritesList: List<FavoriteItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    onHistoryRemove: (VodStream) -> Unit,
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
    // Favoris (Phase 35), première section du mode "Tout".
    val favoriteMovies = remember(filteredStreams, favoritesList) {
        val favoriteIds = favoritesList.filter { it.type == "movie" }.map { it.id }.toSet()
        filteredStreams.filter { it.streamId in favoriteIds }
    }
    val resumeMoviesStreams = remember(state.resumeMovies) {
        state.resumeMovies.map { pos ->
            VodStream(
                streamId = pos.streamId,
                name = pos.title ?: "",
                streamIcon = pos.coverUrl,
                rating = null,
                added = null,
                categoryId = ""
            )
        }
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
            val listState = rememberForeverLazyListState("vod_tv_all_vertical", getScroll, saveScroll)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (resumeMoviesStreams.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "resume_watching",
                            title = stringResource(R.string.home_resume),
                            movies = resumeMoviesStreams,
                            onMovieSelected = onMovieSelected,
                            isTv = true,
                            onLongClick = onHistoryRemove,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }
                if (favoriteMovies.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            movies = favoriteMovies,
                            onMovieSelected = onMovieSelected,
                            isTv = true,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }
                items(actualCategories) { category ->
                    val catMovies = groupedStreams[category.categoryId] ?: emptyList()
                    if (catMovies.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            movies = catMovies,
                            onMovieSelected = onMovieSelected,
                            isTv = true,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }
            }
        } else {
            // Mode "Catégorie spécifique" : Search & Vertical Grid
            Text(
                text = state.selectedCategory?.categoryName?.uppercase() ?: "FILMS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isSpecificCategory) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.vod_search_placeholder), color = Color.Gray, fontSize = 13.sp) },
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
                        text = if (searchQuery.isBlank()) "Aucun film dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                val gridState = rememberForeverLazyGridState("vod_tv_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusGroup()
                ) {
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
                            MovieTvCard(
                                stream = stream,
                                onClick = { onMovieSelected(stream) }
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
    state: VodState,
    onCategorySelected: (VodCategory) -> Unit,
    onMovieSelected: (VodStream) -> Unit,
    onRefresh: () -> Unit,
    filteredStreams: List<VodStream>,
    pagedStreams: androidx.paging.compose.LazyPagingItems<VodStream>,
    favoritesList: List<FavoriteItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    onHistoryRemove: (VodStream) -> Unit,
    onNavigateToFavorites: () -> Unit,
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
    // Favoris (Phase 35), première section du mode "Tout".
    val favoriteMovies = remember(filteredStreams, favoritesList) {
        val favoriteIds = favoritesList.filter { it.type == "movie" }.map { it.id }.toSet()
        filteredStreams.filter { it.streamId in favoriteIds }
    }
    val resumeMoviesStreams = remember(state.resumeMovies) {
        state.resumeMovies.map { pos ->
            VodStream(
                streamId = pos.streamId,
                name = pos.title ?: "",
                streamIcon = pos.coverUrl,
                rating = null,
                added = null,
                categoryId = ""
            )
        }
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

        // Category Modal Bottom Sheet (Opt-In Material 3)
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
            // Mode "Tout" : list of horizontal movie sections
            val listState = rememberForeverLazyListState("vod_mobile_all_vertical", getScroll, saveScroll)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (resumeMoviesStreams.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "resume_watching",
                            title = stringResource(R.string.home_resume),
                            movies = resumeMoviesStreams,
                            onMovieSelected = onMovieSelected,
                            isTv = false,
                            onLongClick = onHistoryRemove,
                            getScroll = getScroll,
                            saveScroll = saveScroll
                        )
                    }
                }
                if (favoriteMovies.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            movies = favoriteMovies,
                            onMovieSelected = onMovieSelected,
                            isTv = false,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            onSeeAll = onNavigateToFavorites
                        )
                    }
                }
                items(actualCategories) { category ->
                    val catMovies = groupedStreams[category.categoryId] ?: emptyList()
                    if (catMovies.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            movies = catMovies,
                            onMovieSelected = onMovieSelected,
                            isTv = false,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            onSeeAll = { onCategorySelected(category) }
                        )
                    }
                }
            }
        } else {
            // Mode "Catégorie spécifique" : Search & Vertical Grid
            if (isSpecificCategory) {
                CategorySearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = "Rechercher dans cette catégorie...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (pagedStreams.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "Aucun film dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                val gridState = rememberForeverLazyGridState("vod_mobile_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3), // 3 colonnes (iso grille "Voir tout" recherche, Phase 57)
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Surface3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMovieSelected(stream) }
                            ) {
                                Column {
                                    // Poster Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .background(Surface1),
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
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.DarkGray,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        // Rating Badge
                                        val cleanRating = stream.rating?.trim()
                                        if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xCC000000))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(cleanRating, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionRow(
    categoryId: String,
    title: String,
    movies: List<VodStream>,
    onMovieSelected: (VodStream) -> Unit,
    isTv: Boolean,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    onLongClick: ((VodStream) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Phase 56 : titre de catégorie grisé (texte secondaire) + lien "Voir tout".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (onSeeAll != null && !isTv) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Voir tout",
                    color = AccentLavande,
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSeeAll() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        val rowState = rememberForeverLazyListState("vod_row_${categoryId}", getScroll, saveScroll)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(movies) { stream ->
                if (isTv) {
                    MovieTvCard(
                        stream = stream,
                        onClick = { onMovieSelected(stream) },
                        onLongClick = onLongClick?.let { { it(stream) } }
                    )
                } else {
                    // Phase 57 : carte unifiée avec celle de la Home (même taille,
                    // note de notation intégrée).
                    HomeVodMovieCard(
                        stream = stream,
                        onClick = { onMovieSelected(stream) },
                        onLongClick = onLongClick?.let { { it(stream) } },
                        isTv = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    category: VodCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(16.dp))
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
private fun MovieTvCard(
    stream: VodStream,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Surface3
        ),
        modifier = Modifier
            .width(150.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .historyItemActions(isTv = true, onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            // Poster Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Surface1),
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
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Rating Badge (on top of poster)
                val cleanRating = stream.rating?.trim()
                if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(cleanRating, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
}
