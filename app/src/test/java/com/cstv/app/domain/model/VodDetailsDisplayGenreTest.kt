package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodDetailsDisplayGenreTest {

    private fun detailsWithGenre(genre: String) = VodDetails(
        streamId = 1,
        name = "Film",
        director = UNKNOWN_METADATA,
        actors = UNKNOWN_METADATA,
        releaseDate = UNKNOWN_METADATA,
        genre = genre,
        plot = "",
        rating = "0",
        coverBig = null,
        containerExtension = "mp4"
    )

    @Test
    fun `exposes a real genre as is`() {
        assertEquals("Action, Thriller", detailsWithGenre("Action, Thriller").displayGenre)
    }

    @Test
    fun `hides the unknown sentinel left by the repositories`() {
        // Chemins concernés : reprise de lecture depuis l'accueil et fiche
        // reconstruite depuis le cache, tous deux sans appel `get_vod_info`.
        assertNull(detailsWithGenre(UNKNOWN_METADATA).displayGenre)
        assertNull(detailsWithGenre("inconnu").displayGenre)
    }

    @Test
    fun `hides an empty or blank genre`() {
        assertNull(detailsWithGenre("").displayGenre)
        assertNull(detailsWithGenre("   ").displayGenre)
    }

    @Test
    fun `trims the surrounding whitespace of a real genre`() {
        assertEquals("Comédie", detailsWithGenre("  Comédie  ").displayGenre)
    }
}
