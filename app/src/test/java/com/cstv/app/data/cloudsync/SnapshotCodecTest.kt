package com.cstv.app.data.cloudsync

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCodecTest {
    private val codec = SnapshotCodec(Gson())

    @Test fun `round trip preserves empty namespace`() {
        val snapshot = NamespaceSnapshot(1, "favorites", emptyMap())
        assertEquals(SnapshotDecodeResult.Success(snapshot), codec.decode((codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes))
    }

    @Test fun `round trip preserves utf8 payload`() {
        val snapshot = NamespaceSnapshot(1, "favorites", mapOf("movie:7" to JsonParser.parseString("\"Café 🎬\"")))
        assertEquals(SnapshotDecodeResult.Success(snapshot), codec.decode((codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes))
    }

    @Test fun `invalid gzip is incompatible rather than throwing`() {
        assertTrue(codec.decode(byteArrayOf(1, 2, 3)) is SnapshotDecodeResult.Malformed)
    }

    @Test fun `newer schema is incompatible without throwing`() {
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION + 1, "favorites", emptyMap())
        assertTrue(codec.decode((codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes) is SnapshotDecodeResult.Incompatible)
    }

    @Test fun `oversized compressed document is refused before upload`() {
        val tinyCodec = SnapshotCodec(Gson(), maxBytes = 1)
        assertTrue(tinyCodec.encode(NamespaceSnapshot(1, "favorites", emptyMap())) is SnapshotEncodeResult.TooLarge)
    }
}
