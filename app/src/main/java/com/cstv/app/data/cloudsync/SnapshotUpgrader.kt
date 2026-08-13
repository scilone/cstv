package com.cstv.app.data.cloudsync

import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * T20 (§4.6 "Lecture d'un ancien document v1 -> v2"): normalizes a decoded [NamespaceSnapshot] to
 * its namespace's current wire shape **before** it ever reaches
 * [com.cstv.app.data.cloudsync.merge.SnapshotMerger] -- both the remote document and the stored
 * merge base (`profile_sync_state.baseSnapshot`) must go through this, or the merger would compare
 * a v1 key against a v2 key and duplicate every item. A no-op for a namespace already at its
 * current version, or for the four namespaces whose format never changed. Denormalized fields from
 * an old document (name, cover, categoryId, plot, ...) are dropped here, never re-persisted or
 * re-emitted -- the next push is always in the current format.
 */
object SnapshotUpgrader {
    fun upgrade(snapshot: NamespaceSnapshot): NamespaceSnapshot {
        val namespace = SyncNamespace.fromWireName(snapshot.namespace) ?: return snapshot
        if (snapshot.schemaVersion >= namespace.schemaVersion) return snapshot
        val upgraded = when (namespace) {
            SyncNamespace.FAVORITES -> upgradeFavorites(snapshot.objects)
            SyncNamespace.PLAYBACK -> upgradePlayback(snapshot.objects)
            SyncNamespace.RECENTLY_WATCHED_LIVE -> upgradeRecentlyWatched(snapshot.objects)
            else -> snapshot.objects
        }
        return snapshot.copy(schemaVersion = namespace.schemaVersion, objects = upgraded)
    }

    /** v1 key was already `"kind:providerId"` (e.g. `"live:1042"`); only `addedAt` survives. */
    private fun upgradeFavorites(objects: Map<String, JsonElement>): Map<String, JsonElement> =
        objects.mapNotNull { (key, value) ->
            val obj = value.asObjectOrNull() ?: return@mapNotNull null
            val addedAt = obj.longOrNull("addedAt") ?: return@mapNotNull null
            key to JsonObject().apply { addProperty("addedAt", addedAt) }
        }.toMap()

    /**
     * v1 key was a bare `streamId`. `kind` is deduced from the object: `seriesId` present or
     * `type == "series"` -> `episode`; `type == "movie"` -> `movie`. If neither indicator is
     * present the item is dropped rather than guessed into the wrong bucket -- a reference
     * impossible to understand is never applied (T20 error-handling rule).
     */
    private fun upgradePlayback(objects: Map<String, JsonElement>): Map<String, JsonElement> =
        objects.mapNotNull { (key, value) ->
            val obj = value.asObjectOrNull() ?: return@mapNotNull null
            val streamId = key.toIntOrNull() ?: return@mapNotNull null
            val type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString
            val hasSeriesId = obj.get("seriesId")?.takeIf { !it.isJsonNull } != null
            val kind = when {
                hasSeriesId || type == "series" -> "episode"
                type == "movie" -> "movie"
                else -> return@mapNotNull null
            }
            val positionMs = obj.longOrNull("positionMs") ?: return@mapNotNull null
            val durationMs = obj.longOrNull("durationMs") ?: return@mapNotNull null
            val lastAccessedAt = obj.longOrNull("lastAccessedAt") ?: return@mapNotNull null
            "$kind:$streamId" to JsonObject().apply {
                addProperty("positionMs", positionMs)
                addProperty("durationMs", durationMs)
                addProperty("lastAccessedAt", lastAccessedAt)
            }
        }.toMap()

    /** v1 key was a bare `streamId` (namespace is always `live`). */
    private fun upgradeRecentlyWatched(objects: Map<String, JsonElement>): Map<String, JsonElement> =
        objects.mapNotNull { (key, value) ->
            val obj = value.asObjectOrNull() ?: return@mapNotNull null
            val streamId = key.toIntOrNull() ?: return@mapNotNull null
            val watchedAt = obj.longOrNull("watchedAt") ?: return@mapNotNull null
            "live:$streamId" to JsonObject().apply { addProperty("watchedAt", watchedAt) }
        }.toMap()

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.longOrNull(field: String): Long? = get(field)?.takeIf { !it.isJsonNull }?.asLong
}
