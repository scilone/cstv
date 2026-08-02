package com.cstv.app.presentation.player

import com.cstv.app.presentation.vod.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock

class TrackSelectionTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun test_trackInfo_isSupported_defaultsToTrue() {
        val group: androidx.media3.common.TrackGroup = mock()
        val track = TrackInfo(
            groupIndex = 0,
            trackIndex = 0,
            language = "fr",
            label = "French",
            isSelected = false,
            mediaTrackGroup = group
        )
        assertTrue(track.isSupported)
    }

    @Test
    fun test_trackInfo_isSupported_canBeFalse() {
        val group: androidx.media3.common.TrackGroup = mock()
        val track = TrackInfo(
            groupIndex = 0,
            trackIndex = 0,
            language = "en",
            label = "English",
            isSelected = false,
            mediaTrackGroup = group,
            isSupported = false
        )
        assertFalse(track.isSupported)
    }
}
