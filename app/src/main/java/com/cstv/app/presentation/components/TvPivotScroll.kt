package com.cstv.app.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Délai maximal d'attente d'une clé de section pas encore visible
 * (`tvPivotSection`) avant d'abandonner le défilement pour ce focus. Couvre le
 * cas où la cible reçoit le focus avant que Compose n'ait posé son layout
 * (recherche de focus au-delà du viewport courant, `bringIntoView` implicite
 * pas encore résolu) : cf. Review F19, Majeur #2.
 */
private const val SECTION_KEY_RESOLUTION_TIMEOUT_MS = 200L

/** Fraction du viewport où la carte active reste ancrée horizontalement (bord gauche). */
const val TV_PIVOT_HORIZONTAL = 0.15f

/** Fraction du viewport où la rangée/cellule active reste ancrée verticalement (centre). */
const val TV_PIVOT_VERTICAL = 0.5f

/**
 * Calcule le `scrollOffset` (négatif dans le cas courant) à passer à
 * `animateScrollToItem` pour que l'élément d'index cible s'arrête au pivot
 * `parentFraction` du viewport, son propre point `childFraction` aligné dessus.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun pivotScrollOffset(
    viewportSize: Int,
    itemSize: Int,
    parentFraction: Float,
    childFraction: Float
): Int {
    if (viewportSize <= 0) return 0
    return -(viewportSize * parentFraction - itemSize * childFraction).roundToInt()
}

suspend fun LazyListState.animateScrollToPivot(
    index: Int,
    parentFraction: Float,
    childFraction: Float
) {
    // Condition d'index explicite (Review F19, Mineur #1) : la liste a pu être
    // rechargée/filtrée entre la résolution de l'index et cet appel.
    if (index < 0 || index >= layoutInfo.totalItemsCount) return
    val info = layoutInfo
    val viewportSize = info.viewportEndOffset - info.viewportStartOffset
    val itemSize = info.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
    val offset = pivotScrollOffset(viewportSize, itemSize, parentFraction, childFraction)
    try {
        animateScrollToItem(index, offset)
    } catch (e: CancellationException) {
        // Annulation structurée (écran quitté, `rememberCoroutineScope` annulé) :
        // ne jamais l'avaler.
        throw e
    } catch (e: Exception) {
        // Défaut transitoire imprévu par la condition d'index ci-dessus
        // (course avec une recomposition concurrente) : le prochain appui
        // D-pad redéclenche le pivot, aucune action corrective nécessaire ici.
    }
}

suspend fun LazyGridState.animateScrollToPivot(
    index: Int,
    parentFraction: Float,
    childFraction: Float
) {
    if (index < 0 || index >= layoutInfo.totalItemsCount) return
    val info = layoutInfo
    val viewportSize = info.viewportEndOffset - info.viewportStartOffset
    val itemSize = info.visibleItemsInfo.firstOrNull { it.index == index }?.size?.height ?: 0
    val offset = pivotScrollOffset(viewportSize, itemSize, parentFraction, childFraction)
    try {
        animateScrollToItem(index, offset)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Idem : liste/grille rechargée entre résolution d'index et appel.
    }
}

/**
 * Pivot horizontal (15 %, bord gauche) sur une carte d'une [LazyListState].
 * Non-op si `enabled = false`. S'applique en enveloppe autour de la carte
 * (`hasFocus`) : fonctionne que la carte expose ou non son propre paramètre
 * `modifier`, sans dépendre de sa structure interne.
 */
@Composable
fun Modifier.tvPivotItem(enabled: Boolean, state: LazyListState, index: Int): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    return this.onFocusChanged { focusState: FocusState ->
        if (focusState.hasFocus) {
            scope.launch { state.animateScrollToPivot(index, TV_PIVOT_HORIZONTAL, 0f) }
        }
    }
}

/** Pivot vertical (50 %, centre) sur une cellule d'une [LazyGridState]. Non-op si `enabled = false`. */
@Composable
fun Modifier.tvPivotCell(enabled: Boolean, state: LazyGridState, index: Int): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    return this.onFocusChanged { focusState: FocusState ->
        if (focusState.hasFocus) {
            scope.launch { state.animateScrollToPivot(index, TV_PIVOT_VERTICAL, 0.5f) }
        }
    }
}

/**
 * Pivot vertical (50 %, centre) sur une rangée/section d'une [LazyListState].
 * Non-op si `enabled = false`. Résout l'index par [key] au moment du focus
 * (`layoutInfo.visibleItemsInfo`) plutôt que de le figer à la composition :
 * les sections d'une `LazyColumn` conditionnelle (ex. Accueil) changent
 * d'index quand une section apparaît ou disparaît, la clé stable reste seule
 * fiable. Aucune cible trouvée pour la clé après [SECTION_KEY_RESOLUTION_TIMEOUT_MS] → no-op.
 */
@Composable
fun Modifier.tvPivotSection(enabled: Boolean, state: LazyListState, key: Any): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    return this.onFocusChanged { focusState: FocusState ->
        if (focusState.hasFocus) {
            scope.launch {
                val index = resolveSectionIndex(state, key) ?: return@launch
                state.animateScrollToPivot(index, TV_PIVOT_VERTICAL, 0.5f)
            }
        }
    }
}

/**
 * Résout l'index de la rangée portant [key] dans [state]. La cible reçoit
 * parfois le focus avant que Compose n'ait posé le layout qui la rend visible
 * (recherche de focus au-delà du viewport, `bringIntoView` implicite pas
 * encore résolu) : `visibleItemsInfo` peut alors ne pas encore la contenir au
 * moment exact de l'appel (Review F19, Majeur #2). On retente donc sur les
 * layouts suivants avant d'abandonner.
 */
private suspend fun resolveSectionIndex(state: LazyListState, key: Any): Int? {
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { return it.index }
    return withTimeoutOrNull(SECTION_KEY_RESOLUTION_TIMEOUT_MS) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .mapNotNull { visibleItems -> visibleItems.firstOrNull { it.key == key }?.index }
            .first()
    }
}
