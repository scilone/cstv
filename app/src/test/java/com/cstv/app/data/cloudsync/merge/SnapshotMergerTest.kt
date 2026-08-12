package com.cstv.app.data.cloudsync.merge

import com.cstv.app.data.cloudsync.NamespaceSnapshot
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapshotMergerTest {
    private val merger = SnapshotMerger()
    private fun json(value: String) = JsonParser.parseString(value)

    @Test fun `local deletion is retained when remote is unchanged`() {
        val base = NamespaceSnapshot(1, "favorites", mapOf("movie:1" to json("{\"id\":1}")))
        val local = base.copy(objects = emptyMap())
        val merged = merger.merge(base, local, base, localIntentWins = true)
        assertFalse(merged.objects.containsKey("movie:1"))
    }

    @Test fun `playback chooses most recent access even at a lower position`() {
        val base = NamespaceSnapshot(1, "playback", emptyMap())
        val local = base.copy(objects = mapOf("1" to json("{\"lastAccessedAt\":20,\"positionMs\":1}")))
        val remote = base.copy(objects = mapOf("1" to json("{\"lastAccessedAt\":10,\"positionMs\":99}")))
        assertEquals(1, merger.merge(base, local, remote, true).objects.getValue("1").asJsonObject.get("positionMs").asInt)
    }

    @Test fun `series state never regresses known episode after a conflict`() {
        val base = NamespaceSnapshot(1, "series-watch-state", mapOf("7" to json("{\"updatedAt\":1,\"lastKnownEpisode\":1}")))
        val local = base.copy(objects = mapOf("7" to json("{\"updatedAt\":3,\"lastKnownEpisode\":2,\"lastNotifiedEpisode\":2}")))
        val remote = base.copy(objects = mapOf("7" to json("{\"updatedAt\":2,\"lastKnownEpisode\":4,\"lastNotifiedEpisode\":1}")))
        val merged = merger.merge(base, local, remote, true).objects.getValue("7").asJsonObject
        assertEquals(4, merged.get("lastKnownEpisode").asInt)
        assertEquals(2, merged.get("lastNotifiedEpisode").asInt)
    }
}
