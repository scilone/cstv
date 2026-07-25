package com.cstv.app.data.remote.gson

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Les panels Xtream sérialisent régulièrement un objet vide en tableau vide :
 * `"info": []`, `"movie_data": []`, `"episodes": []`. Gson lève alors
 * `Expected BEGIN_OBJECT but was BEGIN_ARRAY` et toute la réponse est perdue,
 * alors que le champ signifie simplement "pas de métadonnée".
 *
 * Cette fabrique traite ce cas comme un champ absent (`null`), ce que les
 * repositories savent déjà gérer via leurs valeurs de repli. Les types qui
 * attendent légitimement un tableau (collections, tableaux, `JsonElement`) sont
 * laissés à leur adaptateur normal.
 */
class EmptyArrayAsAbsentTypeAdapterFactory : TypeAdapterFactory {

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        val expectsArray = Collection::class.java.isAssignableFrom(raw) || raw.isArray
        val isScalar = raw.isPrimitive || raw == String::class.java ||
            Number::class.java.isAssignableFrom(raw) || raw == Boolean::class.java
        // JsonElement conserve le JSON brut : c'est au repository de l'interpréter.
        val isRawJson = JsonElement::class.java.isAssignableFrom(raw)
        if (expectsArray || isScalar || isRawJson) return null

        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.skipValue()
                    return null
                }
                return delegate.read(reader)
            }
        }
    }
}
