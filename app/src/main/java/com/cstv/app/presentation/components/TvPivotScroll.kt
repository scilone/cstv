package com.cstv.app.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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

private class PivotSectionCoordinates(
    var section: LayoutCoordinates? = null,
    var focusedChild: LayoutCoordinates? = null
)

/**
 * La carte active reste ancrée sur l'emplacement initial de la première
 * vignette de la rangée. Le `contentPadding` propre à chaque rangée reste donc
 * respecté, sans décalage entre les index 0 et 1.
 */
const val TV_PIVOT_HORIZONTAL = 0f

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

/**
 * Distance à faire défiler pour aligner le centre du descendant focalisé sur
 * le pivot vertical du viewport. Contrairement à [pivotScrollOffset], le calcul
 * part de la position réellement mesurée du focus dans son item de section :
 * le titre placé au-dessus d'une rangée ne décale donc plus sa vignette.
 */
internal fun focusedChildPivotDelta(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    sectionOffset: Int,
    focusedOffsetInSection: Float,
    focusedSize: Int,
    parentFraction: Float = TV_PIVOT_VERTICAL,
    childFraction: Float = 0.5f
): Float {
    val viewportSize = viewportEndOffset - viewportStartOffset
    if (viewportSize <= 0) return 0f
    val pivot = viewportStartOffset + viewportSize * parentFraction
    val focusedAnchor = sectionOffset + focusedOffsetInSection + focusedSize * childFraction
    return focusedAnchor - pivot
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
 * Pivot horizontal (emplacement de la première vignette) sur une carte d'une [LazyListState].
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

/**
 * Réserve non focalisable placée après les médias d'une rangée TV. Sa largeur
 * d'un viewport autorise aussi le dernier média à rejoindre l'emplacement du
 * premier sans être bloqué par la fin du contenu.
 */
fun LazyListScope.tvPivotHorizontalEndSpacer(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_horizontal_end") {
        Spacer(
            modifier = Modifier
                .fillParentMaxWidth()
                .focusProperties { canFocus = false }
        )
    }
}

/** Réserve verticale de début pour les listes TV sans Hero Card. */
fun LazyListScope.tvPivotVerticalStartSpacer(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_start") {
        Spacer(
            modifier = Modifier
                .fillParentMaxHeight(0.5f)
                .focusProperties { canFocus = false }
        )
    }
}

/**
 * Réserve verticale de fin. Une demi-hauteur de viewport suffit pour centrer
 * le descendant focalisé du dernier item, quelle que soit sa position dans la
 * section.
 */
fun LazyListScope.tvPivotVerticalEndSpacer(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_end") {
        Spacer(
            modifier = Modifier
                .fillParentMaxHeight(0.5f)
                .focusProperties { canFocus = false }
        )
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
 * Pivot vertical (50 %, centre du descendant focalisé) sur une rangée/section
 * d'une [LazyListState]. Non-op si `enabled = false`. Résout l'item par [key]
 * au moment du focus (`layoutInfo.visibleItemsInfo`) plutôt que de figer son
 * index à la composition :
 * les sections d'une `LazyColumn` conditionnelle (ex. Accueil) changent
 * d'index quand une section apparaît ou disparaît, la clé stable reste seule
 * fiable. Aucune cible trouvée pour la clé après [SECTION_KEY_RESOLUTION_TIMEOUT_MS] → no-op.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvPivotSection(enabled: Boolean, state: LazyListState, key: Any): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val coordinates = remember { PivotSectionCoordinates() }
    return this
        .onGloballyPositioned { coordinates.section = it }
        .onFocusedBoundsChanged { focusedCoordinates ->
            if (focusedCoordinates == null) {
                coordinates.focusedChild = null
                return@onFocusedBoundsChanged
            }
            // Le callback est aussi notifié lorsque le même nœud se déplace
            // pendant le scroll. Ne lancer qu'un recentrage par changement de
            // cible évite une cascade d'animations concurrentes.
            if (coordinates.focusedChild === focusedCoordinates) return@onFocusedBoundsChanged
            coordinates.focusedChild = focusedCoordinates
            val section = coordinates.section ?: return@onFocusedBoundsChanged
            scope.launch {
                val itemInfo = resolveSectionInfo(state, key) ?: return@launch
                if (!section.isAttached || !focusedCoordinates.isAttached) return@launch
                val focusedOffset = try {
                    section.localPositionOf(focusedCoordinates, Offset.Zero).y
                } catch (e: IllegalArgumentException) {
                    return@launch
                } catch (e: IllegalStateException) {
                    return@launch
                }
                val layoutInfo = state.layoutInfo
                val delta = focusedChildPivotDelta(
                    viewportStartOffset = layoutInfo.viewportStartOffset,
                    viewportEndOffset = layoutInfo.viewportEndOffset,
                    sectionOffset = itemInfo.offset,
                    focusedOffsetInSection = focusedOffset,
                    focusedSize = focusedCoordinates.size.height
                )
                try {
                    state.animateScrollBy(delta)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // La section a pu disparaître pendant un rechargement. Le
                    // prochain focus recalculera sa position depuis le layout.
                }
            }
        }
}

/**
 * Résout l'item de la rangée portant [key] dans [state]. La cible reçoit
 * parfois le focus avant que Compose n'ait posé le layout qui la rend visible
 * (recherche de focus au-delà du viewport, `bringIntoView` implicite pas
 * encore résolu) : `visibleItemsInfo` peut alors ne pas encore la contenir au
 * moment exact de l'appel (Review F19, Majeur #2). On retente donc sur les
 * layouts suivants avant d'abandonner.
 */
private suspend fun resolveSectionInfo(
    state: LazyListState,
    key: Any
): androidx.compose.foundation.lazy.LazyListItemInfo? {
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { return it }
    return withTimeoutOrNull(SECTION_KEY_RESOLUTION_TIMEOUT_MS) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo }
            .mapNotNull { visibleItems -> visibleItems.firstOrNull { it.key == key } }
            .first()
    }
}
