package com.cstv.app.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTrendingDtoTest {

    private val gson = Gson()

    @Test
    fun test_tmdbTrendingDto_parsesSuccessfully() {
        val json = """
            {
                "results": [
                    {
                        "id": 12345,
                        "title": "Movie Title",
                        "media_type": "movie",
                        "poster_path": "/path.jpg",
                        "release_date": "2026-07-19"
                    },
                    {
                        "id": "67890",
                        "name": "TV Show Name",
                        "media_type": "tv",
                        "poster_path": null,
                        "first_air_date": "2025-01-01"
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, TmdbTrendingResponseDto::class.java)

        assertNotNull(response)
        assertNotNull(response.results)
        assertEquals(2, response.results!!.size)

        val item1 = response.results!![0]
        assertEquals(12345.0, item1.id) // Gson parses Any numbers as Double by default
        assertEquals("Movie Title", item1.title)
        assertNull(item1.name)
        assertEquals("movie", item1.mediaType)
        assertEquals("/path.jpg", item1.posterPath)
        assertEquals("2026-07-19", item1.releaseDate)

        val item2 = response.results!![1]
        assertEquals("67890", item2.id)
        assertEquals("TV Show Name", item2.name)
        assertNull(item2.title)
        assertEquals("tv", item2.mediaType)
        assertNull(item2.posterPath)
        assertEquals("2025-01-01", item2.firstAirDate)
    }

    @Test
    fun test_tmdbTrendingDto_handlesDirtyDataGracefully() {
        val json = """
            {
                "results": [
                    {
                        "id": null,
                        "title": null,
                        "media_type": null,
                        "poster_path": null
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, TmdbTrendingResponseDto::class.java)

        assertNotNull(response)
        assertNotNull(response.results)
        assertEquals(1, response.results!!.size)

        val item = response.results!![0]
        assertNull(item.id)
        assertNull(item.title)
        assertNull(item.mediaType)
        assertNull(item.posterPath)
    }
}
