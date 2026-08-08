package com.cstv.app.presentation.search
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.cstv.app.presentation.components.TvCatalogGrid
import com.cstv.app.presentation.components.TvSeeAllCard
import com.cstv.app.presentation.components.TvPivotViewportState
import com.cstv.app.presentation.home.components.HomeSeriesShowCard
import com.cstv.app.presentation.home.components.HomeVodMovieCard
import com.cstv.app.presentation.livetv.components.StreamTvCard
import androidx.compose.foundation.lazy.items
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.components.tvPivotItem
import com.cstv.app.presentation.components.TvChannelGrid
import com.cstv.app.presentation.components.tvPivotCell
import com.cstv.app.presentation.components.tvPivotSection
import com.cstv.app.presentation.components.tvPivotHorizontalEndSpacer
import com.cstv.app.presentation.components.tvPivotVerticalEndSpacer
import com.cstv.app.presentation.components.tvPivotVerticalStartReserve
import com.cstv.app.presentation.components.TV_PIVOT_VERTICAL_START_RESERVE
import com.cstv.app.presentation.components.tvPivotGridEndReserve
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.LocalTvPivotViewport
import com.cstv.app.presentation.components.rememberTvPivotViewport
import com.cstv.app.presentation.components.tvPivotViewport
import com.cstv.app.presentation.components.TvFocusSelectorOverlay
import com.cstv.app.presentation.components.TvFocusSelectorState
import com.cstv.app.presentation.components.tvFocusHighlight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.presentation.components.ActiveFilterChipsRow
import com.cstv.app.presentation.favorites.FavoritesViewModel
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3

// Type de média dont on affiche la liste complète (grille verticale) après
// un clic sur "Voir tout". null = vue combinée (rangées horizontales).
private enum class SearchExpandedType { LIVE, VOD, SERIES }

/**
 * Plafond d'une rangée de résultats. Au-delà, la rangée se termine par une carte
 * « Voir tout » qui ouvre la grille complète — même dispositif que les rangées
 * de catalogue (`CategorySectionRow`). Parcourir des centaines de vignettes au
 * D-pad n'a pas de sens ; la grille, elle, se parcourt en deux dimensions.
 */
private const val SEARCH_ROW_MAX_ITEMS = 30

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
    val playbackSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.playbackError) {
        state.playbackError?.let { message ->
            playbackSnackbarHostState.showSnackbar(message)
            viewModel.consumePlaybackError()
        }
    }

    // Vue "Voir tout" : on ne montre qu'un type de média en grille verticale.
    var expandedType by remember { mutableStateOf<SearchExpandedType?>(null) }

    // Toute nouvelle saisie recasse la vue développée pour revenir aux rangées.
    LaunchedEffect(state.searchQuery) { expandedType = null }

    // La vue développée se referme sur la touche Retour avant que celle-ci ne
    // quitte l'écran : sur TV, c'est le seul chemin de retour depuis qu'il n'y
    // a plus de bouton dédié.
    BackHandler(enabled = expandedType != null) { expandedType = null }

    // Couche avant du sélecteur pivot fixe (F23), comme sur les catalogues :
    // sans elle, la recherche globale gardait un cadre peint par chaque
    // vignette, au comportement différent du reste de l'application.
    val tvFocusSelector = remember { TvFocusSelectorState() }
    val pivotViewport = rememberTvPivotViewport()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header & Search Field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                // Pas de bouton retour sur TV : la touche Retour de la
                // télécommande remplit déjà ce rôle.
                if (!isTv) {
                    IconButton(
                        onClick = { if (expandedType != null) expandedType = null else onBack() },
                        modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Même style que CategorySearchField (CatalogFilterComponents.kt) :
                // homogène avec le filtrage des catégories TV/Films/Séries.
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            stringResource(R.string.search_placeholder),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = AccentLavande,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (isTv) null else {
                        {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_clear),
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface3,
                        unfocusedContainerColor = Surface3,
                        focusedBorderColor = Color.White.copy(alpha = 0.12f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AccentLavande
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Bouton "Recherche avancée" : ouvre la bottom sheet de filtres.
                // État actif (fond lavande plein) quand au moins un filtre est appliqué.
                val filterActive = state.advancedFilter.isActive
                IconButton(
                    onClick = { viewModel.setFilterSheetOpen(true) },
                    modifier = Modifier.background(
                        color = if (filterActive) AccentLavande else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.search_advanced_open),
                        tint = if (filterActive) Color(0xFF17131F) else Color.White
                    )
                }
            }

            // Chips de filtres actifs supprimables (×), sous la barre de recherche.
            if (state.advancedFilter.isActive) {
                ActiveFilterChipsRow(
                    filter = state.advancedFilter,
                    availableCategories = state.availableCategories,
                    isTv = isTv,
                    onRemoveMediaType = { viewModel.removeMediaTypeFilter() },
                    onRemoveCategory = { viewModel.removeCategoryFilter() },
                    onRemoveMinRating = { viewModel.removeMinRatingFilter() },
                    onRemoveYearRange = { viewModel.removeYearRangeFilter() },
                    onRemoveGenre = { viewModel.removeGenreFilter(it) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Filtres seuls autorisés : on n'affiche l'invite initiale que si aucun
            // texte n'est saisi ET aucun filtre avancé n'est actif.
            if (state.searchQuery.trim().isBlank() && !state.advancedFilter.isActive && !state.hasBrowsedAll) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_initial_prompt), color = Color.Gray, fontSize = 14.sp)
                }
            } else if (state.isSearching) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.searchResult.isEmpty) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results, state.searchQuery), color = Color.Gray, fontSize = 14.sp)
                }
            } else if (expandedType != null) {
                // --- Vue développée : un seul type, grille verticale ---
                CompositionLocalProvider(LocalTvFocusSelector provides if (isTv) tvFocusSelector else null) {
                    SearchExpandedGrid(
                        type = expandedType!!,
                        result = state.searchResult,
                        isTv = isTv,
                        favorites = state.favorites,
                        onToggleFavorite = { stream ->
                            viewModel.toggleFavorite(
                                id = stream.streamId,
                                type = "live",
                                name = stream.name,
                                cover = stream.streamIcon,
                                categoryId = stream.categoryId.orEmpty()
                            )
                        },
                        onPlayLive = onPlayLive,
                        onSelectMovie = onSelectMovie,
                        onSelectSeries = onSelectSeries,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            } else {
                // --- Vue combinée : rangées horizontales par type ---
                val combinedListState = rememberLazyListState()
                // Voir HomeScreen : sans `LocalTvPivotViewport`, aucune ancre
                // déterministe n'est calculable et le cadre attend la fin du
                // défilement (B22).
                CompositionLocalProvider(
                    LocalTvFocusSelector provides if (isTv) tvFocusSelector else null,
                    LocalTvPivotViewport provides pivotViewport
                ) {
                LazyColumn(
                    state = combinedListState,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .tvPivotViewport(pivotViewport)
                    .onFocusChanged { if (!it.hasFocus) tvFocusSelector.clear() }
                ) {
                    // Réserve haute réduite (T12), comme sur Live TV, Films et
                    // Séries : la première rangée reste sous le bandeau au lieu
                    // de démarrer au milieu de l'écran.
                    tvPivotVerticalStartReserve(isTv)
                    // 1. Live TV Results Row
                    if (state.searchResult.liveResults.isNotEmpty()) {
                        item(key = "search_live") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvPivotSection(isTv, combinedListState, "search_live")
                            ) {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_channels),
                                    count = state.searchResult.liveResults.size,
                                    isTv = isTv,
                                    onSeeAll = { expandedType = SearchExpandedType.LIVE }
                                )
                                val rowState = rememberLazyListState()
                                LazyRow(
                                    state = rowState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    itemsIndexed(state.searchResult.liveResults.take(SEARCH_ROW_MAX_ITEMS)) { index, stream ->
                                        Box(modifier = Modifier.tvPivotItem(isTv, rowState, index, selectorCornerRadius = 12.dp)) {
                                            SearchCardItem(
                                                name = stream.name,
                                                cover = stream.streamIcon,
                                                isLive = true,
                                                onClick = { onPlayLive(stream) }
                                            )
                                        }
                                    }
                                    if (state.searchResult.liveResults.size > SEARCH_ROW_MAX_ITEMS) {
                                        item(key = "search_live_see_all") {
                                            Box(modifier = Modifier.tvPivotItem(isTv, rowState, SEARCH_ROW_MAX_ITEMS, selectorCornerRadius = 12.dp)) {
                                                TvSeeAllCard(
                                                    onClick = { expandedType = SearchExpandedType.LIVE },
                                                    modifier = Modifier.width(120.dp).height(80.dp),
                                                    cornerRadius = 12.dp
                                                )
                                            }
                                        }
                                    }
                                    tvPivotHorizontalEndSpacer(isTv)
                                }
                            }
                        }
                    }

                    // 2. VOD Movie Results Row
                    if (state.searchResult.vodResults.isNotEmpty()) {
                        item(key = "search_vod") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvPivotSection(isTv, combinedListState, "search_vod")
                            ) {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_movies),
                                    count = state.searchResult.vodResults.size,
                                    isTv = isTv,
                                    onSeeAll = { expandedType = SearchExpandedType.VOD }
                                )
                                val rowState = rememberLazyListState()
                                LazyRow(
                                    state = rowState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    itemsIndexed(state.searchResult.vodResults.take(SEARCH_ROW_MAX_ITEMS)) { index, stream ->
                                        Box(modifier = Modifier.tvPivotItem(isTv, rowState, index, selectorCornerRadius = 12.dp)) {
                                            SearchCardItem(
                                                name = stream.name,
                                                cover = stream.streamIcon,
                                                isLive = false,
                                                rating = stream.rating,
                                                onClick = { onSelectMovie(stream) }
                                            )
                                        }
                                    }
                                    if (state.searchResult.vodResults.size > SEARCH_ROW_MAX_ITEMS) {
                                        item(key = "search_vod_see_all") {
                                            Box(modifier = Modifier.tvPivotItem(isTv, rowState, SEARCH_ROW_MAX_ITEMS, selectorCornerRadius = 12.dp)) {
                                                TvSeeAllCard(
                                                    onClick = { expandedType = SearchExpandedType.VOD },
                                                    modifier = Modifier.width(110.dp).aspectRatio(2f / 3f),
                                                    cornerRadius = 12.dp
                                                )
                                            }
                                        }
                                    }
                                    tvPivotHorizontalEndSpacer(isTv)
                                }
                            }
                        }
                    }

                    // 3. Series Results Row
                    if (state.searchResult.seriesResults.isNotEmpty()) {
                        item(key = "search_series") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvPivotSection(isTv, combinedListState, "search_series")
                            ) {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_series),
                                    count = state.searchResult.seriesResults.size,
                                    isTv = isTv,
                                    onSeeAll = { expandedType = SearchExpandedType.SERIES }
                                )
                                val rowState = rememberLazyListState()
                                LazyRow(
                                    state = rowState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup()
                                ) {
                                    itemsIndexed(state.searchResult.seriesResults.take(SEARCH_ROW_MAX_ITEMS)) { index, stream ->
                                        Box(modifier = Modifier.tvPivotItem(isTv, rowState, index, selectorCornerRadius = 12.dp)) {
                                            SearchCardItem(
                                                name = stream.name,
                                                cover = stream.cover,
                                                isLive = false,
                                                rating = stream.rating,
                                                onClick = { onSelectSeries(stream) }
                                            )
                                        }
                                    }
                                    if (state.searchResult.seriesResults.size > SEARCH_ROW_MAX_ITEMS) {
                                        item(key = "search_series_see_all") {
                                            Box(modifier = Modifier.tvPivotItem(isTv, rowState, SEARCH_ROW_MAX_ITEMS, selectorCornerRadius = 12.dp)) {
                                                TvSeeAllCard(
                                                    onClick = { expandedType = SearchExpandedType.SERIES },
                                                    modifier = Modifier.width(110.dp).aspectRatio(2f / 3f),
                                                    cornerRadius = 12.dp
                                                )
                                            }
                                        }
                                    }
                                    tvPivotHorizontalEndSpacer(isTv)
                                }
                            }
                        }
                    }
                    tvPivotVerticalEndSpacer(isTv)
                }
                }
            }
        }

        if (isTv) {
            TvFocusSelectorOverlay(tvFocusSelector, modifier = Modifier.fillMaxSize())
        }

        if (state.isFilterSheetOpen) {
            AdvancedSearchSheet(
                filter = state.advancedFilter,
                availableGenres = state.availableGenres,
                availableCategories = state.availableCategories,
                resultCount = state.filteredResultCount,
                catalogYearRange = state.catalogYearRange,
                isTv = isTv,
                onMediaTypeSelected = { viewModel.setMediaType(it) },
                onCategorySelected = { viewModel.setCategory(it) },
                onMinRatingSelected = { viewModel.setMinRating(it) },
                onYearRangeChanged = { viewModel.setYearRange(it) },
                onGenreToggled = { viewModel.toggleGenre(it) },
                onReset = { viewModel.resetFilter() },
                onApply = { viewModel.applyFilter() },
                onDismiss = { viewModel.setFilterSheetOpen(false) }
            )
        }
        SnackbarHost(
            hostState = playbackSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    isTv: Boolean,
    onSeeAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 4.dp, end = 4.dp)
    ) {
        // Titre + nombre d'occurrences en gris juste à côté (façon maquette).
        Text(
            text = title,
            fontFamily = BricolageGrotesque,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.01).sp,
            color = Color(0xFFF4F4F7)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($count)",
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        // « Voir tout » : mobile seulement, même traitement que la Home
        // (HomeScreen.kt, HomeSectionRow) et que les rangées de catalogue
        // (CategorySectionRow). Sur TV ces liens sont focusables et précèdent
        // les vignettes : ils captaient le focus à l'arrivée sur l'écran, et la
        // rangée se parcourt de toute façon entièrement au D-pad.
        if (!isTv) {
            Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun SearchExpandedGrid(
    type: SearchExpandedType,
    result: com.cstv.app.domain.model.SearchResult,
    isTv: Boolean,
    favorites: List<com.cstv.app.domain.model.FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    onPlayLive: (LiveStream) -> Unit,
    onSelectMovie: (VodStream) -> Unit,
    onSelectSeries: (SeriesStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = when (type) {
        SearchExpandedType.LIVE -> result.liveResults.size
        SearchExpandedType.VOD -> result.vodResults.size
        SearchExpandedType.SERIES -> result.seriesResults.size
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.search_see_all, count),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        val gridState = rememberLazyGridState()
        val tvFocusSelector = LocalTvFocusSelector.current
        // Conteneur de l'ancre déterministe, comme sur les grilles de catégorie
        // des catalogues : sans lui, `tvPivotCell` ne prédit rien (B22).
        val pivotViewport = rememberTvPivotViewport()
        CompositionLocalProvider(LocalTvPivotViewport provides pivotViewport) {
            if (isTv) {
                SearchExpandedGridTv(
                    type = type,
                    result = result,
                    gridState = gridState,
                    pivotViewport = pivotViewport,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    onPlayLive = onPlayLive,
                    onSelectMovie = onSelectMovie,
                    onSelectSeries = onSelectSeries,
                    onFocusLost = { tvFocusSelector?.clear() }
                )
            } else {
                SearchExpandedGridMobile(
                    type = type,
                    result = result,
                    gridState = gridState,
                    onPlayLive = onPlayLive,
                    onSelectMovie = onSelectMovie,
                    onSelectSeries = onSelectSeries
                )
            }
        }
    }
}

/**
 * Vue « Voir tout » sur TV : rigoureusement le rendu d'une catégorie filtrée.
 *
 * Films et séries passent par [TvCatalogGrid] et par les cartes d'affiche des
 * catalogues ([HomeVodMovieCard], [HomeSeriesShowCard]) — même largeur de
 * cellule, mêmes écarts, mêmes réserves, donc six affiches par ligne sur un
 * écran 1080p. Les chaînes reprennent la géométrie de la grille Live TV (trois
 * colonnes, cartes paysage) et sa carte [StreamTvCard].
 *
 * Seule différence assumée avec la catégorie Live TV : la recherche n'a pas de
 * source EPG, la ligne de programme en cours reste donc vide (`epgProgram` nul,
 * pas de rafraîchissement demandé).
 */
@Composable
private fun SearchExpandedGridTv(
    type: SearchExpandedType,
    result: com.cstv.app.domain.model.SearchResult,
    gridState: LazyGridState,
    pivotViewport: TvPivotViewportState,
    favorites: List<com.cstv.app.domain.model.FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    onPlayLive: (LiveStream) -> Unit,
    onSelectMovie: (VodStream) -> Unit,
    onSelectSeries: (SeriesStream) -> Unit,
    onFocusLost: () -> Unit
) {
    // La carte « Voir tout » qui ouvre cette vue disparaît avec les rangées :
    // sans cible explicite, le focus n'a plus de nœud où aller et la
    // télécommande reste sans effet. Même dispositif que les catalogues.
    val initialFocus = rememberTvInitialFocus(
        isTv = true,
        ready = when (type) {
            SearchExpandedType.LIVE -> result.liveResults.isNotEmpty()
            SearchExpandedType.VOD -> result.vodResults.isNotEmpty()
            SearchExpandedType.SERIES -> result.seriesResults.isNotEmpty()
        },
        targetKey = type
    )
    if (type == SearchExpandedType.LIVE) {
        TvChannelGrid(
            state = gridState,
            pivotViewport = pivotViewport,
            onFocusLost = onFocusLost
        ) {
            gridItemsIndexed(result.liveResults) { index, stream ->
                Box(
                    // Rayon 12.dp : StreamTvCard n'a pas été unifiée au rayon
                    // 14.dp de B18, comme dans la catégorie Live TV.
                    modifier = Modifier.tvPivotCell(true, gridState, index, selectorCornerRadius = 12.dp)
                        .tvInitialFocusTarget(initialFocus, index == 0),
                    // Cf. VodScreen.kt (Review F19, Majeur #1) : force la
                    // propagation de la contrainte min de la cellule à l'enfant.
                    propagateMinConstraints = true
                ) {
                    StreamTvCard(
                        stream = stream,
                        isFavorite = favorites.any { it.id == stream.streamId && it.type == "live" },
                        epgProgram = null,
                        onLoadEpg = null,
                        onToggleFavorite = { onToggleFavorite(stream) },
                        onClick = { onPlayLive(stream) }
                    )
                }
            }
        }
        return
    }

    TvCatalogGrid(
        state = gridState,
        pivotViewport = pivotViewport,
        onFocusLost = onFocusLost
    ) {
        when (type) {
            SearchExpandedType.VOD -> gridItemsIndexed(result.vodResults) { index, stream ->
                Box(
                    modifier = Modifier.tvPivotCell(true, gridState, index)
                        .tvInitialFocusTarget(initialFocus, index == 0),
                    contentAlignment = Alignment.Center
                ) {
                    HomeVodMovieCard(
                        stream = stream,
                        onClick = { onSelectMovie(stream) },
                        isTv = true,
                        fillCell = false
                    )
                }
            }
            SearchExpandedType.SERIES -> gridItemsIndexed(result.seriesResults) { index, stream ->
                Box(
                    modifier = Modifier.tvPivotCell(true, gridState, index)
                        .tvInitialFocusTarget(initialFocus, index == 0),
                    contentAlignment = Alignment.Center
                ) {
                    HomeSeriesShowCard(
                        stream = stream,
                        onClick = { onSelectSeries(stream) },
                        isTv = true,
                        fillCell = false
                    )
                }
            }
            SearchExpandedType.LIVE -> Unit // traité au-dessus
        }
    }
}

/** Vue « Voir tout » sur mobile : grille tactile, inchangée. */
@Composable
private fun SearchExpandedGridMobile(
    type: SearchExpandedType,
    result: com.cstv.app.domain.model.SearchResult,
    gridState: LazyGridState,
    onPlayLive: (LiveStream) -> Unit,
    onSelectMovie: (VodStream) -> Unit,
    onSelectSeries: (SeriesStream) -> Unit
) {
    // Chaînes = vignette paysage -> 2 colonnes ; posters -> 3 colonnes.
    val columns = if (type == SearchExpandedType.LIVE) 2 else 3
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        when (type) {
            SearchExpandedType.LIVE -> gridItemsIndexed(result.liveResults) { _, stream ->
                Box(propagateMinConstraints = true) {
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.streamIcon,
                        isLive = true,
                        onClick = { onPlayLive(stream) }
                    )
                }
            }
            SearchExpandedType.VOD -> gridItemsIndexed(result.vodResults) { _, stream ->
                Box(propagateMinConstraints = true) {
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.streamIcon,
                        isLive = false,
                        rating = stream.rating,
                        onClick = { onSelectMovie(stream) }
                    )
                }
            }
            SearchExpandedType.SERIES -> gridItemsIndexed(result.seriesResults) { _, stream ->
                Box(propagateMinConstraints = true) {
                    SearchGridCard(
                        name = stream.name,
                        cover = stream.cover,
                        isLive = false,
                        rating = stream.rating,
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
    rating: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLive) Modifier.aspectRatio(16f / 9f) else Modifier.aspectRatio(2f / 3f)
            )
            .onFocusChanged { isFocused = it.isFocused }
            // Marquage commun : la vignette ne peint plus son propre anneau
            // quand la couche avant du sélecteur pivot est active (TV).
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Surface1),
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
        if (!isLive) {
            SearchCardRatingBadge(rating = rating, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun SearchCardItem(
    name: String,
    cover: String?,
    isLive: Boolean,
    rating: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(if (isLive) 120.dp else 110.dp)
            .then(
                if (isLive) Modifier.height(80.dp) else Modifier.aspectRatio(2f / 3f)
            )
            .onFocusChanged { isFocused = it.isFocused }
            // Marquage commun : la vignette ne peint plus son propre anneau
            // quand la couche avant du sélecteur pivot est active (TV).
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Surface1),
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
        if (!isLive) {
            SearchCardRatingBadge(rating = rating, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * Badge de note en overlay coin supérieur droit de la vignette, iso reste de
 * l'app (HomeCards.HomeVodMovieCard/HomeSeriesShowCard, VodScreen grille
 * catégorie) : pas de ligne méta sous l'image, juste ce badge sur le poster.
 */
@Composable
private fun SearchCardRatingBadge(rating: String?, modifier: Modifier = Modifier) {
    val cleanRating = rating?.trim()
    if (cleanRating.isNullOrBlank() || cleanRating == "0" || cleanRating == "0.0") return

    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(cleanRating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

