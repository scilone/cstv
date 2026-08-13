package com.cstv.app.data.cloudsync

import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.JsonElement

/**
 * Per-namespace item caps and snapshot slimming enforced at the serialization boundary (T19).
 *
 * The backend only bounds opaque bytes per account (T14: 20 MiB/account, 1 MiB/blob); it never
 * reads inside a snapshot, so it cannot count favourites or watched channels. These caps keep each
 * pushed snapshot small and the lists tidy. They are NOT a security boundary — a modified client can
 * bypass them, and the backend byte quota still holds. See [[T14]] / T19.
 */
object SnapshotLimits {
    const val FAVORITES = 500
    const val PLAYBACK = 10_000
    const val RECENTLY_WATCHED_LIVE = 20

    /**
     * Fields dropped from every serialized playback item. `plot` is re-fetchable from the catalogue
     * and dominates the blob size (10 000 positions: ~0.80 MiB with plot, ~0.30 MiB without), so it
     * is never synced. Title and cover stay: the resume tile needs them without a catalogue lookup.
     */
    val PLAYBACK_STRIPPED_FIELDS: Set<String> = setOf("plot")
}

/**
 * Keeps the [limit] most recent items by [timestamp] (descending). Returns the list unchanged when
 * already within the limit, preserving its order. Pure — unit-tested without Room.
 */
fun <T> List<T>.capMostRecent(limit: Int, timestamp: (T) -> Long): List<T> =
    if (size <= limit) this else sortedByDescending(timestamp).take(limit)

/** Fields removed from a serialized item of [namespace]: always the routing `profileId`, plus the
 *  re-fetchable playback fields. Pure. */
fun SyncNamespace.strippedFields(): Set<String> = when (this) {
    SyncNamespace.PLAYBACK -> setOf("profileId") + SnapshotLimits.PLAYBACK_STRIPPED_FIELDS
    else -> setOf("profileId")
}

/** Returns a copy of this JSON object without [fields]. Pure. A non-object element (never produced
 *  by `RoomSnapshotSerializer`, but reachable through `normalized()` on a merge result of
 *  unknown provenance) is returned unchanged rather than crashing: there is no field to remove
 *  from something that isn't an object. */
fun JsonElement.withoutFields(fields: Set<String>): JsonElement =
    if (isJsonObject) asJsonObject.deepCopy().apply { fields.forEach { remove(it) } } else this

/** Item cap enforced on [namespace], or null when this namespace has none. */
fun SyncNamespace.itemLimit(): Int? = when (this) {
    SyncNamespace.FAVORITES -> SnapshotLimits.FAVORITES
    SyncNamespace.PLAYBACK -> SnapshotLimits.PLAYBACK
    SyncNamespace.RECENTLY_WATCHED_LIVE -> SnapshotLimits.RECENTLY_WATCHED_LIVE
    else -> null
}

/** JSON field [itemLimit] pruning orders on (descending = most recent first), or null when
 *  [itemLimit] is also null. Mirrors the timestamp fields `RoomSnapshotSerializer` sorts by. */
fun SyncNamespace.timestampField(): String? = when (this) {
    SyncNamespace.FAVORITES -> "addedAt"
    SyncNamespace.PLAYBACK -> "lastAccessedAt"
    SyncNamespace.RECENTLY_WATCHED_LIVE -> "watchedAt"
    else -> null
}

/**
 * Re-applies the T19 per-item cap and field stripping to an already-built [NamespaceSnapshot].
 * Pure and idempotent — safe to call on a snapshot that was already normalized.
 *
 * `RoomSnapshotSerializer.snapshot()` only normalizes the local snapshot it builds fresh from
 * Room; the result of a three-way merge (disjoint local/remote sets, or a remote object that still
 * carries a stripped field from an older client) is not itself bounded. Call this on the merged
 * result before it reaches `serializer.apply()` or `codec.encode()` — see T19-R1.
 */
fun NamespaceSnapshot.normalized(namespace: SyncNamespace): NamespaceSnapshot {
    val limit = namespace.itemLimit()
    val timestampField = namespace.timestampField()
    val capped = if (limit != null && timestampField != null && objects.size > limit) {
        objects.entries
            .sortedByDescending { (_, value) ->
                value.takeIf { it.isJsonObject }?.asJsonObject?.get(timestampField)?.asLong ?: Long.MIN_VALUE
            }
            .take(limit)
            .associate { it.key to it.value }
    } else {
        objects
    }
    val strip = namespace.strippedFields()
    return copy(objects = capped.mapValues { (_, value) -> value.withoutFields(strip) })
}
