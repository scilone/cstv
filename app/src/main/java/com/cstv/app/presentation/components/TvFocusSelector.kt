package com.cstv.app.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cstv.app.presentation.theme.AccentLavande
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Géométrie du sélecteur, en coordonnées de la racine de l'écran (fenêtre). */
data class TvSelectorTarget(val bounds: Rect, val cornerRadius: Dp)

/**
 * Nombre de frames attendues après la stabilisation d'un axe avant de publier
 * (Review F23, Majeur R3) : le pivot horizontal (`tvPivotItem`) et le pivot
 * vertical (`tvPivotSection`) se stabilisent chacun de façon indépendante pour
 * une même acquisition de focus D-pad. Publier au premier axe stabilisé
 * affiche une position dont l'autre axe est encore en mouvement, exactement
 * le saut que F23 doit supprimer. On attend donc une courte fenêtre après
 * chaque rapport : si l'autre axe rapporte à son tour dans cet intervalle, une
 * seule publication a lieu, avec la géométrie la plus récente connue.
 */
private const val AXIS_SETTLE_FRAMES = 2

/**
 * Couche avant du focus TV (F23). Ne mémorise que des positions
 * **stabilisées** : convergence de pivot terminée pour une carte de
 * rangée/grille (`TvPivotScroll`), ou position déjà définitive pour une cible
 * qui ne défile pas (Hero Card). Deux vignettes successives d'une même
 * rangée convergent vers **exactement** la même position → un cadre qui ne
 * suit que les positions stabilisées ne bouge pas d'un pixel. C'est tout le
 * mécanisme du « sélecteur statique ».
 */
@Stable
class TvFocusSelectorState {
    var target: TvSelectorTarget? by mutableStateOf(null)
        private set
    var isVisible: Boolean by mutableStateOf(false)
        private set

    private var settleJob: Job? = null

    fun publishStabilised(bounds: Rect, cornerRadius: Dp) {
        target = TvSelectorTarget(bounds, cornerRadius)
        isVisible = true
    }

    /**
     * Rapporte la stabilisation d'un axe (horizontal ou vertical) pour une
     * cible. N'importe quel nouveau rapport annule l'attente précédente et en
     * relance une nouvelle avec sa propre géométrie : la publication effective
     * porte toujours sur le dernier axe à s'être stabilisé, laissant à l'autre
     * axe la fenêtre de [AXIS_SETTLE_FRAMES] pour rapporter à son tour avant
     * qu'une frame ne soit réellement peinte (Review F23, Majeur R3).
     */
    fun reportAxisStabilised(scope: CoroutineScope, coordinates: LayoutCoordinates, cornerRadius: Dp) {
        settleJob?.cancel()
        settleJob = scope.launch {
            repeat(AXIS_SETTLE_FRAMES) { withFrameNanos { } }
            publishFrom(coordinates, cornerRadius)
        }
    }

    /**
     * Focus sorti des listes de médias (rail de navigation, dialogue, bouton
     * d'action). La géométrie n'est pas effacée : un retour ultérieur affiche
     * directement le cadre à sa dernière position plutôt que de le faire
     * apparaître en fondu depuis un `Rect.Zero`.
     */
    fun clear() {
        settleJob?.cancel()
        settleJob = null
        isVisible = false
    }
}

/** Fourni par les écrans catalogue TV ; `null` hors TV ou quand la couche avant est inactive. */
val LocalTvFocusSelector = staticCompositionLocalOf<TvFocusSelectorState?> { null }

/**
 * Traduit des bounds exprimés dans la racine de la fenêtre vers le repère
 * local d'un hôte dont l'origine dans cette même racine est
 * [hostOriginInRoot] (Review F23, Majeur R2) : la `Box` d'un écran catalogue
 * n'est pas la racine Compose — le rail de navigation TV la décale
 * horizontalement et `composableBelowStatusBar` lui ajoute un inset haut.
 * `Modifier.offset` étant relatif au parent (l'hôte), publier des coordonnées
 * racine telles quelles y décale le cadre une seconde fois.
 *
 * Fonction pure, sans dépendance Compose au-delà des types géométriques, pour
 * rester testable en JVM (Review F23, Mineur R6).
 */
internal fun localBounds(rootBounds: Rect, hostOriginInRoot: Offset): Rect =
    rootBounds.translate(-hostOriginInRoot.x, -hostOriginInRoot.y)

/**
 * Overlay de la couche avant : cadre unique animé, dessiné au premier plan de
 * la `Box` racine de l'écran. Non focusable et non cliquable : il ne perturbe
 * ni la recherche de focus D-pad, ni l'activation de la carte réellement
 * focalisée.
 *
 * Structure en deux `Box` (Review F23, Critique R1) : l'hôte reçoit le
 * `modifier` de l'appelant (typiquement `Modifier.fillMaxSize()`) et établit
 * seul le plein écran ; le cadre est un **enfant** positionné par
 * `offset`/`size`. Chaîner `fillMaxSize()` directement devant `offset()` et
 * `size()` imposerait des contraintes min = max au conteneur entier, et
 * `size()` ne pourrait alors plus réduire la `Box` aux bounds de la carte —
 * le cadre serait mesuré comme la couche plein écran elle-même.
 */
@Composable
fun TvFocusSelectorOverlay(state: TvFocusSelectorState, modifier: Modifier = Modifier) {
    val target = state.target
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(modifier = modifier.onGloballyPositioned { hostCoordinates = it }) {
        if (target == null) return@Box
        val host = hostCoordinates ?: return@Box
        val density = LocalDensity.current
        val floatSpringSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        val dpSpringSpec = spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        val alpha by animateFloatAsState(
            targetValue = if (state.isVisible) 1f else 0f,
            animationSpec = floatSpringSpec,
            label = "tvFocusSelectorAlpha"
        )
        if (alpha <= 0f) return@Box

        val localTargetBounds = localBounds(target.bounds, host.boundsInRoot().topLeft)
        val cornerRadius by animateDpAsState(target.cornerRadius, dpSpringSpec, label = "tvFocusSelectorRadius")
        val left by animateDpAsState(with(density) { localTargetBounds.left.toDp() }, dpSpringSpec, label = "tvFocusSelectorLeft")
        val top by animateDpAsState(with(density) { localTargetBounds.top.toDp() }, dpSpringSpec, label = "tvFocusSelectorTop")
        val width by animateDpAsState(with(density) { localTargetBounds.width.toDp() }, dpSpringSpec, label = "tvFocusSelectorWidth")
        val height by animateDpAsState(with(density) { localTargetBounds.height.toDp() }, dpSpringSpec, label = "tvFocusSelectorHeight")

        Box(
            modifier = Modifier
                .focusProperties { canFocus = false }
                .offset(x = left, y = top)
                .size(width = width, height = height)
                .alpha(alpha)
                .border(1.5.dp, AccentLavande.copy(alpha = 0.95f), RoundedCornerShape(cornerRadius))
        )
    }
}

/**
 * Publie la géométrie d'une cible directement, sans passer par
 * [TvFocusSelectorState.reportAxisStabilised] : réservé aux cibles à axe
 * unique (grille — `tvPivotCell` — et Hero Card), qui n'ont pas d'autre axe à
 * attendre.
 */
fun TvFocusSelectorState.publishFrom(coordinates: LayoutCoordinates?, cornerRadius: Dp) {
    val safeCoordinates = coordinates?.takeIf { it.isAttached } ?: return
    publishStabilised(safeCoordinates.boundsInRoot(), cornerRadius)
}
