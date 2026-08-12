package com.cstv.app.data.cloudsync

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SnapshotCodec(private val gson: Gson, private val maxBytes: Int = MAX_OBJECT_SIZE_BYTES) {
    fun encode(snapshot: NamespaceSnapshot): SnapshotEncodeResult {
        val json = JsonObject().apply { addProperty("schemaVersion", snapshot.schemaVersion); addProperty("namespace", snapshot.namespace); add("objects", gson.toJsonTree(snapshot.objects)) }
        return ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(gson.toJson(json).toByteArray(Charsets.UTF_8)) }
            out.toByteArray().let { if (it.size <= maxBytes) SnapshotEncodeResult.Success(it) else SnapshotEncodeResult.TooLarge }
        }
    }
    fun decode(bytes: ByteArray): SnapshotDecodeResult = runCatching {
        if (bytes.size > maxBytes) return SnapshotDecodeResult.TooLarge
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = gson.fromJson(text, JsonObject::class.java)
        val version = root.get("schemaVersion")?.asInt ?: return SnapshotDecodeResult.Malformed
        val namespace = root.get("namespace")?.asString ?: return SnapshotDecodeResult.Malformed
        if (version > SCHEMA_VERSION) SnapshotDecodeResult.Incompatible
        else if (!root.has("objects")) SnapshotDecodeResult.Malformed
        else SnapshotDecodeResult.Success(NamespaceSnapshot(version, namespace, root.getAsJsonObject("objects").entrySet().associate { it.key to it.value }))
    }.getOrElse { SnapshotDecodeResult.Malformed }
    companion object { const val SCHEMA_VERSION = 1; const val MAX_OBJECT_SIZE_BYTES = 1_048_576 }
}
