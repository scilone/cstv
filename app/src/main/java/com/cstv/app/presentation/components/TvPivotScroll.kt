package com.cstv.app.presentation.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
// Une passe entière est désormais consommée par l'animation primaire des
// grilles, qui se déroule sous verrou (B22) : la marge couvre les passes de
// correction et de stabilisation qui la suivent.
private const val VERTICAL_PIVOT_MAX_PASSES = 8
private const val VERTICAL_PIVOT_STABLE_PASSES = 2
private const val VERTICAL_PIVOT_TOLERANCE_PX = 0.5f

private class PivotSectionCoordinates(
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
 * Distance à faire défiler pour ramener la cellule d'index cible sur l'**ancre
 * haute** d'une grille : l'emplacement qu'occupe la première rangée tant que la
 * grille n'a pas défilé, soit l'origine du contenu, juste sous le
 * `contentPadding` de tête (B22).
 *
 * L'ancre haute a remplacé le pivot 50 % qui régnait auparavant : ce pivot est
 * hors d'atteinte pour les premières rangées, qu'aucun défilement ne peut
 * descendre jusqu'au centre du viewport. Le cadre du focus (F23) restait donc
 * en haut sur ces rangées-là, puis sautait au centre à la première rangée
 * capable d'y arriver — au passage 1→2 sur les grilles de posters, 2→3 sur
 * celle des chaînes (cartes plus basses). Ancrer toutes les rangées sur
 * l'emplacement de la première supprime la cause : chacune converge vers la
 * même ordonnée, le cadre ne bouge plus verticalement d'un pixel. La même
 * ancre sert sur l'axe horizontal d'une rangée, où chaque vignette rejoint
 * l'emplacement de la première (voir `convergeItemToStartAnchor`).
 *
 * L'ancre est calculée `viewportStartOffset + beforeContentPadding` plutôt
 * qu'écrite `0` : les deux termes se compensent avec la convention de Compose
 * (`viewportStartOffset == -beforeContentPadding`, offsets d'items comptés
 * depuis l'origine du contenu), et l'expression reste juste si la convention
 * venait à changer.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun topAnchoredPivotDelta(
    viewportStartOffset: Int,
    beforeContentPadding: Int,
    itemOffset: Int
): Float = (itemOffset - (viewportStartOffset + beforeContentPadding)).toFloat()

/**
 * Distance **estimée** pour amener sur l'ancre haute une cellule qui n'est pas
 * encore mesurable (B22).
 *
 * C'est le cas de la remontée : la cellule visée est composée hors du viewport,
 * en réserve de recherche de focus, et n'apparaît donc pas dans
 * `visibleItemsInfo` — aucune position à lire. Comme les grilles TV posent des
 * cellules de hauteur homogène (`GridCells.Fixed`, cartes de taille fixe),
 * compter les lignes d'écart donne une distance juste à quelques pixels près,
 * et les passes suivantes corrigent le reliquat une fois la cellule mesurable.
 *
 * Estimer plutôt qu'attendre est ce qui permet de tenir le verrou de défilement
 * dès la première passe : attendre que Compose rende la cellule visible, c'est
 * précisément lui laisser exécuter le bond sec que ce verrou doit empêcher.
 *
 * Retourne `0` si la grille n'est pas encore mesurée — rien à estimer.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun offscreenRowPivotDelta(
    targetIndex: Int,
    firstVisibleIndex: Int,
    columns: Int,
    rowHeight: Int,
    mainAxisItemSpacing: Int
): Float {
    if (columns <= 0 || rowHeight <= 0) return 0f
    val rowsAway = targetIndex / columns - firstVisibleIndex / columns
    return (rowsAway * (rowHeight + mainAxisItemSpacing)).toFloat()
}

/**
 * Foulée de rattrapage d'une **liste de rangées** dont la rangée visée n'est pas
 * mesurable (B22).
 *
 * Une `LazyColumn` n'expose que des clés, pas d'index pour ses items hors champ :
 * impossible de compter les rangées d'écart comme le fait [offscreenRowPivotDelta]
 * pour une grille. Mais le cas est à sens unique — la rangée active occupant
 * l'ancre haute, tout ce qui la précède est hors champ tandis que la réserve de
 * fin garde visible ce qui la suit : une rangée introuvable est donc au-dessus.
 * Un pas de la hauteur de la première rangée visible, espacement compris, ramène
 * la précédente dans le champ, où sa position réelle prend le relais.
 *
 * Retourne un delta négatif (vers le haut), ou `0` si la liste n'est pas mesurée.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun offscreenSectionStepDelta(
    firstVisibleItemHeight: Int,
    mainAxisItemSpacing: Int
): Float =
    if (firstVisibleItemHeight <= 0) 0f else -(firstVisibleItemHeight + mainAxisItemSpacing).toFloat()

/**
 * Convergence d'une vignette vers l'ancre de **début de rangée**, l'emplacement
 * qu'occupe la première vignette tant que la rangée n'a pas défilé.
 *
 * Transposition exacte de [convergeCellToVerticalPivot] sur l'axe horizontal,
 * verrou compris (B22). L'ancien `animateScrollToItem` s'exécutait à la priorité
 * `Default`, la même que le `bringIntoView` implicite de Compose : partir **à
 * gauche** — vers une vignette hors champ, puisque l'active occupe l'ancre et
 * que tout ce qui la précède est donc sorti par la gauche — faisait annuler
 * notre animation par celle de Compose. L'axe horizontal ne rapportait alors
 * aucune géométrie stabilisée et le cadre était publié sur celle, transitoire,
 * de l'axe vertical, puis corrigé : le « petit effet » qui ne se produisait qu'à
 * gauche. Aller à droite visait au contraire une vignette visible, sans demande
 * implicite ni annulation.
 *
 * @return voir [convergeSectionToVerticalPivot].
 */
private suspend fun LazyListState.convergeItemToStartAnchor(
    index: Int,
    animatePrimaryCorrection: Boolean
): Boolean {
    // Condition d'index explicite (Review F19, Mineur #1) : la liste a pu être
    // rechargée/filtrée entre la résolution de l'index et cet appel.
    if (index < 0 || index >= layoutInfo.totalItemsCount) return false
    var stabilised = false
    scroll(MutatePriority.UserInput) {
        var stablePasses = 0
        var primaryCorrectionPending = animatePrimaryCorrection
        repeat(VERTICAL_PIVOT_MAX_PASSES) {
            val info = layoutInfo
            val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
            val delta = if (itemInfo != null) {
                topAnchoredPivotDelta(
                    viewportStartOffset = info.viewportStartOffset,
                    beforeContentPadding = info.beforeContentPadding,
                    itemOffset = itemInfo.offset
                )
            } else {
                // Vignette hors champ : une rangée est une grille à une colonne.
                offscreenRowPivotDelta(
                    targetIndex = index,
                    firstVisibleIndex = info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                    columns = 1,
                    rowHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0,
                    mainAxisItemSpacing = info.mainAxisItemSpacing
                )
            }
            if (abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX) {
                if (itemInfo != null) {
                    stablePasses++
                    if (stablePasses >= VERTICAL_PIVOT_STABLE_PASSES) {
                        stabilised = true
                        return@scroll
                    }
                }
            } else {
                val consumed = if (primaryCorrectionPending) {
                    primaryCorrectionPending = false
                    animateScrollByInScope(delta)
                } else {
                    scrollBy(delta)
                }
                if (itemInfo != null && isPivotClamped(delta, consumed)) {
                    stabilised = true
                    return@scroll
                }
                stablePasses = 0
            }
            withFrameNanos { }
        }
    }
    return stabilised
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
            selector?.beginAxis()
            try {
                val stabilised = state.convergeItemToStartAnchor(
                    index = index,
                    animatePrimaryCorrection = targetChanged
                )
                if (stabilised && selector != null && focusedCoordinates.isAttached) {
                    selector.reportAxisStabilised(focusedCoordinates, selectorCornerRadius)
                }
            } finally {
                selector?.endAxis()
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

/** Réserve haute réduite : la première rangée reste sous le bandeau (T12). */
val TV_PIVOT_VERTICAL_START_RESERVE = 24.dp

/**
 * Réserve basse des grilles TV, à passer en `contentPadding` bas (B22).
 *
 * L'ancre haute ([topAnchoredPivotDelta]) ne tient sa promesse — un cadre qui
 * ne bouge jamais verticalement — que si la **dernière** rangée peut elle aussi
 * remonter jusqu'à l'ancre. Il lui faut pour cela une hauteur de viewport de
 * vide sous elle ; un demi-viewport, dimensionné pour l'ancien pivot 50 %,
 * bloquerait les dernières rangées à mi-écran et y ramènerait le saut. Une
 * hauteur d'écran entière majore le viewport (amputé des bandeaux système et
 * d'en-tête) dans tous les cas, et ce vide n'est jamais montré au-delà du
 * strict nécessaire : rien n'y est focalisable, donc rien ne l'y fait défiler.
 */
@Composable
fun tvPivotGridEndReserve(): Dp = LocalConfiguration.current.screenHeightDp.dp

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
 * Réserve verticale de fin : une hauteur de viewport entière.
 *
 * Un demi-viewport suffisait au pivot 50 %. Depuis que les rangées rejoignent
 * l'ancre haute (B22), la **dernière** d'entre elles doit pouvoir remonter tout
 * en haut, ce qui demande un viewport de vide sous elle — sans quoi elle
 * resterait bloquée à mi-écran et le cadre du focus y sauterait, exactement le
 * défaut que l'ancre supprime. Rien n'est focalisable dans cette réserve, donc
 * rien ne l'y fait défiler au-delà du nécessaire.
 */
fun LazyListScope.tvPivotVerticalEndSpacer(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_end") {
        Spacer(
            modifier = Modifier
                .fillParentMaxHeight(1f)
                .focusProperties { canFocus = false }
        )
    }
}

/**
 * Ancre haute (B22) sur une cellule d'une [LazyGridState] : chaque rangée
 * rejoint l'emplacement de la première, le cadre du focus ne se déplace donc
 * jamais verticalement dans une grille. Voir [topAnchoredPivotDelta] pour la
 * raison du choix face au pivot 50 % des listes de rangées.
 *
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
            selector?.beginAxis()
            try {
                val stabilised = state.convergeCellToVerticalPivot(
                    index = index,
                    animatePrimaryCorrection = targetChanged
                )
                if (stabilised && selector != null && focusedCoordinates.isAttached) {
                    selector.reportAxisStabilised(focusedCoordinates, selectorCornerRadius)
                }
            } finally {
                selector?.endAxis()
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
        .onFocusedBoundsChanged { focusedCoordinates ->
            if (focusedCoordinates == null) {
                coordinates.focusedChild = null
                coordinates.correctionJob?.cancel()
                coordinates.correctionJob = null
                return@onFocusedBoundsChanged
            }
            val targetChanged = coordinates.focusedChild !== focusedCoordinates
            coordinates.focusedChild = focusedCoordinates
            // Tant que la convergence courante est active, ses passes suivantes
            // absorberont les changements de bounds qu'elle provoque elle-même.
            if (!targetChanged && coordinates.correctionJob?.isActive == true) {
                return@onFocusedBoundsChanged
            }
            coordinates.correctionJob?.cancel()
            coordinates.correctionJob = scope.launch {
                selector?.beginAxis()
                try {
                    val stabilised = state.convergeSectionToVerticalPivot(
                        key = key,
                        animatePrimaryCorrection = targetChanged
                    )
                    if (stabilised && selector != null && focusedCoordinates.isAttached) {
                        selector.reportAxisStabilised(focusedCoordinates, selectorCornerRadius)
                    }
                } finally {
                    selector?.endAxis()
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
 * Convergence d'une rangée de médias vers l'**ancre haute**, comme les cellules
 * de grille (B22) : la rangée active vient occuper l'emplacement de la première,
 * elle n'est plus centrée à 50 %. Le cadre du focus ne se déplace donc plus
 * verticalement, ni ici ni dans les grilles — un seul et même repère fixe sur
 * tous les écrans TV.
 *
 * L'ancrage porte sur l'**item de liste entier**, titre de section compris : les
 * deux vivent dans la même `Column`, sous ce modifier, et arrivent donc
 * ensemble. Ancrer la vignette elle-même aurait poussé son titre hors de l'écran.
 * Les bandeaux de titre étant de hauteur identique d'une section à l'autre, la
 * vignette retombe toujours à la même ordonnée.
 *
 * Comme pour les grilles, toute la convergence se déroule sous un `scroll`
 * de priorité [MutatePriority.UserInput], que le défilement implicite de Compose
 * (`bringIntoView`, priorité [MutatePriority.Default]) ne peut ni devancer ni
 * préempter. Une rangée voisine n'est ici que **partiellement** visible, ce qui
 * suffit à déclencher la demande implicite : le déplacement était sinon avalé
 * d'un bond sec dans les deux sens, là où les grilles ne le subissaient qu'à la
 * remontée.
 *
 * La résolution de la rangée reste **hors** du verrou : tant qu'elle n'est pas
 * posée, c'est précisément le défilement implicite qui la rendra mesurable, et
 * le verrou ferait expirer l'attente pour rien.
 *
 * @return `true` si la convergence a atteint le pivot (cible stabilisée),
 * `false` si les [VERTICAL_PIVOT_MAX_PASSES] passes se sont épuisées sans
 * jamais trouver l'item ou sans se stabiliser — dans ce cas F23 ne doit rien
 * publier à la couche avant plutôt que d'y afficher une position transitoire.
 */
private suspend fun LazyListState.convergeSectionToVerticalPivot(
    key: Any,
    animatePrimaryCorrection: Boolean
): Boolean {
    val resolvedBeforeScroll = resolveSectionInfo(this, key) != null
    var stabilised = false
    var catchUpAvailable = !resolvedBeforeScroll
    scroll(MutatePriority.UserInput) {
        var stablePasses = 0
        var primaryCorrectionPending = animatePrimaryCorrection
        repeat(VERTICAL_PIVOT_MAX_PASSES) {
            val info = layoutInfo
            val itemInfo = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (itemInfo != null) {
                val delta = topAnchoredPivotDelta(
                    viewportStartOffset = info.viewportStartOffset,
                    beforeContentPadding = info.beforeContentPadding,
                    itemOffset = itemInfo.offset
                )
                if (abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX) {
                    stablePasses++
                    if (stablePasses >= VERTICAL_PIVOT_STABLE_PASSES) {
                        stabilised = true
                        return@scroll
                    }
                } else {
                    val consumed = if (primaryCorrectionPending) {
                        primaryCorrectionPending = false
                        animateScrollByInScope(delta)
                    } else {
                        scrollBy(delta)
                    }
                    if (isPivotClamped(delta, consumed)) {
                        stabilised = true
                        return@scroll
                    }
                    stablePasses = 0
                }
            } else if (catchUpAvailable) {
                // Rangée visée introuvable : elle est au-dessus du viewport.
                // C'est la remontée, et c'est structurel — la rangée active
                // occupant l'ancre, tout ce qui la précède est hors champ.
                // Abandonner ici laissait la liste immobile alors que le focus,
                // lui, était bien parti au-dessus : les appuis suivants le
                // faisaient monter à l'aveugle et il ne réapparaissait que
                // plusieurs rangées plus haut (B22). Une foulée, une seule,
                // ramène la rangée précédente dans le champ ; la convergence
                // exacte prend ensuite le relais sur sa position mesurée.
                catchUpAvailable = false
                val step = offscreenSectionStepDelta(
                    firstVisibleItemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0,
                    mainAxisItemSpacing = info.mainAxisItemSpacing
                )
                if (step != 0f) {
                    primaryCorrectionPending = false
                    animateScrollByInScope(step)
                }
            }
            withFrameNanos { }
        }
    }
    return stabilised
}

/**
 * Convergence d'une cellule de grille vers l'ancre haute.
 *
 * Toute la convergence se déroule sous un `scroll` de priorité
 * [MutatePriority.UserInput] (B22). Le défilement implicite de Compose
 * (`bringIntoView`, déclenché par l'arrivée du focus, priorité
 * [MutatePriority.Default]) ne peut alors ni s'exécuter avant elle, ni la
 * préempter en cours de route. Sans ce verrou, la **remontée** d'une ligne était
 * avalée d'un bond sec par Compose — la cellule visée n'y est composée qu'en
 * réserve de recherche de focus, hors du viewport, donc systématiquement objet
 * d'un `bringIntoView` — et il ne restait à notre animation qu'un résidu de la
 * hauteur de la réserve haute, imperceptible. La descente, elle, visait une
 * cellule déjà visible : aucune demande implicite, notre animation faisait tout
 * le trajet. D'où une descente glissée et une remontée sèche. Le verrou rend les
 * deux sens identiques.
 *
 * @return voir [convergeSectionToVerticalPivot].
 */
private suspend fun LazyGridState.convergeCellToVerticalPivot(
    index: Int,
    animatePrimaryCorrection: Boolean
): Boolean {
    var stabilised = false
    scroll(MutatePriority.UserInput) {
        var stablePasses = 0
        var primaryCorrectionPending = animatePrimaryCorrection
        repeat(VERTICAL_PIVOT_MAX_PASSES) {
            val info = layoutInfo
            val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
            val delta = if (itemInfo != null) {
                topAnchoredPivotDelta(
                    viewportStartOffset = info.viewportStartOffset,
                    beforeContentPadding = info.beforeContentPadding,
                    itemOffset = itemInfo.offset.y
                )
            } else {
                offscreenRowPivotDelta(
                    targetIndex = index,
                    firstVisibleIndex = info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                    columns = info.visibleItemsInfo.maxOfOrNull { it.column + 1 } ?: 0,
                    rowHeight = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 0,
                    mainAxisItemSpacing = info.mainAxisItemSpacing
                )
            }
            if (abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX) {
                // Une cellule hors viewport dont l'estimation ne donne rien
                // (grille pas encore mesurée) n'est pas une convergence :
                // seule une position réellement mesurée peut stabiliser.
                if (itemInfo != null) {
                    stablePasses++
                    if (stablePasses >= VERTICAL_PIVOT_STABLE_PASSES) {
                        stabilised = true
                        return@scroll
                    }
                }
            } else {
                val consumed = if (primaryCorrectionPending) {
                    primaryCorrectionPending = false
                    animateScrollByInScope(delta)
                } else {
                    scrollBy(delta)
                }
                // La butée ne conclut que sur une position mesurée : hors
                // viewport, l'estimation peut simplement être fausse.
                if (itemInfo != null && isPivotClamped(delta, consumed)) {
                    stabilised = true
                    return@scroll
                }
                stablePasses = 0
            }
            withFrameNanos { }
        }
    }
    return stabilised
}

/**
 * Équivalent de `animateScrollBy` **à l'intérieur** d'un [ScrollScope] déjà
 * ouvert : le verrou de défilement est tenu par l'appelant, qui l'a pris à une
 * priorité choisie. Même ressort par défaut que `animateScrollBy`, donc même
 * ressenti qu'avant (B22).
 *
 * @return la distance réellement consommée, pour [isPivotClamped].
 */
private suspend fun ScrollScope.animateScrollByInScope(delta: Float): Float {
    var consumed = 0f
    animate(initialValue = 0f, targetValue = delta) { current, _ ->
        consumed += scrollBy(current - consumed)
    }
    return consumed
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
