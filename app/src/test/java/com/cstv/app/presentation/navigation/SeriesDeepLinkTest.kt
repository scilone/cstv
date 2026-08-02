package com.cstv.app.presentation.navigation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SeriesDeepLinkTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun extractsTargetFromCompleteIntent() {
        val intent = mock<Intent>()
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_SERIES_ID, -1)).thenReturn(42)
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_PROFILE_ID, -1)).thenReturn(3)

        assertEquals(SeriesDeepLink.Target(42, 3), SeriesDeepLink.extract(intent))
    }

    @Test
    fun nullIntent_returnsNull() {
        assertNull(SeriesDeepLink.extract(null))
    }

    @Test
    fun intentWithoutExtras_returnsNull() {
        val intent = mock<Intent>()
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_SERIES_ID, -1)).thenReturn(-1)
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_PROFILE_ID, -1)).thenReturn(-1)

        assertNull(SeriesDeepLink.extract(intent))
    }

    @Test
    fun intentWithOnlySeriesId_returnsNull() {
        val intent = mock<Intent>()
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_SERIES_ID, -1)).thenReturn(42)
        whenever(intent.getIntExtra(SeriesDeepLink.EXTRA_PROFILE_ID, -1)).thenReturn(-1)

        assertNull(SeriesDeepLink.extract(intent))
    }
}
