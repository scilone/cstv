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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cstv.app.di.IptvLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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

/**
 * Foulées de rattrapage au maximum pour ramener dans le champ une rangée visée
 * hors viewport (voir [convergeSectionToVerticalPivot]). Deux suffisent : la
 * première avance d'une hauteur de rangée estimée, la seconde rattrape l'écart
 * si les deux rangées n'avaient pas la même hauteur. **Sans borne**, une rangée
 * lente à se composer en déclenche une par passe et la liste part plusieurs
 * rangées trop haut d'un coup.
 */
private const val OFFSCREEN_CATCH_UP_MAX_STEPS = 2

private class PivotSectionCoordinates(
    /** Repère du décalage de la vignette dans sa rangée (mesure relative, stable). */
    var section: LayoutCoordinates? = null,
    var focusedChild: LayoutCoordinates? = null,
    var correctionJob: Job? = null
)

private class PivotCellCoordinates(
    var cell: LayoutCoordinates? = null,
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
 * Défilement qu'il reste à faire pour amener l'item [index] sur l'ancre de début
 * (axe horizontal d'une rangée, axe vertical d'une grille).
 *
 * Sert à **prédire** la position d'arrivée du cadre du focus avant tout
 * défilement (B22) : position d'arrivée = position actuelle − ce delta. Retourne
 * `null` quand l'item n'est pas mesurable, auquel cas rien n'est prédit et la
 * publication de fin de convergence fait foi.
 */
private fun LazyListState.startAnchorDelta(index: Int): Float? {
    val info = layoutInfo
    val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    return topAnchoredPivotDelta(
        viewportStartOffset = info.viewportStartOffset,
        beforeContentPadding = info.beforeContentPadding,
        itemOffset = itemInfo.offset
    )
}

/**
 * Géométrie du conteneur défilant (la `LazyColumn` ou la `LazyVerticalGrid`
 * elle-même), publiée par [tvPivotViewport] et lue par les pivots.
 *
 * C'est la pièce qui rend l'ancre **déterministe** (B22). Toutes les tentatives
 * précédentes calculaient la place du cadre à partir de la vignette : sa
 * position mesurée après convergence, puis une prédiction fondée sur cette même
 * position. Or une vignette bouge, se compose hors champ, et n'est mesurable
 * qu'à des instants dictés par Compose — d'où une succession de défauts de
 * synchronisation qu'aucun correctif ponctuel n'épuisait.
 *
 * Le conteneur, lui, ne bouge pas. Combiné à des mesures **relatives** —
 * décalage de la vignette dans son item, taille de la vignette — que le
 * défilement ne change pas davantage, il donne la position d'arrivée sans jamais
 * consulter où la vignette se trouve à cet instant.
 */
@Stable
class TvPivotViewportState {
    var coordinates: LayoutCoordinates? = null
        internal set
}

@Composable
fun rememberTvPivotViewport(): TvPivotViewportState = remember { TvPivotViewportState() }

/** À poser sur le conteneur défilant lui-même (`LazyColumn`, `LazyVerticalGrid`). */
fun Modifier.tvPivotViewport(state: TvPivotViewportState): Modifier =
    onGloballyPositioned { state.coordinates = it }

/** Conteneur vertical englobant, fourni aux pivots qui s'y ancrent. */
val LocalTvPivotViewport = staticCompositionLocalOf<TvPivotViewportState?> { null }

/**
 * Ordonnée, en coordonnées racine, à laquelle la vignette focalisée s'arrêtera.
 *
 * Ne dépend que de grandeurs stables : position racine du conteneur, réserve de
 * tête, hauteur du viewport, et — mesurés relativement à l'item qui les porte,
 * donc insensibles au défilement — le décalage de la vignette dans son item et
 * sa hauteur. Aucune position absolue de vignette n'intervient.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun anchoredRootTop(
    viewportRootTop: Float,
    viewportHeight: Int,
    beforeContentPadding: Int,
    focusedOffsetInItem: Float,
    focusedHeight: Int,
    anchor: TvPivotAnchor
): Float = when (anchor) {
    TvPivotAnchor.Top -> viewportRootTop + beforeContentPadding + focusedOffsetInItem
    TvPivotAnchor.MidViewport -> viewportRootTop + viewportHeight * TV_PIVOT_VERTICAL_FRACTION
}

private const val TV_PIVOT_LOG_TAG = "TvPivot"

/**
 * Trace un ancrage déterministe qui n'aboutit pas, et renvoie le `null` attendu
 * par l'appelant (B22).
 *
 * Sans cette trace, un ancrage muet est **invisible** : le cadre retombe
 * silencieusement sur la publication mesurée de fin de convergence et n'affiche
 * son défaut que sous forme d'un retard perceptible. C'est exactement ce qui est
 * resté caché sur l'Accueil jusqu'à ce que la v1.73.18 retire ce filet et révèle
 * le trou — [LocalTvPivotViewport] n'y était jamais fourni. Une régression du
 * même genre (nouvel écran qui oublie le `CompositionLocalProvider`, conteneur
 * pas encore mesuré) se voit désormais dans le logcat.
 */
private fun logMissingAnchor(source: Any, reason: String): Float? {
    IptvLog.w(TV_PIVOT_LOG_TAG, "ancre verticale indisponible ($source) : $reason")
    return null
}

/**
 * Ordonnée d'arrivée du cadre pour une rangée, ou `null` si le conteneur n'est
 * pas encore mesuré. Voir [anchoredRootTop] : rien ici ne dépend de la position
 * courante de la vignette.
 *
 * [item] doit être le nœud de l'**item de liste entier** : le décalage de la
 * vignette est mesuré depuis lui, et l'ancre le suppose posé à l'origine du
 * contenu. Poser `tvPivotSection` derrière un `padding` dans la chaîne de
 * modificateurs le fait mesurer depuis le contenu **rembourré**, l'ancre est
 * alors trop haute de ce rembourrage — voir la KDoc de [tvPivotSection].
 *
 * `positionInRoot` et non `boundsInRoot` : cette dernière **clippe** par les
 * parents, elle renverrait donc une origine fausse pour un conteneur
 * partiellement masqué.
 */
private fun LazyListState.anchoredTopFor(
    viewport: TvPivotViewportState?,
    item: LayoutCoordinates,
    focusedChild: LayoutCoordinates,
    anchor: TvPivotAnchor,
    source: Any
): Float? {
    val viewportCoordinates = viewport?.coordinates?.takeIf { it.isAttached }
        ?: return logMissingAnchor(
            source,
            if (viewport == null) "LocalTvPivotViewport non fourni" else "conteneur non mesuré"
        )
    if (!item.isAttached || !focusedChild.isAttached) {
        return logMissingAnchor(source, "coordonnées détachées")
    }
    val offsetInItem = try {
        item.localPositionOf(focusedChild, Offset.Zero).y
    } catch (e: IllegalArgumentException) {
        return logMissingAnchor(source, "repère commun introuvable")
    } catch (e: IllegalStateException) {
        return logMissingAnchor(source, "repère commun introuvable")
    }
    return anchoredRootTop(
        viewportRootTop = viewportCoordinates.positionInRoot().y,
        viewportHeight = viewportCoordinates.size.height,
        beforeContentPadding = layoutInfo.beforeContentPadding,
        focusedOffsetInItem = offsetInItem,
        focusedHeight = focusedChild.size.height,
        anchor = anchor
    )
}

/** Voir [anchoredTopFor] : même rôle pour une cellule de grille, toujours en ancre haute. */
private fun LazyGridState.anchoredTopFor(
    viewport: TvPivotViewportState?,
    cell: LayoutCoordinates,
    focusedChild: LayoutCoordinates,
    source: Any
): Float? {
    val viewportCoordinates = viewport?.coordinates?.takeIf { it.isAttached }
        ?: return logMissingAnchor(
            source,
            if (viewport == null) "LocalTvPivotViewport non fourni" else "conteneur non mesuré"
        )
    if (!cell.isAttached || !focusedChild.isAttached) {
        return logMissingAnchor(source, "coordonnées détachées")
    }
    val offsetInCell = try {
        cell.localPositionOf(focusedChild, Offset.Zero).y
    } catch (e: IllegalArgumentException) {
        return logMissingAnchor(source, "repère commun introuvable")
    } catch (e: IllegalStateException) {
        return logMissingAnchor(source, "repère commun introuvable")
    }
    return anchoredRootTop(
        viewportRootTop = viewportCoordinates.positionInRoot().y,
        viewportHeight = viewportCoordinates.size.height,
        beforeContentPadding = layoutInfo.beforeContentPadding,
        focusedOffsetInItem = offsetInCell,
        focusedHeight = focusedChild.size.height,
        anchor = TvPivotAnchor.Top
    )
}

/** Où une rangée vient se poser verticalement dans son viewport. */
enum class TvPivotAnchor {
    /**
     * Emplacement de la première rangée, sous la réserve de tête. Le cadre du
     * focus ne bouge alors jamais d'une rangée à l'autre — à condition que les
     * cartes aient la même taille, faute de quoi seul son format change.
     */
    Top,

    /**
     * Mi-hauteur du viewport, sur l'Accueil (B22). Comme [Top], c'est le **bord
     * haut** de la vignette qui s'y pose, jamais son centre : centrer la
     * vignette y ferait dépendre le haut du cadre de la hauteur de la carte, et
     * le cadre bougerait donc à chaque changement de format de rangée — Hero
     * vers « Continuer à regarder », entrée et sortie de « TV en direct ».
     * Ancrer le bord haut supprime la variable ; le cadre est aussi rigoureusement
     * fixe qu'en [Top].
     *
     * Reste distinct de [Top] par la **hauteur** de l'ancre : à mi-écran, les
     * rangées voisines restent partiellement visibles de part et d'autre, ce dont
     * l'Accueil a besoin — c'est le seul écran dont les rangées mêlent des
     * formats très différents, et l'ancre haute y accumulait les à-coups.
     */
    MidViewport
}

/** Hauteur du viewport à laquelle se pose le bord haut de la vignette en [TvPivotAnchor.MidViewport]. */
private const val TV_PIVOT_VERTICAL_FRACTION = 0.5f

/**
 * Distance à faire défiler pour poser le **bord haut** du descendant focalisé
 * sur le pivot vertical du viewport ([TvPivotAnchor.MidViewport]).
 *
 * `childFraction` vaut 0 : c'est le bord haut qui s'aligne, pas le centre — la
 * même promesse que [anchoredRootTop], sans quoi défilement et cadre viseraient
 * deux endroits différents.
 *
 * Le calcul part de la position réellement mesurée du focus **dans** son item
 * de section : le titre placé au-dessus d'une rangée ne décale donc pas sa
 * vignette.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun focusedChildPivotDelta(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    sectionOffset: Int,
    focusedOffsetInSection: Float,
    focusedSize: Int,
    parentFraction: Float = TV_PIVOT_VERTICAL_FRACTION,
    childFraction: Float = 0f
): Float {
    val viewportSize = viewportEndOffset - viewportStartOffset
    if (viewportSize <= 0) return 0f
    val pivot = viewportStartOffset + viewportSize * parentFraction
    val focusedAnchor = sectionOffset + focusedOffsetInSection + focusedSize * childFraction
    return focusedAnchor - pivot
}

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
        selector?.beginAxis(focusedCoordinates, TvPivotAxis.HORIZONTAL, selectorCornerRadius)
        // Position d'arrivée posée tout de suite, sans attendre le défilement.
        //
        // `positionInRoot` et non `boundsInRoot` : cette dernière **clippe** par
        // les parents. Une vignette composée hors du viewport — cas structurel de
        // la remontée, la rangée visée étant au-dessus du champ — y renvoie donc
        // un rectangle vide, d'abscisse 0. Le cadre était prédit tout à gauche de
        // l'écran, puis remis en place par la publication mesurée : le saut
        // « vers la gauche puis retour » qui ne se produisait qu'en remontant,
        // jamais en descendant (la réserve de fin garde la rangée visible, donc
        // non clippée). `positionInRoot` ne clippe pas et donne la position réelle
        // d'un nœud posé hors champ (B22).
        //
        // `delta` nul (vignette absente de `visibleItemsInfo`, cas du déplacement
        // vers la gauche à l'intérieur d'une rangée) n'est pas un défaut : toutes
        // les vignettes d'une rangée rejoignent la même ancre horizontale, donc
        // l'abscisse en place est déjà la bonne et il n'y a rien à prédire.
        state.startAnchorDelta(index)?.let { delta ->
            if (focusedCoordinates.isAttached) {
                selector?.predictAxis(
                    key = focusedCoordinates,
                    axis = TvPivotAxis.HORIZONTAL,
                    anchoredOffset = focusedCoordinates.positionInRoot().x - delta,
                    cornerRadius = selectorCornerRadius
                )
            }
        }
        coordinates.correctionJob?.cancel()
        coordinates.correctionJob = scope.launch {
            val stabilised = state.convergeItemToStartAnchor(
                index = index,
                animatePrimaryCorrection = targetChanged
            )
            if (stabilised && selector != null && focusedCoordinates.isAttached) {
                selector.reportAxisStabilised(focusedCoordinates, TvPivotAxis.HORIZONTAL, focusedCoordinates, selectorCornerRadius)
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
 * Réserve verticale de début d'un demi-viewport, indispensable au pivot
 * [TvPivotAnchor.MidViewport] : sans elle, les premières rangées ne peuvent pas
 * descendre jusqu'au centre et le cadre du focus y sauterait à la première qui y
 * parvient. Conserve la clé `"tv_pivot_vertical_start"` de son homologue réduite
 * ([tvPivotVerticalStartReserve]) : les positions de défilement persistées
 * référencent un index d'item, que retirer ou renommer cet item décalerait.
 */
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
    val viewport = LocalTvPivotViewport.current
    val coordinates = remember { PivotCellCoordinates() }
    return this
        // Repère du décalage de la vignette dans sa cellule : mesure relative,
        // que le défilement ne change pas.
        .onGloballyPositioned { coordinates.cell = it }
        .onFocusedBoundsChanged { focusedCoordinates ->
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
        // Grille : un seul axe (VERTICAL), pas d'ambiguïté de nommage — aucun
        // pivot horizontal ne concourt pour cette même cible (B22).
        selector?.beginAxis(focusedCoordinates, TvPivotAxis.VERTICAL, selectorCornerRadius)
        // Ancre déterministe, voir tvPivotSection.
        val cell = coordinates.cell
        if (selector != null && cell != null) {
            state.anchoredTopFor(viewport, cell, focusedCoordinates, index)?.let { top ->
                selector.predictAxis(
                    key = focusedCoordinates,
                    axis = TvPivotAxis.VERTICAL,
                    anchoredOffset = top,
                    cornerRadius = selectorCornerRadius
                )
            }
        }
        coordinates.correctionJob?.cancel()
        coordinates.correctionJob = scope.launch {
            val stabilised = state.convergeCellToVerticalPivot(
                index = index,
                animatePrimaryCorrection = targetChanged
            )
            if (stabilised && selector != null && focusedCoordinates.isAttached) {
                selector.reportAxisStabilised(
                    focusedCoordinates, TvPivotAxis.VERTICAL, focusedCoordinates, selectorCornerRadius,
                    // Grille : seul endroit où le cadre change réellement de
                    // place sans que rien ne défile (B22).
                    glideHorizontally = true
                )
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
 * fiable.
 *
 * Publie sa géométrie stabilisée à la couche avant du focus (F23) une fois la
 * convergence verticale terminée, via [TvFocusSelectorState.reportAxisStabilised]
 * (coordination avec l'axe horizontal de `tvPivotItem` — Review F23, Majeur R3).
 *
 * **À appliquer en tête de la chaîne de modificateurs de l'item de liste**, comme
 * le fait déjà `tvPivotCell` sur une cellule de grille (B22). Le modifier mesure
 * le décalage de la vignette *dans son item* pour en déduire l'ordonnée d'arrivée
 * du cadre, tandis que la convergence, elle, amène l'**item de liste** sur
 * l'ancre : les deux ne parlent du même repère que si le nœud observé est bien
 * l'item entier. Placé derrière un `padding`, il mesure depuis le contenu
 * rembourré et prédit une ordonnée trop haute de ce rembourrage — le cadre
 * montait alors de quelques pixels à chaque déplacement (y compris purement
 * horizontal) et redescendait à la fin du défilement, quand la publication
 * mesurée reprenait la main. Écrire :
 *
 * ```
 * Modifier
 *     .tvPivotSection(isTv, listState, categoryId)
 *     .fillMaxWidth()
 *     .padding(vertical = 4.dp)
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvPivotSection(
    enabled: Boolean,
    state: LazyListState,
    key: Any,
    selectorCornerRadius: Dp = TV_SELECTOR_DEFAULT_RADIUS,
    anchor: TvPivotAnchor = TvPivotAnchor.Top
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val selector = LocalTvFocusSelector.current
    val viewport = LocalTvPivotViewport.current
    val coordinates = remember { PivotSectionCoordinates() }
    return this
        // La section sert de repère au décalage de la vignette en son sein :
        // mesure relative, que le défilement ne change pas.
        .onGloballyPositioned { coordinates.section = it }
        .onFocusedBoundsChanged { focusedCoordinates ->
            if (focusedCoordinates == null) {
                coordinates.focusedChild = null
                coordinates.correctionJob?.cancel()
                coordinates.correctionJob = null
                return@onFocusedBoundsChanged
            }
            // Le focus venait-il déjà de cette rangée ? Alors le déplacement est
            // **horizontal** et l'ordonnée du cadre est déjà la bonne : la
            // re-prédire n'apporterait rien et exposerait à une mesure prise sur
            // une vignette que Compose vient de composer hors champ, pas encore
            // posée à sa place — d'où un cadre qui se téléportait furtivement
            // d'une rangée vers le haut à chaque cran vers la gauche (B22).
            val targetChanged = coordinates.focusedChild !== focusedCoordinates
            coordinates.focusedChild = focusedCoordinates
            // Tant que la convergence courante est active, ses passes suivantes
            // absorberont les changements de bounds qu'elle provoque elle-même.
            if (!targetChanged && coordinates.correctionJob?.isActive == true) {
                return@onFocusedBoundsChanged
            }
            val section = coordinates.section
            if (section == null) return@onFocusedBoundsChanged
            if (selector != null) {
                selector.beginAxis(focusedCoordinates, TvPivotAxis.VERTICAL, selectorCornerRadius)
                // Ancre déterministe : ne consulte jamais où la vignette se trouve à
                // cet instant, seulement des grandeurs que le défilement ne change
                // pas (B22).
                state.anchoredTopFor(viewport, section, focusedCoordinates, anchor, key)?.let { top ->
                    selector.predictAxis(
                        key = focusedCoordinates,
                        axis = TvPivotAxis.VERTICAL,
                        anchoredOffset = top,
                        cornerRadius = selectorCornerRadius
                    )
                }
            }
            coordinates.correctionJob?.cancel()
            coordinates.correctionJob = scope.launch {
                val stabilised = state.convergeSectionToVerticalPivot(
                    key = key,
                    animatePrimaryCorrection = targetChanged,
                    anchor = anchor,
                    section = section,
                    focusedChild = focusedCoordinates
                )
                if (stabilised && selector != null && focusedCoordinates.isAttached) {
                    selector.reportAxisStabilised(focusedCoordinates, TvPivotAxis.VERTICAL, focusedCoordinates, selectorCornerRadius)
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
 * Convergence d'une rangée de médias vers son ancre verticale, [TvPivotAnchor.Top]
 * par défaut, [TvPivotAnchor.MidViewport] sur l'Accueil.
 *
 * En [TvPivotAnchor.Top] l'ancrage porte sur l'**item de liste entier**, titre de
 * section compris : les deux vivent dans la même `Column`, sous ce modifier, et
 * arrivent donc ensemble. Ancrer la vignette elle-même aurait poussé son titre
 * hors de l'écran. En [TvPivotAnchor.MidViewport] c'est au contraire la **vignette**
 * qui s'aligne, sur le centre du viewport : le titre a de la place au-dessus
 * d'elle sans qu'il faille en tenir compte.
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
 * La foulée de rattrapage ([offscreenSectionStepDelta]) se ré-estime à
 * **chaque** passe où la rangée reste introuvable, plutôt qu'une seule fois
 * (limite corrigée en B22) : la première version, à usage unique, lisait la
 * hauteur de la rangée de **départ** pour estimer la distance jusqu'à la
 * cible — juste quand les deux rangées ont la même hauteur, mais faux dès
 * qu'elles diffèrent (rangée « Top N » plus haute que la rangée de
 * recommandations en dessous, vignette de chaîne favorite plus large que sa
 * voisine…). Une foulée trop courte laisse la rangée hors champ, une passe de
 * plus la rattrape avec une mesure fraîche. Le nombre de foulées est **borné**
 * par [OFFSCREEN_CATCH_UP_MAX_STEPS] : sans cette borne, une rangée qui tarde à
 * se composer déclenche une foulée à chaque passe et la liste part plusieurs
 * rangées trop haut d'un coup (régression v1.73.11).
 *
 * @return `true` si la convergence a atteint le pivot (cible stabilisée),
 * `false` si les [VERTICAL_PIVOT_MAX_PASSES] passes se sont épuisées sans
 * jamais trouver l'item ou sans se stabiliser — dans ce cas F23 ne doit rien
 * publier à la couche avant plutôt que d'y afficher une position transitoire.
 */
private suspend fun LazyListState.convergeSectionToVerticalPivot(
    key: Any,
    animatePrimaryCorrection: Boolean,
    anchor: TvPivotAnchor,
    section: LayoutCoordinates?,
    focusedChild: LayoutCoordinates
): Boolean {
    var stabilised = false
    scroll(MutatePriority.UserInput) {
        var stablePasses = 0
        var catchUpBudget = OFFSCREEN_CATCH_UP_MAX_STEPS
        var primaryCorrectionPending = animatePrimaryCorrection
        repeat(VERTICAL_PIVOT_MAX_PASSES) {
            val info = layoutInfo
            val itemInfo = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (itemInfo != null) {
                val delta = if (anchor == TvPivotAnchor.MidViewport) {
                    if (section == null || !section.isAttached || !focusedChild.isAttached) {
                        return@scroll
                    }
                    val focusedOffset = try {
                        section.localPositionOf(focusedChild, Offset.Zero).y
                    } catch (e: IllegalArgumentException) {
                        return@scroll
                    } catch (e: IllegalStateException) {
                        return@scroll
                    }
                    focusedChildPivotDelta(
                        viewportStartOffset = info.viewportStartOffset,
                        viewportEndOffset = info.viewportEndOffset,
                        sectionOffset = itemInfo.offset,
                        focusedOffsetInSection = focusedOffset,
                        focusedSize = focusedChild.size.height
                    )
                } else {
                    topAnchoredPivotDelta(
                        viewportStartOffset = info.viewportStartOffset,
                        beforeContentPadding = info.beforeContentPadding,
                        itemOffset = itemInfo.offset
                    )
                }
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
            } else if (catchUpBudget > 0) {
                // Rangée visée introuvable : elle est au-dessus du viewport.
                // C'est la remontée, et c'est structurel — la rangée active
                // occupant l'ancre, tout ce qui la précède est hors champ.
                catchUpBudget--
                val step = offscreenSectionStepDelta(
                    firstVisibleItemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0,
                    mainAxisItemSpacing = info.mainAxisItemSpacing
                )
                if (step != 0f) {
                    if (primaryCorrectionPending) {
                        primaryCorrectionPending = false
                        animateScrollByInScope(step)
                    } else {
                        scrollBy(step)
                    }
                }
                stablePasses = 0
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

