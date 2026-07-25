package com.cstv.app.data.remote.gson

import com.cstv.app.data.remote.dto.SeriesInfoResponseDto
import com.cstv.app.data.remote.dto.VodInfoResponseDto
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyArrayAsAbsentTypeAdapterFactoryTest {

    private val gson = GsonBuilder()
        .registerTypeAdapterFactory(EmptyArrayAsAbsentTypeAdapterFactory())
        .registerTypeAdapter(Int::class.java, SafeIntAdapter())
        .registerTypeAdapter(Int::class.javaPrimitiveType, SafeIntAdapter())
        .create()

    @Test
    fun vodInfoSentAsEmptyArrayIsReadAsAbsent() {
        val response = gson.fromJson(
            """{"info":[],"movie_data":[]}""",
            VodInfoResponseDto::class.java
        )

        assertNull(response.info)
        assertNull(response.movieData)
    }

    @Test
    fun vodInfoSentAsObjectIsStillParsed() {
        val response = gson.fromJson(
            """{"info":{"name":"Film","genre":"Action"},"movie_data":{"container_extension":"mkv"}}""",
            VodInfoResponseDto::class.java
        )

        assertEquals("Film", response.info?.name)
        assertEquals("mkv", response.movieData?.containerExtension)
    }

    @Test
    fun seriesEpisodesSentAsEmptyArrayIsReadAsAbsent() {
        val response = gson.fromJson(
            """{"seasons":[],"episodes":[],"info":[]}""",
            SeriesInfoResponseDto::class.java
        )

        assertNull(response.episodes)
        assertNull(response.info)
        // Une saison est légitimement une liste : le tableau vide reste une liste vide.
        assertTrue(response.seasons?.isEmpty() == true)
    }

    @Test
    fun seriesEpisodesSentAsMapIsStillParsed() {
        val response = gson.fromJson(
            """{"episodes":{"1":[{"id":"42","episode_num":"1","title":"Pilote"}]}}""",
            SeriesInfoResponseDto::class.java
        )

        assertEquals("Pilote", response.episodes?.get("1")?.first()?.title)
    }
}
