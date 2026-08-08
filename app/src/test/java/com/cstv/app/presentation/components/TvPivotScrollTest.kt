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

    // --- Ancre déterministe, calculée depuis le conteneur (B22) ---

    @Test
    fun topAnchorPlacesTheThumbnailUnderTheLeadingReserve() {
        // Rien ici ne dépend de la position courante de la vignette : conteneur,
        // réserve de tête, et décalage de la vignette dans son item (le bandeau
        // de titre au-dessus d'elle) — toutes stables pendant le défilement.
        assertEquals(
            100f + 24f + 42f,
            anchoredRootTop(
                viewportRootTop = 100f,
                viewportHeight = 1080,
                beforeContentPadding = 24,
                focusedOffsetInItem = 42f,
                focusedHeight = 300,
                anchor = TvPivotAnchor.Top
            )
        )
    }

    @Test
    fun topAnchorIgnoresTheThumbnailHeight() {
        // Deux vignettes de hauteurs différentes se posent au même endroit :
        // c'est très exactement la promesse du cadre fixe.
        val short = anchoredRootTop(100f, 1080, 24, 0f, 92, TvPivotAnchor.Top)
        val tall = anchoredRootTop(100f, 1080, 24, 0f, 300, TvPivotAnchor.Top)
        assertEquals(short, tall)
    }

    @Test
    fun midViewportAnchorPutsTheThumbnailTopAtHalfHeight() {
        // Bord haut de la vignette à mi-conteneur : 100 + 1080/2.
        assertEquals(
            640f,
            anchoredRootTop(
                viewportRootTop = 100f,
                viewportHeight = 1080,
                beforeContentPadding = 24,
                focusedOffsetInItem = 42f,
                focusedHeight = 300,
                anchor = TvPivotAnchor.MidViewport
            )
        )
    }

    @Test
    fun midViewportAnchorIgnoresTheThumbnailHeight() {
        // Le point corrigé : centrer la vignette faisait dépendre le haut du
        // cadre de la hauteur de la carte, et le cadre bougeait donc à chaque
        // changement de format de rangée. Ancrer le bord haut l'immobilise.
        val short = anchoredRootTop(100f, 1080, 24, 0f, 92, TvPivotAnchor.MidViewport)
        val tall = anchoredRootTop(100f, 1080, 24, 0f, 300, TvPivotAnchor.MidViewport)
        assertEquals(short, tall)
    }

    @Test
    fun midViewportAnchorIgnoresTheTitleBandHeight() {
        // Et pas davantage du bandeau de titre au-dessus de la vignette : c'est
        // la vignette qui s'aligne, la rangée se place autour.
        val plain = anchoredRootTop(100f, 1080, 24, 0f, 300, TvPivotAnchor.MidViewport)
        val titled = anchoredRootTop(100f, 1080, 24, 42f, 300, TvPivotAnchor.MidViewport)
        assertEquals(plain, titled)
    }

    @Test
    fun anchorFollowsTheContainerWhenTheScreenMovesIt() {
        // Rail de navigation, insets : le conteneur peut être décalé dans la
        // fenêtre, l'ancre suit sans autre calcul.
        assertEquals(
            60f + 24f,
            anchoredRootTop(60f, 720, 24, 0f, 200, TvPivotAnchor.Top)
        )
    }

    // --- Pivot à mi-hauteur de l'Accueil : bord haut visé (B22) ---

    @Test
    fun midViewportPivotAlignsTheFocusedThumbnailNotTheRow() {
        // Le bandeau de titre au-dessus des vignettes ne doit pas décaler la
        // carte : c'est sa position réelle dans la section qui compte. Bord haut
        // visé : 390 + 42 − 540.
        assertEquals(
            -108f,
            focusedChildPivotDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 1080,
                sectionOffset = 390,
                focusedOffsetInSection = 42f,
                focusedSize = 300
            )
        )
    }

    @Test
    fun midViewportPivotTargetsTheThumbnailTopNotItsCentre() {
        // Deux vignettes de hauteurs différentes, même position : même
        // défilement demandé. C'est ce qui immobilise le cadre.
        val short = focusedChildPivotDelta(0, 1080, 600, 0f, 92)
        val tall = focusedChildPivotDelta(0, 1080, 600, 0f, 300)
        assertEquals(short, tall)
    }

    @Test
    fun midViewportPivotNeedsNoScrollWhenAlreadyInPlace() {
        // Pivot = −24 + 1080/2 = 516 ; bord haut de la vignette = 474 + 42.
        assertEquals(
            0f,
            focusedChildPivotDelta(
                viewportStartOffset = -24,
                viewportEndOffset = 1056,
                sectionOffset = 474,
                focusedOffsetInSection = 42f,
                focusedSize = 300
            )
        )
    }

    @Test
    fun midViewportPivotMovesContentInBothDirections() {
        assertEquals(
            20f,
            focusedChildPivotDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 720,
                sectionOffset = 380,
                focusedOffsetInSection = 0f,
                focusedSize = 200
            )
        )
        assertEquals(
            -220f,
            focusedChildPivotDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 720,
                sectionOffset = 140,
                focusedOffsetInSection = 0f,
                focusedSize = 200
            )
        )
    }

    @Test
    fun midViewportPivotWithUnmeasuredViewportNeedsNoScroll() {
        assertEquals(
            0f,
            focusedChildPivotDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 0,
                sectionOffset = 100,
                focusedOffsetInSection = 20f,
                focusedSize = 200
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
