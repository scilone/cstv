package com.cstv.app.data.cloudsync

import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20 §4.6 "Lecture d'un ancien document v1 → v2" : couvre [SnapshotUpgrader] en isolation, comme
 * fonction pure — `RoomSnapshotSerializerTest`/`CloudSyncManagerTest`/
 * `CloudSyncManagerMergeNormalizationTest` l'exercent déjà indirectement au travers du câblage,
 * mais aucun test ne nommait jusqu'ici chacune des trois règles de normalisation elles-mêmes.
 */
class SnapshotUpgraderTest {
    private fun item(json: String) = JsonParser.parseString(json)

    @Test
    fun `a namespace already at its current version is untouched, same instance`() {
        val snapshot = NamespaceSnapshot(SyncNamespace.FAVORITES.schemaVersion, "favorites", mapOf("movie:1" to item("""{"addedAt":1}""")))

        assertSame(snapshot, SnapshotUpgrader.upgrade(snapshot))
    }

    @Test
    fun `an unrecognized namespace name is never touched, whatever its declared version`() {
        val snapshot = NamespaceSnapshot(0, "unknown-namespace", mapOf("movie:1" to item("""{"value":5}""")))

        assertSame(snapshot, SnapshotUpgrader.upgrade(snapshot))
    }

    @Test
    fun `a recognized but never-versioned namespace only has its version bumped, content untouched`() {
        val snapshot = NamespaceSnapshot(0, "ratings", mapOf("movie:1" to item("""{"value":5}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(1, upgraded.schemaVersion)
        assertEquals(snapshot.objects, upgraded.objects)
    }

    @Test
    fun `favorites v1 -- key already kind colon id, only addedAt survives`() {
        val snapshot = NamespaceSnapshot(
            1, "favorites",
            mapOf("live:1042" to item("""{"addedAt":100,"name":"TF1","cover":"c.jpg","categoryId":"5"}""")),
        )

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(2, upgraded.schemaVersion)
        val obj = upgraded.objects.getValue("live:1042").asJsonObject
        assertEquals(setOf("addedAt"), obj.keySet())
        assertEquals(100L, obj.get("addedAt").asLong)
    }

    @Test
    fun `favorites v1 item missing addedAt is dropped, not defaulted`() {
        val snapshot = NamespaceSnapshot(1, "favorites", mapOf("live:1" to item("""{"name":"TF1"}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertTrue(upgraded.objects.isEmpty())
    }

    @Test
    fun `playback v1 -- seriesId present deduces episode, key becomes episode colon streamId`() {
        val snapshot = NamespaceSnapshot(
            1, "playback",
            mapOf("99213" to item("""{"seriesId":50,"positionMs":60000,"durationMs":2700000,"lastAccessedAt":10,"plot":"résumé"}""")),
        )

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(2, upgraded.schemaVersion)
        val obj = upgraded.objects.getValue("episode:99213").asJsonObject
        assertEquals(setOf("positionMs", "durationMs", "lastAccessedAt"), obj.keySet())
    }

    @Test
    fun `playback v1 -- type series also deduces episode, without a seriesId field`() {
        val snapshot = NamespaceSnapshot(1, "playback", mapOf("42" to item("""{"type":"series","positionMs":1,"durationMs":2,"lastAccessedAt":3}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(setOf("episode:42"), upgraded.objects.keys)
    }

    @Test
    fun `playback v1 -- type movie deduces movie`() {
        val snapshot = NamespaceSnapshot(1, "playback", mapOf("815" to item("""{"type":"movie","positionMs":1,"durationMs":2,"lastAccessedAt":3}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(setOf("movie:815"), upgraded.objects.keys)
    }

    /**
     * T20 §4.6 : « une référence impossible à comprendre n'est jamais appliquée » — sans
     * `seriesId`, ni `type`, l'item est ignoré plutôt que rattaché au hasard à un film.
     */
    @Test
    fun `playback v1 -- neither seriesId nor type present drops the item, never guesses movie`() {
        val snapshot = NamespaceSnapshot(1, "playback", mapOf("555" to item("""{"positionMs":1,"durationMs":2,"lastAccessedAt":3}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertTrue(upgraded.objects.isEmpty())
    }

    @Test
    fun `playback v1 -- an unparseable key is dropped rather than crashing the upgrade`() {
        val snapshot = NamespaceSnapshot(1, "playback", mapOf("not-a-stream-id" to item("""{"type":"movie","positionMs":1,"durationMs":2,"lastAccessedAt":3}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertTrue(upgraded.objects.isEmpty())
    }

    @Test
    fun `recently-watched-live v1 -- bare streamId key becomes live colon streamId, only watchedAt survives`() {
        val snapshot = NamespaceSnapshot(1, "recently-watched-live", mapOf("1042" to item("""{"watchedAt":400,"name":"TF1"}""")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertEquals(2, upgraded.schemaVersion)
        val obj = upgraded.objects.getValue("live:1042").asJsonObject
        assertEquals(setOf("watchedAt"), obj.keySet())
    }

    @Test
    fun `a non-object item (plain JsonPrimitive fixture) is dropped, never crashes the upgrade`() {
        val snapshot = NamespaceSnapshot(1, "favorites", mapOf("live:1" to item("\"unexpected-primitive\"")))

        val upgraded = SnapshotUpgrader.upgrade(snapshot)

        assertFalse(upgraded.objects.containsKey("live:1"))
    }
}
