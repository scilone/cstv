package com.cstv.app.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Régression : certains panels Xtream nomment le timestamp de fin de
 * programme "stop_timestamp" plutôt que "end_timestamp" dans la réponse
 * get_short_epg. Sans alias Gson, ce champ restait null -> parsé à 0 par
 * LiveTvRepositoryImpl.parseJsonTimestamp -> LiveEpgProgram.getProgressFraction()
 * voyait "now > 0" toujours vrai -> jauge de progression figée à 100%.
 */
class EpgDtoTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private val gson = Gson()

    @Test
    fun test_endTimestamp_parsesFromEndTimestampKey() {
        val json = """{"title":"T","description":"D","start_timestamp":1000,"end_timestamp":2000}"""

        val dto = gson.fromJson(json, EpgListingDto::class.java)

        assertNotNull(dto.endTimestamp)
        assertEquals(2000, dto.endTimestamp!!.asLong)
    }

    @Test
    fun test_endTimestamp_parsesFromStopTimestampAlias() {
        val json = """{"title":"T","description":"D","start_timestamp":1000,"stop_timestamp":2000}"""

        val dto = gson.fromJson(json, EpgListingDto::class.java)

        assertNotNull(dto.endTimestamp)
        assertEquals(2000, dto.endTimestamp!!.asLong)
    }

    @Test
    fun test_startEnd_parseFromStartStopAlias() {
        val json = """{"title":"T","description":"D","start":"2026-01-01 20:00:00","stop":"2026-01-01 21:00:00"}"""

        val dto = gson.fromJson(json, EpgListingDto::class.java)

        assertEquals("2026-01-01 20:00:00", dto.start)
        assertEquals("2026-01-01 21:00:00", dto.end)
    }
}
