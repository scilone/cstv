package com.cstv.app.presentation.player.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaItemFactoryTest {

    @Test
    fun mimeType_hlsExtensions() {
        assertEquals("application/x-mpegurl", CastMediaItemFactory.mimeTypeFor("m3u8"))
        assertEquals("application/x-mpegurl", CastMediaItemFactory.mimeTypeFor("ts"))
        assertEquals("application/x-mpegurl", CastMediaItemFactory.mimeTypeFor(".M3U8"))
    }

    @Test
    fun mimeType_mp4Family() {
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor("mp4"))
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor("MOV"))
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor("m4v"))
    }

    @Test
    fun mimeType_matroskaAndWebm() {
        assertEquals("video/x-matroska", CastMediaItemFactory.mimeTypeFor("mkv"))
        assertEquals("video/webm", CastMediaItemFactory.mimeTypeFor("webm"))
    }

    @Test
    fun mimeType_unknownOrBlank_fallsBackToMp4() {
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor(null))
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor(""))
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor("avi"))
        assertEquals("video/mp4", CastMediaItemFactory.mimeTypeFor("   "))
    }
}
