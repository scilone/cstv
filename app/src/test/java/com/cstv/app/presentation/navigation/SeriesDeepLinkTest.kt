package com.cstv.app.presentation.navigation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SeriesDeepLinkTest {

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
