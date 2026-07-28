package com.cstv.app.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendingTitleImageTest {

    private fun title(poster: String?, backdrop: String?) = TrendingTitle(
        tmdbId = 1,
        title = "Dune",
        isMovie = true,
        year = 2021,
        posterUrl = poster,
        backdropUrl = backdrop
    )

    @Test
    fun `landscapeImageUrl prefers backdrop when available`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/back.jpg",
            title("https://image.tmdb.org/t/p/w500/post.jpg", "https://image.tmdb.org/t/p/w780/back.jpg")
                .landscapeImageUrl()
        )
    }

    @Test
    fun `landscapeImageUrl falls back to poster when backdrop is missing or blank`() {
        val poster = "https://image.tmdb.org/t/p/w500/post.jpg"
        assertEquals(poster, title(poster, null).landscapeImageUrl())
        assertEquals(poster, title(poster, "").landscapeImageUrl())
    }

    @Test
    fun `landscapeImageUrl returns null when no image is known`() {
        assertNull(title(null, null).landscapeImageUrl())
        assertNull(title("", "").landscapeImageUrl())
    }

    @Test
    fun `cache entries written before the backdrop field remain readable`() {
        // Le cache persistant des tendances est du JSON Gson : les entrées
        // existantes ne portent pas `backdropUrl` et doivent rester exploitables.
        val legacyJson = """
            {"tmdbId":42,"title":"Old","isMovie":false,"year":2019,"posterUrl":"https://p/x.jpg"}
        """.trimIndent()

        val parsed = Gson().fromJson(legacyJson, TrendingTitle::class.java)

        assertNull(parsed.backdropUrl)
        assertEquals("https://p/x.jpg", parsed.landscapeImageUrl())
    }
}
