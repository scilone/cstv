package com.cstv.app.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Règle de repositionnement du focus à l'entrée d'un écran catalogue TV
 * ([tvFocusEntryTarget]). Le comportement D-pad lui-même n'est pas couvert en
 * JVM (pas de dépendance Compose UI test, cf. `AGENTS.md`) : ce test verrouille
 * la décision, pas son application.
 */
class TvFocusEntryTest {

    @Test
    fun `retour du rail avec vignette memorisee revient dessus`() {
        assertEquals(
            TvFocusEntryTarget.RESTORED_MEDIA,
            tvFocusEntryTarget(
                alreadyFocused = false,
                modalHandoverPending = false,
                hasRestoreTarget = true,
                hasInitialTarget = true
            )
        )
    }

    @Test
    fun `sans vignette memorisee le focus se pose sur la cible initiale`() {
        assertEquals(
            TvFocusEntryTarget.INITIAL_MEDIA,
            tvFocusEntryTarget(
                alreadyFocused = false,
                modalHandoverPending = false,
                hasRestoreTarget = false,
                hasInitialTarget = true
            )
        )
    }

    @Test
    fun `sans aucune cible le focus par defaut est conserve`() {
        assertEquals(
            TvFocusEntryTarget.NONE,
            tvFocusEntryTarget(
                alreadyFocused = false,
                modalHandoverPending = false,
                hasRestoreTarget = false,
                hasInitialTarget = false
            )
        )
    }

    @Test
    fun `un deplacement interne a l ecran ne repositionne rien`() {
        // Sans cette garde, chaque événement de focus rejouerait la
        // restauration et interdirait de rejoindre le chrome de l'écran.
        assertEquals(
            TvFocusEntryTarget.NONE,
            tvFocusEntryTarget(
                alreadyFocused = true,
                modalHandoverPending = false,
                hasRestoreTarget = true,
                hasInitialTarget = true
            )
        )
    }

    @Test
    fun `la fermeture d une surface modale garde la main sur le focus`() {
        // Le dialogue de catégories rend le focus à son déclencheur : la
        // restauration ne doit pas le lui arracher.
        assertEquals(
            TvFocusEntryTarget.NONE,
            tvFocusEntryTarget(
                alreadyFocused = false,
                modalHandoverPending = true,
                hasRestoreTarget = true,
                hasInitialTarget = true
            )
        )
    }
}
