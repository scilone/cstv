package com.cstv.app.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Délai maximal d'attente d'une clé de section pas encore visible
 * (`tvPivotSection`) avant d'abandonner le défilement pour ce focus. Couvre le
 * cas où la cible reçoit le focus avant que Compose n'ait posé son layout
 * (recherche de focus au-delà du viewport courant, `bringIntoView` implicite
 * pas encore résolu) : cf. Review F19, Majeur #2.
 */
private const val SECTION_KEY_RESOLUTION_TIMEOUT_MS = 200L

/**
 * Plusieurs passes couvrent le `bringIntoView` implicite de Compose, qui peut
 * encore déplacer la liste dans les frames suivant l'acquisition du focus.
 */
private const val VERTICAL_PIVOT_MAX_PASSES = 5
private const val VERTICAL_PIVOT_STABLE_PASSES = 2
private const val VERTICAL_PIVOT_TOLERANCE_PX = 0.5f

private class PivotSectionCoordinates(
    var section: LayoutCoordinates? = null,
    var focusedChild: LayoutCoordinates? = null,
    var correctionJob: Job? = null
)

private class PivotCellCoordinates(
    var focusedChild: LayoutCoordinates? = null,
    var correctionJob: Job? = null
)

private class PivotItemCoordinates(
    var focusedChild: LayoutCoordinates? = null,
    var correctionJob: Job? = null
)

/**
 * Rayon par défaut publié à la couche avant du focus (F23) : rayon unifié des
 * cartes de rangée/grille depuis B18 (`HomeVodMovieCard`/`HomeSeriesShowCard`).
 */
private val TV_SELECTOR_DEFAULT_RADIUS = 14.dp

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

/**
 * Pivot horizontal (emplacement de la première vignette) sur une carte d'une [LazyListState].
 * Non-op si `enabled = false`. S'applique en enveloppe autour de la carte
 * (`hasFocus`) : fonctionne que la carte expose ou non son propre paramètre
 * `modifier`, sans dépendre de sa structure interne.
 *
 * Publie sa géométrie stabilisée à la couche avant du focus (F23,
 * [LocalTvFocusSelector]) une fois l'animation de défilement terminée — pas
 * avant, sinon le cadre fixe hériterait de la position transitoire que F23
 * supprime précisément. Utilise [TvFocusSelectorState.reportAxisStabilised]
 * plutôt qu'une publication directe : la rangée porte aussi un `tvPivotSection`
 * ancêtre qui se stabilise verticalement de façon indépendante pour la même
 * acquisition de focus, et publier au premier axe stabilisé réintroduirait un
 * saut (Review F23, Majeur R3). Utilise `onFocusedBoundsChanged` plutôt que
 * `onGloballyPositioned`/`onFocusChanged` pour mesurer les bounds du
 * descendant réellement focalisé plutôt que ceux du `Box` d'enveloppe
 * (Review F23, Majeur R4) : un wrapper de carte « Top 10 » inclut le grand
 * chiffre, plus large que le poster que l'ancien anneau entourait seul.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvPivotItem(
    enabled: Boolean,
    state: LazyListState,
    index: Int,
    selectorCornerRadius: Dp = TV_SELECTOR_DEFAULT_RADIUS
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val selector = LocalTvFocusSelector.current
    val coordinates = remember { PivotItemCoordinates() }
    return this.onFocusedBoundsChanged { focusedCoordinates ->
        if (focusedCoordinates == null) {
            coordinates.focusedChild = null
            coordinates.correctionJob?.cancel()
            coordinates.correctionJob = null
            return@onFocusedBoundsChanged
        }
        val targetChanged = coordinates.focusedChild !== focusedCoordinates
        coordinates.focusedChild = focusedCoordinates
        if (!targetChanged && coordinates.correctionJob?.isActive == true) {
            return@onFocusedBoundsChanged
        }
        coordinates.correctionJob?.cancel()
        coordinates.correctionJob = scope.launch {
            state.animateScrollToPivot(index, TV_PIVOT_HORIZONTAL, 0f)
            if (selector != null && focusedCoordinates.isAttached) {
                selector.reportAxisStabilised(scope, focusedCoordinates, selectorCornerRadius)
            }
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

/** Réserve haute réduite : la première rangée reste sous le bandeau (T12). */
val TV_PIVOT_VERTICAL_START_RESERVE = 24.dp

/**
 * Réserve verticale de début réduite (T12) : la première rangée d'une liste
 * reste proche du bandeau d'en-tête plutôt que centrée au pivot vertical.
 * Conserve la clé `"tv_pivot_vertical_start"` : les positions de défilement
 * persistées ([rememberForeverLazyListState]) référencent un index d'item,
 * que retirer ou renommer cet item décalerait.
 */
fun LazyListScope.tvPivotVerticalStartReserve(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_start") {
        Spacer(
            modifier = Modifier
                .height(0.dp)
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

/**
 * Pivot vertical strict (50 %, centre) sur une cellule d'une [LazyGridState].
 * Le callback de bounds reste actif après le focus initial afin de corriger un
 * éventuel `bringIntoView` tardif de Compose.
 *
 * Publie sa géométrie stabilisée à la couche avant du focus (F23) une fois la
 * convergence verticale terminée.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvPivotCell(
    enabled: Boolean,
    state: LazyGridState,
    index: Int,
    selectorCornerRadius: Dp = TV_SELECTOR_DEFAULT_RADIUS
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val selector = LocalTvFocusSelector.current
    val coordinates = remember { PivotCellCoordinates() }
    return this.onFocusedBoundsChanged { focusedCoordinates ->
        if (focusedCoordinates == null) {
            coordinates.focusedChild = null
            coordinates.correctionJob?.cancel()
            coordinates.correctionJob = null
            return@onFocusedBoundsChanged
        }
        val targetChanged = coordinates.focusedChild !== focusedCoordinates
        coordinates.focusedChild = focusedCoordinates
        if (!targetChanged && coordinates.correctionJob?.isActive == true) {
            return@onFocusedBoundsChanged
        }
        coordinates.correctionJob?.cancel()
        coordinates.correctionJob = scope.launch {
            val stabilised = state.convergeCellToVerticalPivot(
                index = index,
                animatePrimaryCorrection = targetChanged
            )
            if (stabilised) {
                selector?.publishFrom(focusedCoordinates, selectorCornerRadius)
            }
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
 *
 * Publie sa géométrie stabilisée à la couche avant du focus (F23) une fois la
 * convergence verticale terminée, via [TvFocusSelectorState.reportAxisStabilised]
 * (coordination avec l'axe horizontal de `tvPivotItem` — Review F23, Majeur R3).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvPivotSection(
    enabled: Boolean,
    state: LazyListState,
    key: Any,
    selectorCornerRadius: Dp = TV_SELECTOR_DEFAULT_RADIUS
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val selector = LocalTvFocusSelector.current
    val coordinates = remember { PivotSectionCoordinates() }
    return this
        .onGloballyPositioned { coordinates.section = it }
        .onFocusedBoundsChanged { focusedCoordinates ->
            if (focusedCoordinates == null) {
                coordinates.focusedChild = null
                coordinates.correctionJob?.cancel()
                coordinates.correctionJob = null
                return@onFocusedBoundsChanged
            }
            val targetChanged = coordinates.focusedChild !== focusedCoordinates
            coordinates.focusedChild = focusedCoordinates
            val section = coordinates.section ?: return@onFocusedBoundsChanged
            // Tant que la convergence courante est active, ses passes suivantes
            // absorberont les changements de bounds qu'elle provoque elle-même.
            if (!targetChanged && coordinates.correctionJob?.isActive == true) {
                return@onFocusedBoundsChanged
            }
            coordinates.correctionJob?.cancel()
            coordinates.correctionJob = scope.launch {
                val stabilised = state.convergeSectionToVerticalPivot(
                    key = key,
                    section = section,
                    focusedChild = focusedCoordinates,
                    animatePrimaryCorrection = targetChanged
                )
                if (stabilised && selector != null && focusedCoordinates.isAttached) {
                    selector.reportAxisStabilised(scope, focusedCoordinates, selectorCornerRadius)
                }
            }
        }
}

/**
 * Vrai quand le défilement demandé n'a rien consommé alors qu'un écart
 * subsiste : la liste est en butée, la position atteinte est la plus proche
 * possible du pivot et doit être considérée comme stable (T12).
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun isPivotClamped(delta: Float, consumed: Float): Boolean =
    abs(delta) > VERTICAL_PIVOT_TOLERANCE_PX && abs(consumed) <= VERTICAL_PIVOT_TOLERANCE_PX

/**
 * Butée **connue d'avance** : la liste ne peut pas défiler dans la direction
 * que réclame le pivot. C'est le cas courant des premières rangées d'un
 * catalogue, qu'aucun défilement ne peut amener au pivot 50 %.
 *
 * [isPivotClamped] ne constate la butée qu'après coup, une fois
 * `animateScrollBy` terminé : cette animation se déroule alors entièrement
 * sans rien déplacer, et la couche avant du focus (F23) ne publie qu'à son
 * terme — d'où un cadre qui reste plusieurs centaines de millisecondes sur la
 * vignette précédente. Tester la butée avant d'animer supprime ce temps mort.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun isPivotBlocked(delta: Float, canScrollForward: Boolean, canScrollBackward: Boolean): Boolean =
    (delta > VERTICAL_PIVOT_TOLERANCE_PX && !canScrollForward) ||
        (delta < -VERTICAL_PIVOT_TOLERANCE_PX && !canScrollBackward)

/**
 * @return `true` si la convergence a atteint le pivot (cible stabilisée),
 * `false` si les [VERTICAL_PIVOT_MAX_PASSES] passes se sont épuisées sans
 * jamais trouver l'item ou sans se stabiliser — dans ce cas F23 ne doit rien
 * publier à la couche avant plutôt que d'y afficher une position transitoire.
 */
private suspend fun LazyListState.convergeSectionToVerticalPivot(
    key: Any,
    section: LayoutCoordinates,
    focusedChild: LayoutCoordinates,
    animatePrimaryCorrection: Boolean
): Boolean {
    var stablePasses = 0
    var primaryCorrectionPending = animatePrimaryCorrection
    repeat(VERTICAL_PIVOT_MAX_PASSES) {
        val itemInfo = resolveSectionInfo(this, key)
        if (itemInfo != null && section.isAttached && focusedChild.isAttached) {
            val focusedOffset = try {
                section.localPositionOf(focusedChild, Offset.Zero).y
            } catch (e: IllegalArgumentException) {
                return false
            } catch (e: IllegalStateException) {
                return false
            }
            val info = layoutInfo
            val delta = focusedChildPivotDelta(
                viewportStartOffset = info.viewportStartOffset,
                viewportEndOffset = info.viewportEndOffset,
                sectionOffset = itemInfo.offset,
                focusedOffsetInSection = focusedOffset,
                focusedSize = focusedChild.size.height
            )
            if (abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX) {
                stablePasses++
                if (stablePasses >= VERTICAL_PIVOT_STABLE_PASSES) return true
            } else if (isPivotBlocked(delta, canScrollForward, canScrollBackward)) {
                return true
            } else {
                val consumed = if (primaryCorrectionPending) {
                    primaryCorrectionPending = false
                    animateScrollBy(delta)
                } else {
                    scrollBy(delta)
                }
                if (isPivotClamped(delta, consumed)) return true
                stablePasses = 0
            }
        }
        withFrameNanos { }
    }
    return false
}

/** @return voir [convergeSectionToVerticalPivot]. */
private suspend fun LazyGridState.convergeCellToVerticalPivot(
    index: Int,
    animatePrimaryCorrection: Boolean
): Boolean {
    var stablePasses = 0
    var primaryCorrectionPending = animatePrimaryCorrection
    repeat(VERTICAL_PIVOT_MAX_PASSES) {
        val info = layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            val delta = focusedChildPivotDelta(
                viewportStartOffset = info.viewportStartOffset,
                viewportEndOffset = info.viewportEndOffset,
                sectionOffset = itemInfo.offset.y,
                focusedOffsetInSection = 0f,
                focusedSize = itemInfo.size.height
            )
            if (abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX) {
                stablePasses++
                if (stablePasses >= VERTICAL_PIVOT_STABLE_PASSES) return true
            } else if (isPivotBlocked(delta, canScrollForward, canScrollBackward)) {
                return true
            } else {
                val consumed = if (primaryCorrectionPending) {
                    primaryCorrectionPending = false
                    animateScrollBy(delta)
                } else {
                    scrollBy(delta)
                }
                if (isPivotClamped(delta, consumed)) return true
                stablePasses = 0
            }
        }
        withFrameNanos { }
    }
    return false
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
