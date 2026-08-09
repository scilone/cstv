package com.cstv.app.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Entrée de focus déterministe dans une rangée horizontale TV.
 *
 * Sans elle, c'est la recherche de focus bidimensionnelle de Compose qui choisit
 * la vignette d'arrivée quand le focus descend (ou monte) dans une rangée : elle
 * retient la candidate la plus proche **latéralement** de l'élément de départ.
 * Tant que ce dernier est une vignette de rangée, posée sur l'ancre de début
 * (voir `tvPivotItem`), la candidate est celle qui occupe déjà cette ancre et
 * rien ne défile. Mais dès que l'élément de départ est **large** — la Hero Card
 * de l'Accueil, qui occupe toute la largeur, ou le sélecteur de catégorie du
 * mode « Tout » — son centre tombe au milieu de l'écran et la candidate retenue
 * est une vignette du milieu de la rangée. Le pivot horizontal la ramène alors
 * sur l'ancre de début : la rangée défile toute seule vers la droite à chaque
 * passage Hero → rangée suivante.
 *
 * La rangée impose donc sa propre cible d'entrée : la vignette posée sur l'ancre
 * de début, c'est-à-dire le premier item visible. C'est aussi celle sur laquelle
 * le focus reposait la dernière fois que la rangée a été quittée — le pivot l'y
 * ayant laissée —, donc la restauration naturelle du focus est préservée sans
 * `focusRestorer`, dont l'`enter` annule la recherche dès qu'une vignette sauvée
 * a été recyclée (voir les rangées de `VodScreen`/`SeriesScreen`).
 *
 * Usage — deux points de pose, sur la rangée et sur ses items :
 *
 * ```
 * val rowEntry = rememberTvRowFocusEntry()
 * LazyRow(state = rowState, modifier = Modifier.tvRowFocusEntry(isTv, rowEntry).focusGroup()) {
 *     itemsIndexed(items) { index, item ->
 *         Box(modifier = Modifier.tvPivotItem(isTv, rowState, index)
 *             .tvRowFocusEntryTarget(isTv, rowEntry, rowState, index)) { ... }
 *     }
 * }
 * ```
 */
@Stable
class TvRowFocusEntryState {
    internal val requester = FocusRequester()

    /**
     * Nombre d'items portant [requester] à cet instant. Une recomposition peut
     * en faire coexister deux le temps d'une frame (l'ancien pas encore
     * disposé) : seul « au moins un » importe. À zéro, `enter` doit rendre la
     * main à la recherche par défaut plutôt que de demander le focus sur un
     * `FocusRequester` non attaché, ce qui lèverait une `IllegalStateException`.
     */
    internal var attachedTargets by mutableIntStateOf(0)
}

@Composable
fun rememberTvRowFocusEntry(): TvRowFocusEntryState = remember { TvRowFocusEntryState() }

/**
 * À poser sur la `LazyRow`, **avant** `focusGroup()` : `focusGroup` pose le nœud
 * de focus auquel ces propriétés s'appliquent.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvRowFocusEntry(enabled: Boolean, entry: TvRowFocusEntryState): Modifier {
    if (!enabled) return this
    return focusProperties {
        enter = {
            if (entry.attachedTargets > 0) entry.requester else FocusRequester.Default
        }
    }
}

/**
 * À poser sur l'item d'index [index] de la rangée : il devient la cible d'entrée
 * quand il occupe l'ancre de début (premier item visible).
 *
 * `derivedStateOf` borne la recomposition aux seuls items dont la qualité de
 * cible change réellement, plutôt qu'à tous les items visibles à chaque
 * variation de `firstVisibleItemIndex`.
 */
@Composable
fun Modifier.tvRowFocusEntryTarget(
    enabled: Boolean,
    entry: TvRowFocusEntryState,
    state: LazyListState,
    index: Int
): Modifier {
    if (!enabled) return this
    val isTarget by remember(state, index) {
        derivedStateOf { state.firstVisibleItemIndex == index }
    }
    DisposableEffect(entry, isTarget) {
        if (isTarget) entry.attachedTargets++
        onDispose { if (isTarget) entry.attachedTargets-- }
    }
    return if (isTarget) this.focusRequester(entry.requester) else this
}
