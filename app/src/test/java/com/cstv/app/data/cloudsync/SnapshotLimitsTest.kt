package com.cstv.app.data.cloudsync

import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotLimitsTest {
    private data class Item(val id: Int, val ts: Long)

    @Test fun `capMostRecent returns the same list untouched when within the limit`() {
        val list = listOf(Item(1, 10), Item(2, 20), Item(3, 30))
        assertSame(list, list.capMostRecent(5) { it.ts })
    }

    @Test fun `capMostRecent keeps only the most recent items by timestamp`() {
        val list = listOf(Item(1, 10), Item(2, 50), Item(3, 20), Item(4, 40), Item(5, 30))
        val capped = list.capMostRecent(3) { it.ts }
        assertEquals(3, capped.size)
        assertEquals(listOf(2, 4, 5), capped.map { it.id })
    }

    @Test fun `capMostRecent at the exact limit keeps everything`() {
        val list = listOf(Item(1, 10), Item(2, 20))
        assertEquals(2, list.capMostRecent(2) { it.ts }.size)
    }

    @Test fun `playback strips profileId and plot, other namespaces strip only profileId`() {
        assertEquals(setOf("profileId", "plot"), SyncNamespace.PLAYBACK.strippedFields())
        assertEquals(setOf("profileId"), SyncNamespace.FAVORITES.strippedFields())
        assertEquals(setOf("profileId"), SyncNamespace.RECENTLY_WATCHED_LIVE.strippedFields())
    }

    @Test fun `withoutFields removes exactly the requested keys and keeps the rest`() {
        val gson = Gson()
        val element = gson.toJsonTree(
            mapOf("profileId" to 7, "plot" to "a long synopsis", "title" to "Interstellar", "positionMs" to 42),
        )
        val stripped = element.withoutFields(setOf("profileId", "plot")).asJsonObject

        assertFalse(stripped.has("profileId"))
        assertFalse(stripped.has("plot"))
        assertTrue(stripped.has("title"))
        assertEquals("Interstellar", stripped.get("title").asString)
        assertEquals(42, stripped.get("positionMs").asInt)
    }

    @Test fun `the configured caps match the product decision`() {
        assertEquals(500, SnapshotLimits.FAVORITES)
        assertEquals(10_000, SnapshotLimits.PLAYBACK)
        assertEquals(20, SnapshotLimits.RECENTLY_WATCHED_LIVE)
    }

    // T19-R1: `normalized()` is what CloudSyncManagerImpl now re-applies to a merged snapshot
    // before it reaches Room or the wire. These tests exercise it directly against real per-
    // namespace limits (SyncNamespace.itemLimit/timestampField), not a test-only stand-in cap.

    private fun recentlyWatchedItem(gson: Gson, streamId: Int, watchedAt: Long) =
        gson.toJsonTree(mapOf("streamId" to streamId, "watchedAt" to watchedAt, "profileId" to 7))

    @Test fun `normalized caps a namespace beyond its real backend limit, keeping the most recent`() {
        val gson = Gson()
        // recently-watched-live's cap is 20, small enough to build a genuinely disjoint
        // over-the-limit set here (unlike playback's 10 000).
        val objects = (1..25).associate { id -> id.toString() to recentlyWatchedItem(gson, id, watchedAt = id.toLong()) }
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, SyncNamespace.RECENTLY_WATCHED_LIVE.wireName, objects)

        val normalized = snapshot.normalized(SyncNamespace.RECENTLY_WATCHED_LIVE)

        assertEquals(20, normalized.objects.size)
        // Ids 6..25 have the 20 highest `watchedAt` values; 1..5 must be dropped.
        assertEquals((6..25).map { it.toString() }.toSet(), normalized.objects.keys)
    }

    @Test fun `normalized strips profileId and plot from every merged playback item`() {
        val gson = Gson()
        val stale = gson.toJsonTree(mapOf("streamId" to 1, "lastAccessedAt" to 10L, "plot" to "a synopsis", "profileId" to 7))
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, SyncNamespace.PLAYBACK.wireName, mapOf("1" to stale))

        val normalized = snapshot.normalized(SyncNamespace.PLAYBACK)

        val item = normalized.objects.getValue("1").asJsonObject
        assertFalse(item.has("plot"))
        assertFalse(item.has("profileId"))
        assertTrue(item.has("streamId"))
    }

    @Test fun `normalized is a no-op on an already-normalized snapshot`() {
        val gson = Gson()
        val objects = (1..5).associate { id -> id.toString() to recentlyWatchedItem(gson, id, watchedAt = id.toLong()) }
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, SyncNamespace.RECENTLY_WATCHED_LIVE.wireName, objects)

        val once = snapshot.normalized(SyncNamespace.RECENTLY_WATCHED_LIVE)
        val twice = once.normalized(SyncNamespace.RECENTLY_WATCHED_LIVE)

        assertEquals(once.objects.keys, twice.objects.keys)
    }

    @Test fun `normalized leaves an uncapped namespace's item count untouched`() {
        val gson = Gson()
        val objects = (1..3).associate { id -> id.toString() to gson.toJsonTree(mapOf("mediaId" to id, "profileId" to 7)) }
        val snapshot = NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, SyncNamespace.RATINGS.wireName, objects)

        val normalized = snapshot.normalized(SyncNamespace.RATINGS)

        assertEquals(3, normalized.objects.size)
        assertFalse(normalized.objects.getValue("1").asJsonObject.has("profileId"))
    }
}
