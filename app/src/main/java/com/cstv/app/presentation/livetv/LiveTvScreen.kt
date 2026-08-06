package com.cstv.app.presentation.livetv
import com.cstv.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
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
import com.cstv.app.presentation.components.tvPivotCell
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.TvFocusSelectorOverlay
import com.cstv.app.presentation.components.TvFocusSelectorState
import com.cstv.app.presentation.components.tvPivotVerticalEndSpacer
import com.cstv.app.presentation.components.TV_PIVOT_VERTICAL_START_RESERVE
import com.cstv.app.presentation.components.tvPivotVerticalStartReserve
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import com.cstv.app.presentation.components.TvCategorySelectorTrigger
import com.cstv.app.presentation.components.TvCategoryPickerDialog
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.cstv.app.presentation.vod.CatalogFocusTarget
import com.cstv.app.presentation.vod.CatalogInitialFocusTarget
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
    val tvFocusSelector = remember { TvFocusSelectorState() }

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
                epgPrograms = state.epgPrograms,
                onLoadEpg = { viewModel.loadEpgForStream(it) },
                onHistoryRemove = { pendingRemoval = it },
                getScroll = getScroll,
                saveScroll = saveScroll,
                tvFocusSelector = tvFocusSelector
            )
            TvFocusSelectorOverlay(tvFocusSelector, modifier = Modifier.fillMaxSize())
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
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    onHistoryRemove: (LiveStream) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit,
    tvFocusSelector: TvFocusSelectorState
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"
    var showCategoryPicker by remember { mutableStateOf(false) }
    val categoryTriggerFocusRequester = remember { FocusRequester() }

    // Voir VodScreen : regroupement mesuré, thread principal.
    val groupedStreams = remember(filteredStreams) {
        val startedAt = System.nanoTime()
        filteredStreams.groupBy { it.categoryId }
            .also {
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "Live regroupement ${filteredStreams.size} flux en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
            }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Chaînes favorites (Phase 35), section dédiée du mode "Tout", sous
    // "Récemment regardées" — comme sur Films/Séries.
    // Voir VodScreen : rangée pilotée par `favoritesList`, pour qu'une chaîne
    // favorite classée au-delà de la centième de sa catégorie ne disparaisse
    // pas. L'entrée du catalogue est préférée quand elle est chargée : elle
    // seule porte `epgChannelId`, donc le programme en cours.
    val favoriteStreams = remember(filteredStreams, favoritesList, searchQuery) {
        val loadedById = filteredStreams.associateBy { it.streamId }
        favoritesList.asSequence()
            .filter { it.type == "live" }
            .map { favorite ->
                loadedById[favorite.id] ?: LiveStream(
                    streamId = favorite.id,
                    name = favorite.name,
                    streamIcon = favorite.cover,
                    epgChannelId = null,
                    num = 0,
                    categoryId = favorite.categoryId
                )
            }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .toList()
    }
    val firstCategoryId = actualCategories.firstOrNull { (groupedStreams[it.categoryId] ?: emptyList()).isNotEmpty() }?.categoryId
    // B17 review (C1) : `filteredStreams` (déjà scopé à la catégorie active et
    // filtré par le texte) est stable, contrairement à `pagedStreams.itemCount`
    // qui retombe transitoirement à 0 à chaque frappe (voir VodScreen).
    val initialTarget = remember(isAllSelected, state.recentlyWatched, favoriteStreams, firstCategoryId, filteredStreams.size) {
        CatalogInitialFocusTarget.of(isAllSelected, state.recentlyWatched.isNotEmpty(), favoriteStreams.isNotEmpty(), firstCategoryId, filteredStreams.size)
    }
    // B17 (M4, décision 6) : voir VodScreen — un scroll restauré prime sur le
    // focus par défaut en tête de liste.
    val scrollKey = if (isAllSelected) "livetv_tv_all_vertical" else "livetv_tv_cat_" + (state.selectedCategory?.categoryId ?: "0")
    val hasRestorableScroll = remember(scrollKey) { getScroll(scrollKey) != Pair(0, 0) }
    val initialFocus = rememberTvInitialFocus(
        isTv = true,
        ready = !state.isLoadingStreams && !state.isLoadingCategories && initialTarget != null && !hasRestorableScroll,
        targetKey = initialTarget to state.selectedCategory?.categoryId
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sélecteur de catégorie (F24 : dropdown aligné VOD/Séries), seul sur sa
        // ligne : le rafraîchissement manuel vit dans les Paramètres, comme sur
        // mobile depuis la Phase 56.
        TvCategorySelectorTrigger(
            label = stringResource(
                R.string.livetv_category_selector_label,
                (state.selectedCategory?.categoryName ?: "Tout").uppercase()
            ),
            onClick = { showCategoryPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .focusRequester(categoryTriggerFocusRequester)
        )
        if (showCategoryPicker) {
            val totalCount = state.categoryCounts.values.sum().takeIf { it > 0 }
            TvCategoryPickerDialog(
                entries = state.categories.map { category ->
                    CategorySheetEntry(
                        id = category.categoryId,
                        label = category.categoryName,
                        count = if (category.categoryId == "all") totalCount
                                else state.categoryCounts[category.categoryId]
                    )
                },
                selectedId = state.selectedCategory?.categoryId,
                onSelect = { id ->
                    state.categories.firstOrNull { it.categoryId == id }?.let(onCategorySelected)
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }
        // Focus perdu à la fermeture du dialogue : redemandé sur le
        // déclencheur après une fermeture réelle (cf. VodScreen/SeriesScreen).
        var hasOpenedCategoryPicker by remember { mutableStateOf(false) }
        LaunchedEffect(showCategoryPicker) {
            if (showCategoryPicker) {
                hasOpenedCategoryPicker = true
            } else if (hasOpenedCategoryPicker) {
                runCatching { categoryTriggerFocusRequester.requestFocus() }
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
            CompositionLocalProvider(LocalTvFocusSelector provides tvFocusSelector) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
                    .onFocusChanged { if (!it.hasFocus) tvFocusSelector.clear() }
            ) {
                tvPivotVerticalStartReserve(true)
                // Section 1: Récemment regardées (if not empty)
                if (state.recentlyWatched.isNotEmpty()) {
                    item(key = "recently_watched") {
                        RecentlyWatchedRow(
                            streams = state.recentlyWatched,
                            onStreamSelected = onStreamSelected,
                            isTv = true,
                            epgPrograms = epgPrograms,
                            onLoadEpg = onLoadEpg,
                            onLongClick = onHistoryRemove,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.RESUME
                        )
                    }
                }

                // Section 2: Favoris (Phase 35), sous "Récemment regardées"
                if (favoriteStreams.isNotEmpty()) {
                    item(key = "favorites") {
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
                            saveScroll = saveScroll,
                            sectionListState = listState
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.FAVORITES
                        )
                    }
                }

                items(actualCategories, key = { it.categoryId }) { category ->
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
                            saveScroll = saveScroll,
                            sectionListState = listState
                            ,onSeeAll = { onCategorySelected(category) }
                            ,totalCount = state.categoryCounts[category.categoryId]
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.FIRST_CATEGORY && category.categoryId == firstCategoryId
                        )
                    }
                }
                tvPivotVerticalEndSpacer(true)
            }
            }
        } else {
            // Mode "Catégorie spécifique" : grille verticale seule. Pas de champ
            // de recherche ici, comme sur Films et Séries : la recherche globale
            // couvre déjà ce besoin et le champ captait le focus à l'arrivée.
            Text(
                text = state.selectedCategory?.categoryName?.uppercase() ?: "DIRECT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (pagedStreams.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "Aucune chaîne dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                val gridState = rememberForeverLazyGridState("livetv_tv_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                CompositionLocalProvider(LocalTvFocusSelector provides tvFocusSelector) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = TV_PIVOT_VERTICAL_START_RESERVE,
                        bottom = LocalConfiguration.current.screenHeightDp.dp / 2
                    ),
                    modifier = Modifier.fillMaxSize()
                        .focusGroup()
                        .onFocusChanged { if (!it.hasFocus) tvFocusSelector.clear() }
                ) {
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
                            val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                            Box(
                                // Rayon 12.dp : StreamTvCard n'a pas été unifiée au
                                // rayon 14.dp de B18 (hors périmètre de ce ticket-là).
                                modifier = Modifier.tvPivotCell(true, gridState, index, selectorCornerRadius = 12.dp)
                                    .tvInitialFocusTarget(initialFocus, index == 0 && initialTarget == CatalogFocusTarget.GRID_FIRST_CELL),
                                // Cf. VodScreen.kt (Review F19, Majeur #1) : force la
                                // propagation de la contrainte min de la cellule à l'enfant.
                                propagateMinConstraints = true
                            ) {
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

    // Voir VodScreen : mesure présente sur TvLayout, manquante ici.
    val groupedStreams = remember(filteredStreams) {
        val startedAt = System.nanoTime()
        filteredStreams.groupBy { it.categoryId }
            .also {
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "Live regroupement ${filteredStreams.size} flux en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
            }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Chaînes favorites (Phase 35), section dédiée du mode "Tout", sous
    // "Récemment regardées" — comme sur Films/Séries.
    // Voir VodScreen : rangée pilotée par `favoritesList`, pour qu'une chaîne
    // favorite classée au-delà de la centième de sa catégorie ne disparaisse
    // pas. L'entrée du catalogue est préférée quand elle est chargée : elle
    // seule porte `epgChannelId`, donc le programme en cours.
    val favoriteStreams = remember(filteredStreams, favoritesList, searchQuery) {
        val loadedById = filteredStreams.associateBy { it.streamId }
        favoritesList.asSequence()
            .filter { it.type == "live" }
            .map { favorite ->
                loadedById[favorite.id] ?: LiveStream(
                    streamId = favorite.id,
                    name = favorite.name,
                    streamIcon = favorite.cover,
                    epgChannelId = null,
                    num = 0,
                    categoryId = favorite.categoryId
                )
            }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .toList()
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
                            saveScroll = saveScroll,
                            sectionListState = listState
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
                            saveScroll = saveScroll,
                            sectionListState = listState
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
                            sectionListState = listState,
                            onSeeAll = { onCategorySelected(category) },
                            totalCount = state.categoryCounts[category.categoryId]
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
