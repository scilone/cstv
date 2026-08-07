package com.cstv.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Géométrie du sélecteur, en coordonnées de la racine de l'écran (fenêtre). */
data class TvSelectorTarget(val bounds: Rect, val cornerRadius: Dp)

/**
 * Couche avant du focus TV (F23). Ne mémorise que des positions
 * **stabilisées** : convergence de pivot terminée pour une carte de
 * rangée/grille (`TvPivotScroll`), ou position déjà définitive pour une cible
 * qui ne défile pas (Hero Card). Deux vignettes successives d'une même
 * rangée convergent vers **exactement** la même position → un cadre qui ne
 * suit que les positions stabilisées ne bouge pas d'un pixel. C'est tout le
 * mécanisme du « sélecteur statique ».
 *
 * Une vignette de rangée relève de **deux** pivots simultanés : l'horizontal
 * (`tvPivotItem`, qui fait glisser la `LazyRow`) et le vertical
 * (`tvPivotSection`). Ils ne se stabilisent pas au même rythme — le vertical en
 * deux frames quand la rangée est déjà en place, l'horizontal en toute la durée
 * du glissement. La publication est donc comptée : chaque pivot s'annonce
 * ([beginAxis]) et se retire ([endAxis]), et la géométrie n'est lue **et** posée
 * qu'au retrait du dernier. Une fenêtre d'attente de quelques frames, telle
 * qu'employée auparavant, ne pouvait pas tenir cette promesse : elle publiait au
 * premier axe stabilisé une position que l'autre axe déplaçait encore, d'où un
 * cadre qui partait puis se corrigeait (B22).
 */
@Stable
class TvFocusSelectorState {
    var target: TvSelectorTarget? by mutableStateOf(null)
        private set
    var isVisible: Boolean by mutableStateOf(false)
        private set

    private var runningAxes = 0
    private var settledCoordinates: LayoutCoordinates? = null
    private var settledRadius: Dp = 0.dp

    fun publishStabilised(bounds: Rect, cornerRadius: Dp) {
        target = TvSelectorTarget(bounds, cornerRadius)
        isVisible = true
    }

    /** Un pivot entame une convergence pour la cible focalisée. */
    fun beginAxis() {
        runningAxes++
    }

    /**
     * Un pivot a convergé. Rien n'est publié ici : la géométrie retenue n'est
     * lue qu'au retrait du dernier axe, quand plus rien ne bouge.
     */
    fun reportAxisStabilised(coordinates: LayoutCoordinates, cornerRadius: Dp) {
        settledCoordinates = coordinates
        settledRadius = cornerRadius
    }

    /**
     * Un pivot se retire — convergence terminée, abandonnée ou annulée, d'où
     * l'appel depuis un `finally`. Sans cela un axe annulé laisserait le compte
     * ouvert et le cadre ne serait plus jamais publié.
     */
    fun endAxis() {
        runningAxes = (runningAxes - 1).coerceAtLeast(0)
        if (runningAxes > 0) return
        val coordinates = settledCoordinates?.takeIf { it.isAttached }
        settledCoordinates = null
        if (coordinates != null) publishStabilised(coordinates.boundsInRoot(), settledRadius)
    }

    /**
     * Focus sorti des listes de médias (rail de navigation, dialogue, bouton
     * d'action). La géométrie n'est pas effacée : un retour ultérieur affiche
     * directement le cadre à sa dernière position plutôt que de le faire
     * apparaître en fondu depuis un `Rect.Zero`.
     */
    fun clear() {
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
 * Overlay de la couche avant : cadre unique dessiné au premier plan de la
 * `Box` racine de l'écran. Ordonnée, taille et rayon sont appliqués
 * immédiatement — le cadre est un repère fixe sous lequel défilent les
 * vignettes ; seul le passage d'une colonne à l'autre dans une grille est
 * amorti (voir le corps de la fonction). Non
 * focusable et non cliquable : il ne perturbe ni la recherche de focus D-pad,
 * ni l'activation de la carte réellement focalisée.
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
        val alpha by animateFloatAsState(
            targetValue = if (state.isVisible) 1f else 0f,
            animationSpec = floatSpringSpec,
            label = "tvFocusSelectorAlpha"
        )
        if (alpha <= 0f) return@Box

        val localTargetBounds = localBounds(target.bounds, host.boundsInRoot().topLeft)
        val cornerRadius = target.cornerRadius
        // Un seul déplacement est amorti : celui d'une colonne à la suivante
        // dans une grille — le cadre y change réellement de place, puisque rien
        // ne défile, et le glissement est alors la seule chose qui bouge à
        // l'écran (B22).
        //
        // Tout le reste est instantané, et la condition ci-dessous le garantit
        // en décrivant ce cas plutôt qu'en devinant le contexte : même ordonnée,
        // même taille. Cela exclut le changement de rangée, où l'ordonnée change
        // — et où l'abscisse peut changer aussi, les vignettes « Top N » étant
        // décalées par leur grand chiffre. Cela exclut aussi tout changement de
        // format entre deux rangées.
        var previousBounds by remember { mutableStateOf(localTargetBounds) }
        val purelyHorizontal = localTargetBounds.top == previousBounds.top &&
            localTargetBounds.width == previousBounds.width &&
            localTargetBounds.height == previousBounds.height
        SideEffect { previousBounds = localTargetBounds }

        // `animateDpAsState` — y compris avec `snap()` — reste porté par un
        // `LaunchedEffect` : la valeur qu'il expose reflète encore l'ancienne
        // cible pendant la frame où `target` change, puisque cet effet ne
        // s'exécute qu'après le commit de la composition. Ordonnée, largeur et
        // hauteur, elles, sont des `val` lus directement et changent donc dans
        // cette même frame — d'où un cadre à la bonne taille mais à l'ancienne
        // abscisse pendant un instant, perceptible surtout quand ancienne et
        // nouvelle abscisse diffèrent nettement (rangées « Top N » notamment).
        // Piloter l'`Animatable` à la main élimine ce décalage : hors glissement,
        // l'abscisse est lue directement dans `localTargetBounds`, sans jamais
        // dépendre de l'effet — seul `snapTo` le maintient synchronisé en
        // arrière-plan, prêt pour un futur glissement.
        val leftPx = localTargetBounds.left
        val leftAnimatable = remember { Animatable(leftPx) }
        LaunchedEffect(leftPx, purelyHorizontal) {
            if (purelyHorizontal) {
                leftAnimatable.animateTo(
                    leftPx,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                leftAnimatable.snapTo(leftPx)
            }
        }
        val left = with(density) {
            (if (purelyHorizontal) leftAnimatable.value else leftPx).toDp()
        }
        val top = with(density) { localTargetBounds.top.toDp() }
        val width = with(density) { localTargetBounds.width.toDp() }
        val height = with(density) { localTargetBounds.height.toDp() }

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
