package com.cstv.app.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TvFocusSelectorStateTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun initialStateHasNoTargetAndIsHidden() {
        val state = TvFocusSelectorState()
        assertNull(state.target)
        assertFalse(state.isVisible)
    }

    @Test
    fun publishStabilisedMakesTheFrameVisibleAndSetsGeometry() {
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)

        state.publishStabilised(bounds, 14.dp)

        assertTrue(state.isVisible)
        assertNotNull(state.target)
        assertEquals(bounds, state.target?.bounds)
        assertEquals(14.dp, state.target?.cornerRadius)
    }

    @Test
    fun twoIdenticalPublicationsLeaveTargetUnchanged() {
        // Propriété centrale du sélecteur statique F23 : deux vignettes
        // successives d'une même rangée qui convergent vers la même
        // géométrie ne doivent produire aucun mouvement perceptible du cadre.
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)

        state.publishStabilised(bounds, 14.dp)
        val firstTarget = state.target

        state.publishStabilised(bounds, 14.dp)
        val secondTarget = state.target

        assertEquals(firstTarget, secondTarget)
    }

    @Test
    fun clearHidesTheFrameWithoutErasingTheLastGeometry() {
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)
        state.publishStabilised(bounds, 14.dp)

        state.clear()

        assertFalse(state.isVisible)
        // La géométrie reste connue : un retour ultérieur du focus n'anime pas
        // depuis un Rect.Zero.
        assertEquals(bounds, state.target?.bounds)
    }

    @Test
    fun publishingAfterClearMakesTheFrameVisibleAgain() {
        val state = TvFocusSelectorState()
        state.publishStabilised(Rect(0f, 0f, 130f, 195f), 14.dp)
        state.clear()

        state.publishStabilised(Rect(50f, 60f, 180f, 255f), 16.dp)

        assertTrue(state.isVisible)
        assertEquals(Rect(50f, 60f, 180f, 255f), state.target?.bounds)
        assertEquals(16.dp, state.target?.cornerRadius)
    }

    // Review F23, Mineur R6 : la conversion de repère (Majeur R2) est extraite
    // en fonction pure `localBounds` précisément pour rester testable en JVM
    // sans dépendance à la mesure Compose réelle.

    @Test
    fun localBounds_hostAtRootOriginLeavesBoundsUnchanged() {
        val rootBounds = Rect(10f, 20f, 130f, 195f)

        val result = localBounds(rootBounds, hostOriginInRoot = Offset.Zero)

        assertEquals(rootBounds, result)
    }

    @Test
    fun localBounds_offsetHostTranslatesBoundsToItsLocalSpace() {
        // Cas concret F23-R2 : le rail TV (largeur w) et l'inset haut (h)
        // décalent la Box d'écran par rapport à la racine de la fenêtre.
        val rootBounds = Rect(210f, 120f, 340f, 315f) // carte mesurée dans la fenêtre
        val hostOriginInRoot = Offset(200f, 100f) // origine de la Box d'écran (rail + inset)

        val result = localBounds(rootBounds, hostOriginInRoot)

        assertEquals(Rect(10f, 20f, 140f, 215f), result)
    }

    @Test
    fun localBounds_preservesWidthAndHeight() {
        val rootBounds = Rect(500f, 300f, 630f, 495f) // 130 x 195
        val hostOriginInRoot = Offset(64f, 48f)

        val result = localBounds(rootBounds, hostOriginInRoot)

        assertEquals(rootBounds.width, result.width, 0f)
        assertEquals(rootBounds.height, result.height, 0f)
    }

    // --- PendingAxisTracker (B22) ---

    @Test
    fun gridCellCompletesOnItsSingleAxis() {
        val tracker = PendingAxisTracker()
        val card = Any()
        tracker.begin(card, TvPivotAxis.VERTICAL)

        assertTrue(tracker.complete(card, TvPivotAxis.VERTICAL))
    }

    @Test
    fun rowCardWaitsForBothAxesBeforeCompleting() {
        val tracker = PendingAxisTracker()
        val card = Any()
        tracker.begin(card, TvPivotAxis.HORIZONTAL)
        tracker.begin(card, TvPivotAxis.VERTICAL)

        // Le vertical se stabilise en premier (rangée déjà en place) : pas
        // encore de publication tant que l'horizontal glisse toujours.
        assertFalse(tracker.complete(card, TvPivotAxis.VERTICAL))
        assertTrue(tracker.complete(card, TvPivotAxis.HORIZONTAL))
    }

    @Test
    fun axisOrderDoesNotMatter() {
        val tracker = PendingAxisTracker()
        val card = Any()
        tracker.begin(card, TvPivotAxis.HORIZONTAL)
        tracker.begin(card, TvPivotAxis.VERTICAL)

        assertFalse(tracker.complete(card, TvPivotAxis.HORIZONTAL))
        assertTrue(tracker.complete(card, TvPivotAxis.VERTICAL))
    }

    @Test
    fun aNewTargetDiscardsEverythingOwedToThePreviousOne() {
        // Coeur du correctif B22 : une nouvelle carte efface l'attente en
        // cours pour l'ancienne, qu'elle ait ou non déjà rapporté un axe.
        val tracker = PendingAxisTracker()
        val firstCard = Any()
        val secondCard = Any()
        tracker.begin(firstCard, TvPivotAxis.HORIZONTAL)
        tracker.complete(firstCard, TvPivotAxis.VERTICAL) // jamais amorcé, sans effet

        tracker.begin(secondCard, TvPivotAxis.VERTICAL)

        assertFalse(tracker.complete(secondCard, TvPivotAxis.HORIZONTAL))
        assertTrue(tracker.complete(secondCard, TvPivotAxis.VERTICAL))
    }

    @Test
    fun aLateCompletionForAnAbandonedTargetIsRejected() {
        // Défilement D-pad maintenu (répétition matérielle) : la convergence
        // de la carte N, annulée par l'arrivée de la carte N+1, ne termine
        // parfois qu'après coup — l'annulation d'une coroutine ne prend effet
        // qu'à sa prochaine suspension. Ce rapport tardif ne doit ni publier,
        // ni entamer le suivi de la carte réellement visée.
        val tracker = PendingAxisTracker()
        val abandonedCard = Any()
        val currentCard = Any()
        tracker.begin(abandonedCard, TvPivotAxis.HORIZONTAL)
        tracker.begin(abandonedCard, TvPivotAxis.VERTICAL)

        tracker.begin(currentCard, TvPivotAxis.VERTICAL) // la carte suivante prend le relais

        assertFalse(tracker.complete(abandonedCard, TvPivotAxis.HORIZONTAL))
        // La carte courante n'a pourtant reçu qu'un seul axe : la carte
        // abandonnée ne doit pas l'avoir fait progresser.
        assertFalse(tracker.complete(currentCard, TvPivotAxis.HORIZONTAL))
    }

    @Test
    fun bringIntoViewCorrectionRestartsOnlyItsOwnAxis() {
        // Le callback de bounds reste actif après la stabilisation initiale
        // pour corriger un `bringIntoView` tardif de Compose (F19). Un seul
        // axe recommence alors ; l'autre, déjà rapporté, ne doit pas être
        // exigé à nouveau.
        val tracker = PendingAxisTracker()
        val card = Any()
        tracker.begin(card, TvPivotAxis.HORIZONTAL)
        tracker.begin(card, TvPivotAxis.VERTICAL)
        assertFalse(tracker.complete(card, TvPivotAxis.VERTICAL))
        assertTrue(tracker.complete(card, TvPivotAxis.HORIZONTAL))

        tracker.begin(card, TvPivotAxis.VERTICAL) // correction tardive, même carte

        assertTrue(tracker.complete(card, TvPivotAxis.VERTICAL))
    }

    @Test
    fun beginReportsWhetherTheTargetIsGenuinelyNew() {
        // Signal consommé par TvFocusSelectorState.beginAxis pour masquer le
        // cadre le temps d'une convergence sur une cible différente (B22) —
        // jamais pour une simple correction tardive sur la carte déjà active.
        val tracker = PendingAxisTracker()
        val firstCard = Any()
        val secondCard = Any()

        assertTrue(tracker.begin(firstCard, TvPivotAxis.HORIZONTAL))
        assertFalse(tracker.begin(firstCard, TvPivotAxis.VERTICAL))
        assertFalse(tracker.begin(firstCard, TvPivotAxis.HORIZONTAL)) // relance, même carte
        assertTrue(tracker.begin(secondCard, TvPivotAxis.VERTICAL))
    }

    // --- Taille adoptée immédiatement, cadre jamais masqué (B22) ---
    //
    // `beginAxis` a besoin d'un vrai `LayoutCoordinates`, hors de portée d'un
    // test JVM sans Compose UI réel (AGENTS.md) : seul le contrat observable
    // sans lui est vérifié ici — le cadre reste visible d'un bout à l'autre.
    // Le redimensionnement en place est couvert par `PendingAxisTracker`, qui
    // décide seul de ce qui est une cible neuve.

    @Test
    fun publishingKeepsTheFrameVisibleAcrossTargets() {
        val state = TvFocusSelectorState()
        state.publishStabilised(Rect(0f, 0f, 130f, 195f), 14.dp)
        assertTrue(state.isVisible)

        state.publishStabilised(Rect(0f, 0f, 280f, 92f), 12.dp)

        assertTrue(state.isVisible)
        assertEquals(Rect(0f, 0f, 280f, 92f), state.target?.bounds)
        assertEquals(12.dp, state.target?.cornerRadius)
    }
}
