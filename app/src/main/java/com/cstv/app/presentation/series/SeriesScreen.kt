package com.cstv.app.presentation.series

import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import com.cstv.app.presentation.rememberForeverLazyListState
import com.cstv.app.presentation.rememberRowScrollState
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
import com.cstv.app.presentation.components.tvPivotItem
import com.cstv.app.presentation.components.tvPivotCell
import com.cstv.app.presentation.components.tvPivotSection
import com.cstv.app.presentation.components.tvPivotHorizontalEndSpacer
import com.cstv.app.presentation.components.tvPivotVerticalEndSpacer
import com.cstv.app.presentation.components.TV_PIVOT_VERTICAL_START_RESERVE
import com.cstv.app.presentation.components.tvPivotGridEndReserve
import com.cstv.app.presentation.components.tvPivotVerticalStartReserve
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.TvFocusSelectorOverlay
import com.cstv.app.presentation.components.TvFocusSelectorState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.EpisodeLabel
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.TextSecondary
import com.cstv.app.presentation.components.CategoryFilterSheet
import com.cstv.app.presentation.components.CategorySheetEntry
import com.cstv.app.presentation.components.CategorySelectorTrigger
import com.cstv.app.presentation.components.CategorySearchField
import com.cstv.app.presentation.components.TvCategoryPickerDialog
import com.cstv.app.presentation.components.TvCategorySelectorTrigger
import com.cstv.app.presentation.components.ActiveFilterChipsRow
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.components.tvFocusDownTo
import com.cstv.app.presentation.search.AdvancedSearchSheet
import com.cstv.app.domain.model.CatalogFilterMatcher
import com.cstv.app.presentation.vod.CatalogFocusTarget
import com.cstv.app.presentation.vod.CatalogInitialFocusTarget
import com.cstv.app.presentation.home.components.HomeSeriesShowCard
import com.cstv.app.presentation.components.HistoryRemovalDialog
import com.cstv.app.presentation.components.historyItemActions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    isTv: Boolean,
    favoritesList: List<FavoriteItem>,
    onSeriesSelected: (SeriesStream) -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<com.cstv.app.domain.model.PlaybackPosition?>(null) }
    val historySnackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.historyRemovalError) {
        state.historyRemovalError?.let { historySnackbarHost.showSnackbar(it); viewModel.consumeHistoryRemovalError(); pendingRemoval = null }
    }
    LaunchedEffect(state.resumeSeries, pendingRemoval) {
        pendingRemoval?.takeIf { pending -> state.resumeSeries.none { it.streamId == pending.streamId } }?.let { pendingRemoval = null }
    }

    var searchQuery by remember { mutableStateOf("") }
    // Voir VodScreen : la cible réellement focalisée complète l'index du
    // premier item visible, qui peut être la rangée juste au-dessus du pivot.
    var lastFocusedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedSeriesId by rememberSaveable { mutableStateOf<Int?>(null) }
    
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

    val pagedStreams = remember(viewModel.pagedStreams, searchQuery, state.advancedFilter) {
        if (searchQuery.isBlank() && state.advancedFilter.isEmpty) {
            viewModel.pagedStreams
        } else {
            viewModel.pagedStreams.map { pagingData ->
                pagingData.filter {
                    (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)) &&
                        CatalogFilterMatcher.matchesContent(it.rating, it.releaseYear, it.genre, state.advancedFilter)
                }
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
                onSeriesSelected = onSeriesSelected,
                onRefresh = { viewModel.refresh() },
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                favoritesList = favoritesList,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                onHistoryRemove = { stream -> pendingRemoval = state.resumeSeries.firstOrNull { (it.seriesId ?: it.streamId) == stream.seriesId } },
                getScroll = getScroll,
                saveScroll = saveScroll,
                tvFocusSelector = tvFocusSelector
                ,lastFocusedCategoryId = lastFocusedCategoryId
                ,lastFocusedSeriesId = lastFocusedSeriesId
                ,onMediaFocused = { categoryId, seriesId ->
                    lastFocusedCategoryId = categoryId
                    lastFocusedSeriesId = seriesId
                }
                ,onFilterSheetOpen = { viewModel.setFilterSheetOpen(true) }
                ,onFilterSheetDismiss = { viewModel.setFilterSheetOpen(false) }
                ,onMinRatingSelected = viewModel::setMinRating
                ,onYearRangeChanged = viewModel::setYearRange
                ,onGenreToggled = viewModel::toggleGenre
                ,onResetFilter = viewModel::resetFilter
                ,onApplyFilter = viewModel::applyFilter
                ,onRemoveMinRating = viewModel::removeMinRatingFilter
                ,onRemoveYearRange = viewModel::removeYearRangeFilter
                ,onRemoveGenre = viewModel::removeGenreFilter
            )
            TvFocusSelectorOverlay(tvFocusSelector, modifier = Modifier.fillMaxSize())
        } else {
            MobileLayout(
                state = state,
                onCategorySelected = { viewModel.selectCategory(it) },
                onSeriesSelected = onSeriesSelected,
                onRefresh = { viewModel.refresh() },
                filteredStreams = filteredStreams,
                pagedStreams = pagedStreams,
                favoritesList = favoritesList,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                isSpecificCategory = isSpecificCategory,
                onHistoryRemove = { stream -> pendingRemoval = state.resumeSeries.firstOrNull { (it.seriesId ?: it.streamId) == stream.seriesId } },
                onNavigateToFavorites = onNavigateToFavorites,
                getScroll = getScroll,
                saveScroll = saveScroll
            )
        }
        SnackbarHost(historySnackbarHost, Modifier.align(Alignment.BottomCenter))
        pendingRemoval?.let { position -> HistoryRemovalDialog(position.title.orEmpty(), isTv, state.isRemovingHistory, { viewModel.removeFromContinueWatching(position) }, { if (!state.isRemovingHistory) pendingRemoval = null }) }
    }
}

/**
 * Plafond d'une rangée horizontale du mode « Tout ». Au-delà, la LazyRow
 * retient des milliers d'éléments dont aucun ne sera atteint au D-pad : la
 * grille de la catégorie ("Voir tout" / puce de catégorie) reste le chemin
 * exhaustif (T10). Modifié de 250 à 100 avec une vignette "Voir tout" (101e).
 */
private const val CATEGORY_ROW_MAX_ITEMS = 100

@Composable
private fun TvLayout(
    state: SeriesState,
    onCategorySelected: (SeriesCategory) -> Unit,
    onSeriesSelected: (SeriesStream) -> Unit,
    onRefresh: () -> Unit,
    filteredStreams: List<SeriesStream>,
    pagedStreams: androidx.paging.compose.LazyPagingItems<SeriesStream>,
    favoritesList: List<FavoriteItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    onHistoryRemove: (SeriesStream) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit,
    tvFocusSelector: TvFocusSelectorState,
    lastFocusedCategoryId: String?,
    lastFocusedSeriesId: Int?,
    onMediaFocused: (String, Int) -> Unit,
    onFilterSheetOpen: () -> Unit,
    onFilterSheetDismiss: () -> Unit,
    onMinRatingSelected: (Int?) -> Unit,
    onYearRangeChanged: (IntRange) -> Unit,
    onGenreToggled: (String) -> Unit,
    onResetFilter: () -> Unit,
    onApplyFilter: () -> Unit,
    onRemoveMinRating: () -> Unit,
    onRemoveYearRange: () -> Unit,
    onRemoveGenre: (String) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    // Voir VodScreen : regroupement mesuré, thread principal.
    val groupedStreams = remember(filteredStreams) {
        val startedAt = System.nanoTime()
        filteredStreams
            .groupBy { it.categoryId }
            .also {
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "Séries regroupement ${filteredStreams.size} flux en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
            }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Voir VodScreen : rangée pilotée par `favoritesList`, pour qu'un favori
    // classé au-delà du centième de sa catégorie ne disparaisse pas.
    val favoriteSeries = remember(filteredStreams, favoritesList, searchQuery) {
        val loadedById = filteredStreams.associateBy { it.seriesId }
        favoritesList.asSequence()
            .filter { it.type == "series" }
            .map { favorite ->
                loadedById[favorite.id] ?: SeriesStream(
                    seriesId = favorite.id,
                    name = favorite.name,
                    cover = favorite.cover,
                    rating = null,
                    added = null,
                    categoryId = favorite.categoryId
                )
            }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .toList()
    }
    val resumeSeriesStreams = remember(state.resumeSeries) {
        state.resumeSeries.map { pos ->
            SeriesStream(
                seriesId = pos.seriesId ?: pos.streamId,
                name = pos.title ?: "",
                cover = pos.coverUrl,
                rating = null,
                added = null,
                categoryId = ""
            )
        }
    }
    // Badge "S01E03" en surimpression sur la rangée "Reprendre" (B18) : seule
    // information perdue par le retrait du titre, pour un épisode en cours.
    val resumeLabels = remember(state.resumeSeries) {
        EpisodeLabel.buildResumeLabels(state.resumeSeries) { it.seriesId ?: it.streamId }
    }
    val firstCategoryId = actualCategories.firstOrNull { (groupedStreams[it.categoryId] ?: emptyList()).isNotEmpty() }?.categoryId
    // B17 review (C1) : dérivé de `state.streams`, pas de `pagedStreams.itemCount`
    // (voir VodScreen — même flicker de flux Paging lors d'une recherche/filtre tapé).
    val gridFilteredCount = remember(state.streams, searchQuery, state.advancedFilter) {
        state.streams.count { stream ->
            (searchQuery.isBlank() || stream.name.contains(searchQuery, ignoreCase = true)) &&
                CatalogFilterMatcher.matchesContent(stream.rating, stream.releaseYear, stream.genre, state.advancedFilter)
        }
    }
    val initialTarget = remember(isAllSelected, resumeSeriesStreams, favoriteSeries, firstCategoryId, gridFilteredCount) {
        CatalogInitialFocusTarget.of(isAllSelected, resumeSeriesStreams.isNotEmpty(), favoriteSeries.isNotEmpty(), firstCategoryId, gridFilteredCount)
    }
    // B17 (M4, décision 6) : voir VodScreen — un scroll restauré prime sur le
    // focus par défaut en tête de liste.
    val scrollKey = if (isAllSelected) "series_tv_all_vertical" else "series_tv_cat_" + (state.selectedCategory?.categoryId ?: "0")
    val hasRestorableScroll = remember(scrollKey) { getScroll(scrollKey) != Pair(0, 0) }
    val hasFocusRestoreTarget = lastFocusedCategoryId != null && lastFocusedSeriesId != null &&
        (isAllSelected || lastFocusedCategoryId == state.selectedCategory?.categoryId)
    val restoredFocus = rememberTvInitialFocus(
        isTv = true,
        ready = !state.isLoadingStreams && !state.isLoadingCategories && hasFocusRestoreTarget,
        targetKey = "series_restore_${lastFocusedCategoryId}_${lastFocusedSeriesId}"
    )
    val initialFocus = rememberTvInitialFocus(
        isTv = true,
        ready = !state.isLoadingStreams && !state.isLoadingCategories && initialTarget != null && !hasRestorableScroll && !hasFocusRestoreTarget,
        targetKey = initialTarget to state.selectedCategory?.categoryId
    )

    var showCategoryPicker by remember { mutableStateOf(false) }
    val categoryTriggerFocusRequester = remember { FocusRequester() }
    // Cible de descente depuis le déclencheur de catégorie : la première
    // vignette de la grille, colonne de gauche (B22).
    val gridEntryFocusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // F22/B20 : la catégorie, la recherche et les filtres partagent une
        // même ligne dès qu'une catégorie précise est ouverte.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            TvCategorySelectorTrigger(
                label = stringResource(R.string.series_category_selector_label, (state.selectedCategory?.categoryName ?: "Tout").uppercase()),
                onClick = { showCategoryPicker = true },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(categoryTriggerFocusRequester)
                    .tvFocusDownTo(gridEntryFocusRequester)
            )
            if (isSpecificCategory) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onFilterSheetOpen,
                    modifier = Modifier.background(
                        color = if (state.advancedFilter.isActive) AccentLavande else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                ) {
                    Icon(
                        Icons.Default.Tune,
                        stringResource(R.string.catalog_filters_button_description),
                        tint = if (state.advancedFilter.isActive) Color(0xFF17131F) else Color.White
                    )
                }
            }
        }
        if (showCategoryPicker) {
            TvCategoryPickerDialog(
                entries = state.categories.map { CategorySheetEntry(it.categoryId, it.categoryName, state.categoryCounts[it.categoryId]) },
                selectedId = state.selectedCategory?.categoryId,
                onSelect = { id ->
                    state.categories.firstOrNull { it.categoryId == id }?.let(onCategorySelected)
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }
        // Focus perdu à la fermeture du dialogue (risque étape 4) : redemandé
        // sur le déclencheur après une fermeture réelle (pas à l'entrée sur
        // l'écran, où B17 pilote déjà le focus initial sur le premier média).
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
            val listState = rememberForeverLazyListState("series_tv_all_vertical", getScroll, saveScroll)
            CompositionLocalProvider(LocalTvFocusSelector provides tvFocusSelector) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
                    .onFocusChanged { if (!it.hasFocus) tvFocusSelector.clear() }
            ) {
                tvPivotVerticalStartReserve(true)
                if (resumeSeriesStreams.isNotEmpty()) {
                    item(key = "resume_watching") {
                        CategorySectionRow(
                            categoryId = "resume_watching",
                            title = stringResource(R.string.home_resume),
                            series = resumeSeriesStreams,
                            onSeriesSelected = onSeriesSelected,
                            isTv = true,
                            onLongClick = onHistoryRemove,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState,
                            badgeFor = { stream -> resumeLabels[stream.seriesId] }
                            ,restoredFocusState = restoredFocus
                            ,restoredCategoryId = lastFocusedCategoryId
                            ,restoredSeriesId = lastFocusedSeriesId
                            ,onMediaFocused = onMediaFocused
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.RESUME
                        )
                    }
                }
                if (favoriteSeries.isNotEmpty()) {
                    item(key = "favorites") {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            series = favoriteSeries,
                            onSeriesSelected = onSeriesSelected,
                            isTv = true,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState
                            ,restoredFocusState = restoredFocus
                            ,restoredCategoryId = lastFocusedCategoryId
                            ,restoredSeriesId = lastFocusedSeriesId
                            ,onMediaFocused = onMediaFocused
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.FAVORITES
                        )
                    }
                }
                items(actualCategories, key = { it.categoryId }) { category ->
                    val catSeries = groupedStreams[category.categoryId] ?: emptyList()
                    if (catSeries.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            series = catSeries,
                            onSeriesSelected = onSeriesSelected,
                            isTv = true,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState,
                            onSeeAll = { onCategorySelected(category) },
                            totalCount = state.categoryCounts[category.categoryId],
                            restoredFocusState = restoredFocus
                            ,restoredCategoryId = lastFocusedCategoryId
                            ,restoredSeriesId = lastFocusedSeriesId
                            ,onMediaFocused = onMediaFocused
                            ,initialFocusState = initialFocus
                            ,isInitialTarget = initialTarget == CatalogFocusTarget.FIRST_CATEGORY && category.categoryId == firstCategoryId
                        )
                    }
                }
                tvPivotVerticalEndSpacer(true)
            }
            }
        } else {
            // Mode "Catégorie spécifique" : Search & Vertical Grid
            if (isSpecificCategory) {
                if (state.advancedFilter.isActive) {
                    ActiveFilterChipsRow(state.advancedFilter, true, onRemoveMinRating, onRemoveYearRange, onRemoveGenre,
                        Modifier.padding(bottom = 12.dp))
                }
            }

            if (pagedStreams.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> stringResource(R.string.catalog_no_search_result, searchQuery)
                            state.advancedFilter.isActive -> stringResource(R.string.series_empty_filtered)
                            else -> stringResource(R.string.series_empty_category)
                        },
                        color = Color.Gray
                    )
                }
            } else {
                val gridState = rememberForeverLazyGridState("series_tv_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
                CompositionLocalProvider(LocalTvFocusSelector provides tvFocusSelector) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Voir VodScreen : cellules de 130 dp et écart visible de
                    // 12 dp, identiques aux rangées horizontales « Tout ».
                    val horizontalPadding = 24.dp
                    val cardWidth = 130.dp
                    val gridGap = 12.dp
                    val availableWidth = (maxWidth - horizontalPadding).coerceAtLeast(cardWidth)
                    val columns = ((availableWidth + gridGap) / (cardWidth + gridGap)).roundToInt().coerceAtLeast(1)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(gridGap),
                    verticalArrangement = Arrangement.spacedBy(gridGap),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = TV_PIVOT_VERTICAL_START_RESERVE,
                        bottom = tvPivotGridEndReserve()
                    ),
                    modifier = Modifier.fillMaxSize()
                        .focusGroup()
                        .onFocusChanged { if (!it.hasFocus) tvFocusSelector.clear() }
                ) {
                    items(pagedStreams.itemCount) { index ->
                        val stream = pagedStreams[index]
                        if (stream != null) {
                            Box(
                                modifier = Modifier.tvPivotCell(true, gridState, index)
                                    .tvInitialFocusTarget(
                                        if (lastFocusedCategoryId == state.selectedCategory?.categoryId && lastFocusedSeriesId == stream.seriesId) restoredFocus else initialFocus,
                                        (lastFocusedCategoryId == state.selectedCategory?.categoryId && lastFocusedSeriesId == stream.seriesId) || (index == 0 && initialTarget == CatalogFocusTarget.GRID_FIRST_CELL)
                                    )
                                    // Cible de la descente depuis le déclencheur (B22).
                                    .then(if (index == 0) Modifier.focusRequester(gridEntryFocusRequester) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                HomeSeriesShowCard(
                                    stream = stream,
                                    onClick = { onSeriesSelected(stream) },
                                    isTv = true,
                                    fillCell = false,
                                    modifier = Modifier.onFocusChanged { if (it.isFocused) onMediaFocused(state.selectedCategory?.categoryId.orEmpty(), stream.seriesId) }
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
    if (state.isFilterSheetOpen) {
        AdvancedSearchSheet(
            filter = state.advancedFilter, availableGenres = state.availableGenres, availableCategories = emptyList(),
            resultCount = state.filteredCount, catalogYearRange = state.categoryYearRange, isTv = true,
            showMediaTypeFilter = false, showCategoryFilter = false,
            onMediaTypeSelected = {}, onCategorySelected = {}, onMinRatingSelected = onMinRatingSelected,
            onYearRangeChanged = onYearRangeChanged, onGenreToggled = onGenreToggled, onReset = onResetFilter,
            onApply = onApplyFilter, onDismiss = onFilterSheetDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLayout(
    state: SeriesState,
    onCategorySelected: (SeriesCategory) -> Unit,
    onSeriesSelected: (SeriesStream) -> Unit,
    onRefresh: () -> Unit,
    filteredStreams: List<SeriesStream>,
    pagedStreams: androidx.paging.compose.LazyPagingItems<SeriesStream>,
    favoritesList: List<FavoriteItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSpecificCategory: Boolean,
    onHistoryRemove: (SeriesStream) -> Unit,
    onNavigateToFavorites: () -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    val isAllSelected = state.selectedCategory?.categoryId == "all"

    // Voir VodScreen : mesure présente sur TvLayout, manquante ici.
    val groupedStreams = remember(filteredStreams) {
        val startedAt = System.nanoTime()
        filteredStreams
            .groupBy { it.categoryId }
            .also {
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "Séries regroupement ${filteredStreams.size} flux en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
            }
    }
    val actualCategories = remember(state.categories) {
        state.categories.filter { it.categoryId != "all" }
    }
    // Voir VodScreen : rangée pilotée par `favoritesList`, pour qu'un favori
    // classé au-delà du centième de sa catégorie ne disparaisse pas.
    val favoriteSeries = remember(filteredStreams, favoritesList, searchQuery) {
        val loadedById = filteredStreams.associateBy { it.seriesId }
        favoritesList.asSequence()
            .filter { it.type == "series" }
            .map { favorite ->
                loadedById[favorite.id] ?: SeriesStream(
                    seriesId = favorite.id,
                    name = favorite.name,
                    cover = favorite.cover,
                    rating = null,
                    added = null,
                    categoryId = favorite.categoryId
                )
            }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .toList()
    }
    val resumeSeriesStreams = remember(state.resumeSeries) {
        state.resumeSeries.map { pos ->
            SeriesStream(
                seriesId = pos.seriesId ?: pos.streamId,
                name = pos.title ?: "",
                cover = pos.coverUrl,
                rating = null,
                added = null,
                categoryId = ""
            )
        }
    }
    // Badge "S01E03" en surimpression sur la rangée "Reprendre" (B18) : seule
    // information perdue par le retrait du titre, pour un épisode en cours.
    val resumeLabels = remember(state.resumeSeries) {
        EpisodeLabel.buildResumeLabels(state.resumeSeries) { it.seriesId ?: it.streamId }
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
            // Mode "Tout" : list of horizontal series sections
            val listState = rememberForeverLazyListState("series_mobile_all_vertical", getScroll, saveScroll)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (resumeSeriesStreams.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "resume_watching",
                            title = stringResource(R.string.home_resume),
                            series = resumeSeriesStreams,
                            onSeriesSelected = onSeriesSelected,
                            isTv = false,
                            onLongClick = onHistoryRemove,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState,
                            badgeFor = { stream -> resumeLabels[stream.seriesId] }
                        )
                    }
                }
                if (favoriteSeries.isNotEmpty()) {
                    item {
                        CategorySectionRow(
                            categoryId = "favorites",
                            title = "Favoris",
                            series = favoriteSeries,
                            onSeriesSelected = onSeriesSelected,
                            isTv = false,
                            getScroll = getScroll,
                            saveScroll = saveScroll,
                            sectionListState = listState,
                            onSeeAll = onNavigateToFavorites
                        )
                    }
                }
                items(actualCategories) { category ->
                    val catSeries = groupedStreams[category.categoryId] ?: emptyList()
                    if (catSeries.isNotEmpty()) {
                        CategorySectionRow(
                            categoryId = category.categoryId,
                            title = category.categoryName,
                            series = catSeries,
                            onSeriesSelected = onSeriesSelected,
                            isTv = false,
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
                        text = if (searchQuery.isBlank()) "Aucune série dans cette catégorie" else "Aucun résultat pour « $searchQuery »",
                        color = Color.Gray
                    )
                }
            } else {
                val gridState = rememberForeverLazyGridState("series_mobile_cat_" + (state.selectedCategory?.categoryId ?: "0"), getScroll, saveScroll)
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
                            HomeSeriesShowCard(
                                stream = stream,
                                onClick = { onSeriesSelected(stream) },
                                fillCell = true
                            )
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
    series: List<SeriesStream>,
    onSeriesSelected: (SeriesStream) -> Unit,
    isTv: Boolean,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit,
    sectionListState: LazyListState,
    onSeeAll: (() -> Unit)? = null,
    /** Voir VodScreen : effectif total, décorrélé du plafond d'affichage. */
    totalCount: Int? = null,
    onLongClick: ((SeriesStream) -> Unit)? = null,
    /** Badge court en surimpression (ex. « S01E03 »), réservé à la rangée « Reprendre » (B18). */
    badgeFor: ((SeriesStream) -> String?)? = null,
    restoredFocusState: com.cstv.app.presentation.components.TvInitialFocusState? = null,
    restoredCategoryId: String? = null,
    restoredSeriesId: Int? = null,
    onMediaFocused: (String, Int) -> Unit = { _, _ -> },
    initialFocusState: com.cstv.app.presentation.components.TvInitialFocusState? = null,
    isInitialTarget: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .tvPivotSection(isTv, sectionListState, categoryId)
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

        val rowState = rememberRowScrollState(isTv, "series_row_${categoryId}", getScroll, saveScroll)
        val displayList = remember(series) { series.take(CATEGORY_ROW_MAX_ITEMS) }
        val showSeeAllCard = onSeeAll != null && (totalCount ?: series.size) > CATEGORY_ROW_MAX_ITEMS
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            // Voir VodScreen : `focusRestorer` sur une rangée annule la
            // recherche de focus vers le haut dès qu'une vignette sauvée a été
            // recyclée. La restauration exacte passe par [restoredFocus].
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            itemsIndexed(displayList) { index, stream ->
                val isRestoredTarget = categoryId == restoredCategoryId && stream.seriesId == restoredSeriesId
                val focusState = if (isRestoredTarget) restoredFocusState else initialFocusState
                Box(modifier = Modifier.tvPivotItem(isTv, rowState, index)
                    .tvInitialFocusTarget(focusState, isRestoredTarget || (index == 0 && isInitialTarget))) {
                    HomeSeriesShowCard(
                        stream = stream,
                        onClick = { onSeriesSelected(stream) },
                        onLongClick = onLongClick?.let { { it(stream) } },
                        isTv = isTv,
                        badgeLabel = badgeFor?.invoke(stream),
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onMediaFocused(categoryId, stream.seriesId) }
                    )
                }
            }
            if (showSeeAllCard) {
                item(key = "see_all_card") {
                    Box(modifier = Modifier.tvPivotItem(isTv, rowState, CATEGORY_ROW_MAX_ITEMS)) {
                        SeeAllCard(
                            onClick = { onSeeAll?.invoke() }
                        )
                    }
                }
            }
            tvPivotHorizontalEndSpacer(isTv)
        }
    }
}

@Composable
private fun SeeAllCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .width(130.dp)
            .height(195.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, shape)
            .clip(shape)
            .background(Surface3)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AccentLavande,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "VOIR TOUT",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HankenGrotesk,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryFilterChip(
    category: SeriesCategory,
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
