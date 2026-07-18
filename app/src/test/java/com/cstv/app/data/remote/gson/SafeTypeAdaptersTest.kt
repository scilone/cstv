package com.cstv.app.data.remote.gson

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SafeTypeAdaptersTest {

    private lateinit var gson: com.google.gson.Gson

    @Before
    fun setUp() {
        gson = GsonBuilder()
            .registerTypeAdapter(Int::class.java, SafeIntAdapter())
            .registerTypeAdapter(Int::class.javaPrimitiveType, SafeIntAdapter())
            .registerTypeAdapter(Long::class.java, SafeLongAdapter())
            .registerTypeAdapter(Long::class.javaPrimitiveType, SafeLongAdapter())
            .create()
    }

    private data class TestModel(
        val intValue: Int,
        val longValue: Long
    )

    @Test
    fun testIntAdapter_withNormalInt_parsesCorrectly() {
        val json = "{\"intValue\": 42, \"longValue\": 1000}"
        val model = gson.fromJson(json, TestModel::class.java)
        assertEquals(42, model.intValue)
        assertEquals(1000L, model.longValue)
    }

    @Test
    fun testIntAdapter_withStringInt_parsesCorrectly() {
        val json = "{\"intValue\": \"42\", \"longValue\": \"1000\"}"
        val model = gson.fromJson(json, TestModel::class.java)
        assertEquals(42, model.intValue)
        assertEquals(1000L, model.longValue)
    }

    @Test
    fun testIntAdapter_withNull_parsesAsZero() {
        val json = "{\"intValue\": null, \"longValue\": null}"
        val model = gson.fromJson(json, TestModel::class.java)
        assertEquals(0, model.intValue)
        assertEquals(0L, model.longValue)
    }

    @Test
    fun testIntAdapter_withBoolean_parsesCorrectly() {
        val json = "{\"intValue\": true, \"longValue\": true}"
        val model = gson.fromJson(json, TestModel::class.java)
        assertEquals(1, model.intValue)
        assertEquals(1L, model.longValue)
    }

    @Test
    fun testIntAdapter_withMalformedString_parsesAsZero() {
        val json = "{\"intValue\": \"invalid_int\", \"longValue\": \"invalid_long\"}"
        val model = gson.fromJson(json, TestModel::class.java)
        assertEquals(0, model.intValue)
        assertEquals(0L, model.longValue)
    }
}
