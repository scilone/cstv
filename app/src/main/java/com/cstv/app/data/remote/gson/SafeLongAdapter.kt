package com.cstv.app.data.remote.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class SafeLongAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) {
        out.value(value)
    }

    override fun read(reader: JsonReader): Long {
        val token = reader.peek()
        if (token == JsonToken.NULL) {
            reader.nextNull()
            return 0L
        }
        if (token == JsonToken.STRING) {
            val s = reader.nextString()
            return s.toLongOrNull() ?: 0L
        }
        if (token == JsonToken.NUMBER) {
            return reader.nextLong()
        }
        if (token == JsonToken.BOOLEAN) {
            return if (reader.nextBoolean()) 1L else 0L
        }
        reader.skipValue()
        return 0L
    }
}
