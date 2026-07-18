package com.cstv.app.presentation.player

import com.cstv.app.presentation.vod.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class TrackSelectionTest {

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
