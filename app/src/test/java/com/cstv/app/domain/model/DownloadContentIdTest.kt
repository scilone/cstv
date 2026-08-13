package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadContentIdTest {
    @Test fun `movie and episode ids round trip`() {
        assertEquals(MediaKind.MOVIE to 12, DownloadContentId.parse(DownloadContentId.of(MediaKind.MOVIE, 12)))
        assertEquals(MediaKind.EPISODE to 34, DownloadContentId.parse(DownloadContentId.of(MediaKind.EPISODE, 34)))
    }

    @Test fun `non downloadable and malformed ids are rejected`() {
        assertNull(DownloadContentId.parse("series_2"))
        assertNull(DownloadContentId.parse("movie_x"))
        assertNull(DownloadContentId.parse("movie"))
    }
}
