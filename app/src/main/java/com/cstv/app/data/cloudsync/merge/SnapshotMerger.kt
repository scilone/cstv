package com.cstv.app.data.cloudsync.merge

import com.cstv.app.data.cloudsync.NamespaceSnapshot
import com.google.gson.JsonElement

/** Pure three-way merge. Callers decide whether local is an outstanding user intent. */
class SnapshotMerger {
    fun merge(base: NamespaceSnapshot?, local: NamespaceSnapshot, remote: NamespaceSnapshot, localIntentWins: Boolean): NamespaceSnapshot {
        val merged = buildMap<String, JsonElement> {
            (base?.objects.orEmpty().keys + local.objects.keys + remote.objects.keys).forEach { key ->
                val before = base?.objects?.get(key)
                val left = local.objects[key]
                val right = remote.objects[key]
                val result = when {
                    same(left, right) -> left
                    same(left, before) -> right
                    same(right, before) -> left
                    before == null -> resolve(local.namespace, left, right, localIntentWins = false)
                    else -> resolve(local.namespace, left, right, localIntentWins)
                }
                if (result != null) put(key, result)
            }
        }
        return local.copy(objects = merged)
    }

    private fun resolve(namespace: String, local: JsonElement?, remote: JsonElement?, localIntentWins: Boolean): JsonElement? {
        if (local == null || remote == null) return if (localIntentWins) local else remote ?: local
        val timestamp = when (namespace) { "playback" -> "lastAccessedAt"; "recently-watched-live" -> "watchedAt"; "series-watch-state" -> "updatedAt"; else -> null }
        if (timestamp == null) return if (localIntentWins) local else remote
        val localTime = local.asJsonObject.get(timestamp)?.asLong ?: Long.MIN_VALUE
        val remoteTime = remote.asJsonObject.get(timestamp)?.asLong ?: Long.MIN_VALUE
        val winner = if (localTime >= remoteTime) local else remote
        if (namespace != "series-watch-state") return winner

        // A more recent state wins its descriptive fields, but no known/notified
        // episode may regress when both installations advanced independently.
        return winner.deepCopy().asJsonObject.apply {
            listOf("lastKnownSeason", "lastKnownEpisode", "lastNotifiedSeason", "lastNotifiedEpisode").forEach { field ->
                val largest = maxOf(
                    local.asJsonObject.get(field)?.asInt ?: 0,
                    remote.asJsonObject.get(field)?.asInt ?: 0
                )
                addProperty(field, largest)
            }
        }
    }
    private fun same(one: JsonElement?, two: JsonElement?): Boolean = one?.toString() == two?.toString()
}
