package com.cstv.app.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TvPivotScrollTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun horizontalPivotAnchorsEveryItemAtFirstThumbnailPosition() {
        assertEquals(0, pivotScrollOffset(viewportSize = 1920, itemSize = 130, parentFraction = 0f, childFraction = 0f))
    }

    @Test
    fun verticalPivotAt50PercentCentersItem() {
        assertEquals(-390, pivotScrollOffset(viewportSize = 1080, itemSize = 300, parentFraction = 0.5f, childFraction = 0.5f))
    }

    @Test
    fun unmeasuredItemFallsBackToParentFractionOnly() {
        val offset = pivotScrollOffset(viewportSize = 1920, itemSize = 0, parentFraction = 0.15f, childFraction = 0f)
        assertEquals(-Math.round(1920 * 0.15f), offset)
    }

    @Test
    fun unmeasuredViewportProducesNoScroll() {
        assertEquals(0, pivotScrollOffset(viewportSize = 0, itemSize = 130, parentFraction = 0.15f, childFraction = 0f))
    }

    @Test
    fun itemLargerThanViewportProducesExactPositiveOffset() {
        // Review F19, Mineur #2 : la version précédente vérifiait qu'un `Int`
        // n'est pas "NaN", ce qu'il ne peut structurellement jamais être — le
        // test ne démontrait rien. Remplacée par une valeur exacte.
        assertEquals(2400, pivotScrollOffset(viewportSize = 200, itemSize = 5000, parentFraction = 0.5f, childFraction = 0.5f))
    }

    @Test
    fun hugeItemSizeNearIntRangeDoesNotOverflow() {
        // itemSize proche de l'échelle d'Int.MAX_VALUE : si le calcul était un
        // jour réécrit en arithmétique Int pure (au lieu de Float), une telle
        // valeur ferait déborder le produit intermédiaire et boucler vers un
        // nombre négatif. `2.0f.pow(30)` reste exactement représentable en
        // Float (puissance de deux), donc le résultat attendu est exact.
        val hugeItemSize = 1_073_741_824 // 2^30
        assertEquals(
            536_870_912, // 2^29
            pivotScrollOffset(viewportSize = 200, itemSize = hugeItemSize, parentFraction = 0f, childFraction = 0.5f)
        )
    }

    @Test
    fun hugeViewportSizeNearIntRangeDoesNotOverflow() {
        val hugeViewportSize = 1_073_741_824 // 2^30
        assertEquals(
            -1_073_741_824,
            pivotScrollOffset(viewportSize = hugeViewportSize, itemSize = 0, parentFraction = 1f, childFraction = 0f)
        )
    }

    @Test
    fun nonIntegerFractionRoundsDeterministically() {
        val offset = pivotScrollOffset(viewportSize = 1001, itemSize = 0, parentFraction = 0.15f, childFraction = 0f)
        assertEquals(-150, offset)
    }

    @Test
    fun zeroDeltaIsNotClamped() {
        assertFalse(isPivotClamped(delta = 0f, consumed = 0f))
    }

    @Test
    fun residualDeltaWithNoConsumptionIsClamped() {
        assertTrue(isPivotClamped(delta = 120f, consumed = 0f))
    }

    @Test
    fun residualDeltaWithPartialConsumptionIsNotClamped() {
        assertFalse(isPivotClamped(delta = 120f, consumed = 45f))
    }

    @Test
    fun negativeResidualDeltaWithNoConsumptionIsClamped() {
        assertTrue(isPivotClamped(delta = -120f, consumed = 0f))
    }

    // --- Ancre haute des grilles (B22) ---

    @Test
    fun firstGridRowIsAlreadyOnTheTopAnchor() {
        // Grille au repos : le premier item est à l'origine du contenu, sous la
        // réserve haute. Rien à défiler, donc aucun mouvement du cadre F23.
        assertEquals(
            0f,
            topAnchoredPivotDelta(viewportStartOffset = -48, beforeContentPadding = 48, itemOffset = 0)
        )
    }

    @Test
    fun secondGridRowScrollsExactlyItsOwnOffset() {
        // La deuxième rangée remonte de toute sa distance à l'origine : elle
        // occupe alors très exactement la place qu'occupait la première.
        assertEquals(
            440f,
            topAnchoredPivotDelta(viewportStartOffset = -48, beforeContentPadding = 48, itemOffset = 440)
        )
    }

    @Test
    fun rowAboveTheAnchorScrollsBackwards() {
        // Remontée au D-pad : la rangée visée est au-dessus de l'ancre, le
        // défilement doit être négatif pour la ramener dessus.
        assertEquals(
            -440f,
            topAnchoredPivotDelta(viewportStartOffset = -48, beforeContentPadding = 48, itemOffset = -440)
        )
    }

    @Test
    fun topAnchorFollowsTheLeadingContentPadding() {
        // La réserve haute déplace l'ancre d'autant : le cadre se cale sous le
        // bandeau, jamais collé au bord du viewport.
        assertEquals(
            0f,
            topAnchoredPivotDelta(viewportStartOffset = -120, beforeContentPadding = 120, itemOffset = 0)
        )
    }

    @Test
    fun topAnchorHoldsUnderTheContainerRelativeOffsetConvention() {
        // Convention alternative (offsets comptés depuis le bord du conteneur,
        // viewportStartOffset nul) : l'ancre vaut alors la réserve haute
        // elle-même, où se trouve la première rangée. L'expression reste juste.
        assertEquals(
            0f,
            topAnchoredPivotDelta(viewportStartOffset = 0, beforeContentPadding = 48, itemOffset = 48)
        )
        assertEquals(
            440f,
            topAnchoredPivotDelta(viewportStartOffset = 0, beforeContentPadding = 48, itemOffset = 488)
        )
    }

    @Test
    fun gridWithoutLeadingPaddingAnchorsOnTheViewportEdge() {
        assertEquals(
            0f,
            topAnchoredPivotDelta(viewportStartOffset = 0, beforeContentPadding = 0, itemOffset = 0)
        )
    }

    // --- Estimation pour une cellule hors viewport (B22) ---

    @Test
    fun rowAboveTheViewportIsEstimatedOneStrideBackwards() {
        // Remontée d'une ligne : cible index 3, première ligne visible = 6..8
        // sur 3 colonnes. Une foulée de ligne vers le haut.
        assertEquals(
            -(220f + 16f),
            offscreenRowPivotDelta(
                targetIndex = 3,
                firstVisibleIndex = 6,
                columns = 3,
                rowHeight = 220,
                mainAxisItemSpacing = 16
            )
        )
    }

    @Test
    fun estimationCountsWholeRowsNotItems() {
        // Deux lignes d'écart, quel que soit le rang de la cellule dans sa ligne.
        assertEquals(
            -(2 * (220f + 16f)),
            offscreenRowPivotDelta(
                targetIndex = 2,
                firstVisibleIndex = 8,
                columns = 3,
                rowHeight = 220,
                mainAxisItemSpacing = 16
            )
        )
    }

    @Test
    fun cellOnTheFirstVisibleRowNeedsNoEstimatedScroll() {
        // Même ligne que le premier item visible : rien à estimer, la position
        // mesurée prendra le relais dès qu'elle sera disponible.
        assertEquals(
            0f,
            offscreenRowPivotDelta(
                targetIndex = 8,
                firstVisibleIndex = 6,
                columns = 3,
                rowHeight = 220,
                mainAxisItemSpacing = 16
            )
        )
    }

    @Test
    fun rowBelowTheViewportIsEstimatedForwards() {
        assertEquals(
            236f,
            offscreenRowPivotDelta(
                targetIndex = 9,
                firstVisibleIndex = 6,
                columns = 3,
                rowHeight = 220,
                mainAxisItemSpacing = 16
            )
        )
    }

    @Test
    fun unmeasuredGridProducesNoEstimatedScroll() {
        // Grille pas encore mesurée : ni colonnes ni hauteur exploitables. Une
        // estimation inventée ferait défiler à l'aveugle.
        assertEquals(
            0f,
            offscreenRowPivotDelta(
                targetIndex = 9,
                firstVisibleIndex = 0,
                columns = 0,
                rowHeight = 220,
                mainAxisItemSpacing = 16
            )
        )
        assertEquals(
            0f,
            offscreenRowPivotDelta(
                targetIndex = 9,
                firstVisibleIndex = 0,
                columns = 3,
                rowHeight = 0,
                mainAxisItemSpacing = 16
            )
        )
    }

    // --- Foulée de rattrapage d'une liste de rangées (B22) ---

    @Test
    fun offscreenSectionStepGoesUpByOneRowStride() {
        // Une rangée introuvable est forcément au-dessus : la rangée active
        // occupe l'ancre haute et la réserve de fin garde visible ce qui suit.
        assertEquals(
            -(248f + 16f),
            offscreenSectionStepDelta(firstVisibleItemHeight = 248, mainAxisItemSpacing = 16)
        )
    }

    @Test
    fun offscreenSectionStepCountsTheSpacingBetweenRows() {
        assertEquals(
            -248f,
            offscreenSectionStepDelta(firstVisibleItemHeight = 248, mainAxisItemSpacing = 0)
        )
    }

    @Test
    fun unmeasuredRowListProducesNoCatchUpStep() {
        // Liste pas encore mesurée : défiler d'une foulée inventée déplacerait
        // la liste à l'aveugle, sans rien ramener dans le champ.
        assertEquals(
            0f,
            offscreenSectionStepDelta(firstVisibleItemHeight = 0, mainAxisItemSpacing = 16)
        )
    }

    @Test
    fun estimationHoldsOnASingleColumnGrid() {
        assertEquals(
            -(180f + 12f),
            offscreenRowPivotDelta(
                targetIndex = 4,
                firstVisibleIndex = 5,
                columns = 1,
                rowHeight = 180,
                mainAxisItemSpacing = 12
            )
        )
    }
}
