package com.cstv.app.presentation.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Largeur d'affiche des grilles de catalogue TV, identique aux rangées « Tout ». */
val TV_CATALOG_GRID_CARD_WIDTH = 130.dp

/** Écart visible entre deux affiches, identique aux rangées « Tout ». */
private val TV_CATALOG_GRID_GAP = 12.dp

/** Marge latérale du contenu, comptée deux fois dans la largeur disponible. */
private val TV_CATALOG_GRID_HORIZONTAL_PADDING = 12.dp

/**
 * Grille verticale d'affiches d'un catalogue TV : la mise en page partagée par
 * une catégorie filtrée (Films, Séries) et par la vue « Voir tout » de la
 * recherche. Un seul composant, donc un seul rendu — c'est la raison d'être de
 * cette extraction : les trois écrans en portaient jusqu'ici trois copies, que
 * rien n'obligeait à rester d'accord.
 *
 * Le nombre de colonnes se déduit de la largeur disponible plutôt que d'être
 * écrit en dur, à raison d'une affiche de [TV_CATALOG_GRID_CARD_WIDTH] et d'un
 * écart de [TV_CATALOG_GRID_GAP] — six colonnes sur un écran 1080p. `Adaptive`
 * ne conviendrait pas : il répartit le reliquat de largeur dans les cellules, et
 * des affiches de taille fixe y semblent alors plus espacées qu'en rangée.
 *
 * Porte aussi le conteneur du pivot ([tvPivotViewport]) et les réserves haute et
 * basse dont l'ancre du sélecteur a besoin (B22) : à ce titre, l'appelant n'a
 * plus qu'à fournir ses cellules, chacune marquée par [Modifier.tvPivotCell].
 */
@Composable
fun TvCatalogGrid(
    state: LazyGridState,
    pivotViewport: TvPivotViewportState,
    modifier: Modifier = Modifier,
    onFocusLost: () -> Unit = {},
    content: LazyGridScope.() -> Unit
) {
    val endReserve: Dp = tvPivotGridEndReserve()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = (maxWidth - TV_CATALOG_GRID_HORIZONTAL_PADDING * 2)
            .coerceAtLeast(TV_CATALOG_GRID_CARD_WIDTH)
        val columns = ((availableWidth + TV_CATALOG_GRID_GAP) /
            (TV_CATALOG_GRID_CARD_WIDTH + TV_CATALOG_GRID_GAP)).roundToInt().coerceAtLeast(1)
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(TV_CATALOG_GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(TV_CATALOG_GRID_GAP),
            contentPadding = PaddingValues(
                start = TV_CATALOG_GRID_HORIZONTAL_PADDING,
                end = TV_CATALOG_GRID_HORIZONTAL_PADDING,
                top = TV_PIVOT_VERTICAL_START_RESERVE,
                bottom = endReserve
            ),
            modifier = Modifier.fillMaxSize()
                .focusGroup()
                .tvPivotViewport(pivotViewport)
                .onFocusChanged { if (!it.hasFocus) onFocusLost() },
            content = content
        )
    }
}

/** Colonnes de la grille de chaînes : cartes paysage, larges. */
private const val TV_CHANNEL_GRID_COLUMNS = 3

/** Écart des cartes de chaîne, plus généreux que celui des affiches. */
private val TV_CHANNEL_GRID_GAP = 16.dp

/**
 * Grille verticale de cartes de chaîne : le pendant de [TvCatalogGrid] pour le
 * Live TV, partagé par la catégorie filtrée et par la vue « Voir tout » de la
 * recherche.
 *
 * Les cartes étant paysage et pleine largeur de cellule, le nombre de colonnes
 * est fixe plutôt que déduit d'une largeur d'affiche.
 */
@Composable
fun TvChannelGrid(
    state: LazyGridState,
    pivotViewport: TvPivotViewportState,
    modifier: Modifier = Modifier,
    onFocusLost: () -> Unit = {},
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(TV_CHANNEL_GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(TV_CHANNEL_GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(TV_CHANNEL_GRID_GAP),
        contentPadding = PaddingValues(
            top = TV_PIVOT_VERTICAL_START_RESERVE,
            bottom = tvPivotGridEndReserve()
        ),
        modifier = modifier.fillMaxSize()
            .focusGroup()
            .tvPivotViewport(pivotViewport)
            .onFocusChanged { if (!it.hasFocus) onFocusLost() },
        content = content
    )
}
