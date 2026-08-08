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
import androidx.compose.ui.unit.toSize
import com.cstv.app.presentation.theme.AccentLavande

/**
 * Géométrie du sélecteur, en coordonnées de la racine de l'écran (fenêtre).
 *
 * [glideHorizontally] n'est vrai que pour une cellule de **grille** : là, rien
 * ne défile entre deux colonnes, c'est le cadre qui change réellement de place
 * et son glissement est l'unique mouvement à l'écran. Partout ailleurs le cadre
 * est un repère fixe et toute correction d'abscisse doit être instantanée.
 * Déduire ce cas de la seule géométrie (« même ordonnée, même taille ») ne
 * suffisait pas : deux rangées voisines s'ancrent au même endroit avec des
 * cartes de même format et ne diffèrent que par l'abscisse — les vignettes
 * « Top N » sont décalées par leur grand chiffre —, ce que la comparaison
 * prenait pour un déplacement de grille (B22).
 */
data class TvSelectorTarget(
    val bounds: Rect,
    val cornerRadius: Dp,
    val glideHorizontally: Boolean = false
)

/** Identifie les deux pivots qui peuvent concourir pour une même vignette de rangée. */
enum class TvPivotAxis { HORIZONTAL, VERTICAL }

/**
 * Suit, pour une cible identifiée par référence, l'ensemble des axes encore
 * attendus avant de considérer sa géométrie stabilisée (B22).
 *
 * Une vignette de rangée relève de **deux** pivots simultanés : l'horizontal
 * (`tvPivotItem`, qui fait glisser la `LazyRow`) et le vertical
 * (`tvPivotSection`). Ils ne se stabilisent pas au même rythme — le vertical en
 * deux frames quand la rangée est déjà en place, l'horizontal en toute la durée
 * du glissement — [complete] n'autorise donc la publication qu'une fois les
 * deux rapportés pour la **même** cible. Une cellule de grille n'a qu'un axe
 * ([TvPivotAxis.VERTICAL], seul utilisé par `tvPivotCell`) et se stabilise
 * donc dès son unique rapport.
 *
 * La cible sert de clé ([key], le `LayoutCoordinates` du descendant focalisé —
 * identique pour les deux axes d'une même carte). C'est ce qui distingue cette
 * conception d'un simple compteur de pivots « en cours » (première version
 * B22) : sous répétition D-pad rapide (appui maintenu), chaque nouvelle cible
 * annule les convergences de la précédente, mais l'annulation d'une coroutine
 * ne prend effet qu'à sa prochaine suspension — un compteur partagé pouvait
 * donc se retrouver décrémenté en retard par une cible déjà supplantée,
 * publiant sa géométrie obsolète au lieu de celle réellement visée. Ici,
 * [begin] pour une **nouvelle** cible vide l'ensemble attendu et y range
 * fraîchement les axes attendus ; [complete] rejette silencieusement tout
 * rapport dont la cible ne correspond plus à [key] — une complétion tardive
 * d'une cible abandonnée ne peut donc jamais corrompre le suivi de la cible
 * courante.
 *
 * Sans dépendance Compose au-delà de son paramètre générique, pour rester
 * testable en JVM.
 */
internal class PendingAxisTracker {
    private var key: Any? = null
    private val pendingAxes = mutableSetOf<TvPivotAxis>()

    /**
     * Un pivot amorce une convergence pour [target]. Une cible différente de
     * celle en cours efface tout l'état précédent : elle appartenait à une
     * carte déjà quittée. Sur cible inchangée (`tvPivotCell`/`tvPivotSection`
     * restent actifs après le focus initial pour corriger un `bringIntoView`
     * tardif de Compose), seul [axis] repart en attente — l'autre axe, déjà
     * rapporté, n'a pas à reconverger.
     *
     * @return `true` si [target] est une cible réellement nouvelle (appelant :
     * voir [TvFocusSelectorState.beginAxis]).
     */
    fun begin(target: Any, axis: TvPivotAxis): Boolean {
        val isNewTarget = key !== target
        if (isNewTarget) {
            key = target
            pendingAxes.clear()
        }
        pendingAxes.add(axis)
        return isNewTarget
    }

    /**
     * Un pivot a convergé pour [target].
     *
     * @return `true` si [target] est toujours la cible courante et que tous
     * ses axes attendus ont maintenant rapporté — c'est alors, et alors
     * seulement, que l'appelant doit publier.
     */
    fun complete(target: Any, axis: TvPivotAxis): Boolean {
        if (key !== target) return false
        pendingAxes.remove(axis)
        return pendingAxes.isEmpty()
    }

    /** Vrai tant que [target] est la cible en cours — elle seule peut poser sa géométrie. */
    fun owns(target: Any): Boolean = key === target
}

/**
 * Couche avant du focus TV (F23). Ne mémorise que des positions
 * **stabilisées** : convergence de pivot terminée pour une carte de
 * rangée/grille (`TvPivotScroll`), ou position déjà définitive pour une cible
 * qui ne défile pas (Hero Card). Deux vignettes successives d'une même
 * rangée convergent vers **exactement** la même position → un cadre qui ne
 * suit que les positions stabilisées ne bouge pas d'un pixel. C'est tout le
 * mécanisme du « sélecteur statique ». Voir [PendingAxisTracker] pour la
 * coordination des deux axes d'une même carte de rangée.
 *
 * Le cadre reste **visible en permanence** tant que le focus est dans une
 * liste : il ne s'efface ni ne se rallume entre deux cartes. Sa **taille**, en
 * revanche, est adoptée dès l'acquisition du focus ([beginAxis]), sans attendre
 * la fin du défilement (B22) : une carte est mesurée avant de bouger, sa taille
 * est donc connue tout de suite, alors que sa position ne le sera qu'une fois la
 * convergence terminée. Comme toutes les cartes rejoignent la même ancre, le
 * coin haut-gauche du cadre ne change pas — seule la taille avait à suivre, et
 * la faire attendre la fin du défilement laissait un cadre au mauvais format
 * posé sur le contenu qui glissait dessous (cartes « Top N », chaîne favorite
 * plus large que sa voisine).
 */
@Stable
class TvFocusSelectorState {
    var target: TvSelectorTarget? by mutableStateOf(null)
        private set
    var isVisible: Boolean by mutableStateOf(false)
        private set

    private val axisTracker = PendingAxisTracker()

    fun publishStabilised(bounds: Rect, cornerRadius: Dp, glideHorizontally: Boolean = false) {
        target = TvSelectorTarget(bounds, cornerRadius, glideHorizontally)
        isVisible = true
    }

    /**
     * Un pivot amorce une convergence pour [coordinates] (le descendant
     * réellement focalisé). Sur une cible neuve, le cadre prend immédiatement sa
     * taille et son rayon, en gardant son coin haut-gauche : voir la
     * documentation de la classe.
     *
     * Amorcer, c'est aussi **prendre la main** sur le cadre : toute publication
     * ultérieure portant sur une autre cible est refusée jusqu'à ce que
     * celle-ci amorce à son tour. Sans cela, une cible qui republie à chaque
     * mesure — la Hero Card le fait, sa position n'étant jamais figée par une
     * convergence — écrasait la géométrie de la carte qui venait de prendre le
     * focus, à chacune des mesures provoquées par le défilement : le grand cadre
     * de la Hero « restait » alors visiblement le temps que la liste défile
     * (B22).
     */
    fun beginAxis(coordinates: LayoutCoordinates, axis: TvPivotAxis, cornerRadius: Dp) {
        if (!axisTracker.begin(coordinates, axis)) return
        val current = target ?: return
        target = TvSelectorTarget(
            bounds = Rect(current.bounds.topLeft, coordinates.size.toSize()),
            cornerRadius = cornerRadius,
            glideHorizontally = false
        )
    }

    /**
     * Pose **tout de suite** le cadre là où la carte s'arrêtera sur cet axe,
     * sans attendre que le défilement l'y amène (B22).
     *
     * C'est le correctif de fond : la position d'arrivée n'a jamais eu besoin
     * d'être mesurée après coup. Elle vaut « position actuelle moins le
     * défilement qu'on s'apprête à faire », et ce défilement est calculé avant
     * de commencer. Publier seulement à la fin de la convergence — ce que faisait
     * l'implémentation initiale — imposait au cadre d'attendre que **toutes les
     * vignettes aient fini de bouger** pour se replacer : de loin le défaut le
     * plus visible, et la cause commune de tous les « reliquats » signalés.
     *
     * Chaque axe pose sa composante ; l'autre garde la sienne. Un axe qui ne
     * peut pas prédire (rangée pas encore mesurable) laisse simplement la
     * composante en place, et la publication de fin de convergence corrigera.
     */
    fun predictAxis(key: Any, axis: TvPivotAxis, anchoredOffset: Float, cornerRadius: Dp) {
        if (!axisTracker.owns(key)) return
        val current = target ?: return
        val topLeft = when (axis) {
            TvPivotAxis.HORIZONTAL -> Offset(anchoredOffset, current.bounds.top)
            TvPivotAxis.VERTICAL -> Offset(current.bounds.left, anchoredOffset)
        }
        target = TvSelectorTarget(
            bounds = Rect(topLeft, current.bounds.size),
            cornerRadius = cornerRadius,
            glideHorizontally = false
        )
        isVisible = true
    }

    /**
     * Un pivot a convergé pour [key] ; publie dès que tous les axes attendus
     * l'ont fait. Filet de sécurité derrière [predictAxis] : quand la prédiction
     * était juste — le cas courant — cette publication ne change rien.
     */
    fun reportAxisStabilised(
        key: Any,
        axis: TvPivotAxis,
        coordinates: LayoutCoordinates,
        cornerRadius: Dp,
        glideHorizontally: Boolean = false
    ) {
        if (!axisTracker.complete(key, axis)) return
        val safeCoordinates = coordinates.takeIf { it.isAttached } ?: return
        publishStabilised(safeCoordinates.boundsInRoot(), cornerRadius, glideHorizontally)
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
        // dans une grille, que la cible déclare elle-même
        // ([TvSelectorTarget.glideHorizontally]) — la géométrie seule ne permet
        // pas de le reconnaître (B22).
        //
        // `animateDpAsState` — y compris avec `snap()` — reste porté par un
        // `LaunchedEffect` : la valeur qu'il expose reflète encore l'ancienne
        // cible pendant la frame où `target` change, puisque cet effet ne
        // s'exécute qu'après le commit de la composition. Ordonnée, largeur et
        // hauteur, elles, sont des `val` lus directement et changent donc dans
        // cette même frame — d'où un cadre à la bonne taille mais à l'ancienne
        // abscisse pendant un instant. Piloter l'`Animatable` à la main élimine
        // ce décalage : hors glissement, l'abscisse est lue directement dans
        // `localTargetBounds`, sans jamais dépendre de l'effet — seul `snapTo`
        // le maintient synchronisé en arrière-plan, prêt pour un futur
        // glissement.
        val glide = target.glideHorizontally
        val leftPx = localTargetBounds.left
        val leftAnimatable = remember { Animatable(leftPx) }
        LaunchedEffect(leftPx, glide) {
            if (glide) {
                leftAnimatable.animateTo(
                    leftPx,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                leftAnimatable.snapTo(leftPx)
            }
        }
        val left = with(density) {
            (if (glide) leftAnimatable.value else leftPx).toDp()
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
 * Publie la géométrie d'une cible dont la position est **déjà définitive**,
 * sans convergence à attendre : la Hero Card, qui ne défile pas sous le cadre.
 *
 * Passe malgré tout par [TvFocusSelectorState.beginAxis] pour prendre la main
 * sur le cadre, puis par [TvFocusSelectorState.reportAxisStabilised] pour
 * publier. Cette cible republie à **chaque mesure** tant qu'elle a le focus ;
 * sans cette prise de main, ses republications continuaient d'écraser la
 * géométrie d'une carte qui venait de prendre le focus — le défilement qui
 * l'emporte hors de l'écran en provoque une par frame — et le grand cadre de la
 * Hero « restait » visiblement le temps que la liste défile (B22).
 */
fun TvFocusSelectorState.publishFrom(coordinates: LayoutCoordinates?, cornerRadius: Dp) {
    val safeCoordinates = coordinates?.takeIf { it.isAttached } ?: return
    beginAxis(safeCoordinates, TvPivotAxis.VERTICAL, cornerRadius)
    reportAxisStabilised(safeCoordinates, TvPivotAxis.VERTICAL, safeCoordinates, cornerRadius)
}
