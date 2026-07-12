package com.poc.iptvxtream.data.remote.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class SafeIntAdapter : TypeAdapter<Int>() {
    override fun write(out: JsonWriter, value: Int?) {
        out.value(value)
    }

    override fun read(reader: JsonReader): Int {
        val token = reader.peek()
        if (token == JsonToken.NULL) {
            reader.nextNull()
            return 0
        }
        if (token == JsonToken.STRING) {
            val s = reader.nextString()
            return s.toIntOrNull() ?: 0
        }
        if (token == JsonToken.NUMBER) {
            return reader.nextInt()
        }
        if (token == JsonToken.BOOLEAN) {
            return if (reader.nextBoolean()) 1 else 0
        }
        reader.skipValue()
        return 0
    }
}
