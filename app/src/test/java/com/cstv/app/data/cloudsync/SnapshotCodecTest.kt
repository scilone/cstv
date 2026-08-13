package com.cstv.app.data.cloudsync

import com.cstv.app.domain.sync.SyncNamespace
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

    // T20: the ceiling is per-namespace (SyncNamespace.schemaVersion), not the global constant --
    // favorites is at v2, so a v3 document (not v2) is what actually exceeds it now.
    @Test fun `newer schema is incompatible without throwing`() {
        val snapshot = NamespaceSnapshot(SyncNamespace.FAVORITES.schemaVersion + 1, "favorites", emptyMap())
        assertTrue(codec.decode((codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes) is SnapshotDecodeResult.Incompatible)
    }

    @Test fun `an unrecognized namespace name falls back to the legacy global ceiling`() {
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION + 1, "unknown-namespace", emptyMap())
        assertTrue(codec.decode((codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes) is SnapshotDecodeResult.Incompatible)
    }

    @Test fun `oversized compressed document is refused before upload`() {
        val tinyCodec = SnapshotCodec(Gson(), maxBytes = 1)
        assertTrue(tinyCodec.encode(NamespaceSnapshot(1, "favorites", emptyMap())) is SnapshotEncodeResult.TooLarge)
    }
}
