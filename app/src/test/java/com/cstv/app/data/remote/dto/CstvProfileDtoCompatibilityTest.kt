package com.cstv.app.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F44, tâche 9 (non-régression) : un ancien backend non déployé avec la
 * colonne `max_age_rating` ne renvoie jamais le champ `maxAgeRating` — le
 * client doit lire `null` (profil non bridé), jamais planter ni bloquer
 * l'ordre de déploiement backend-puis-app (§9.3).
 */
class CstvProfileDtoCompatibilityTest {

    private val gson = Gson()

    @Test
    fun `a profile JSON without maxAgeRating parses as unbridged`() {
        val json = """{"id":"p1","name":"Nico","avatarId":0,"createdAt":"2026-01-01T00:00:00Z"}"""

        val dto = gson.fromJson(json, CstvProfileDto::class.java)

        assertNull(dto.maxAgeRating)
    }

    @Test
    fun `a profile JSON with an explicit maxAgeRating parses it`() {
        val json = """{"id":"p1","name":"Nico","avatarId":0,"createdAt":"2026-01-01T00:00:00Z","maxAgeRating":12}"""

        val dto = gson.fromJson(json, CstvProfileDto::class.java)

        assertEquals(12, dto.maxAgeRating)
    }

    @Test
    fun `an explicit null maxAgeRating parses as unbridged, not a parse error`() {
        val json = """{"id":"p1","name":"Nico","avatarId":0,"createdAt":"2026-01-01T00:00:00Z","maxAgeRating":null}"""

        val dto = gson.fromJson(json, CstvProfileDto::class.java)

        assertNull(dto.maxAgeRating)
    }
}
