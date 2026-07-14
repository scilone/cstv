package com.poc.iptvxtream.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Régression : certains panels Xtream nomment le timestamp de fin de
 * programme "stop_timestamp" plutôt que "end_timestamp" dans la réponse
 * get_short_epg. Sans alias Gson, ce champ restait null -> parsé à 0 par
 * LiveTvRepositoryImpl.parseJsonTimestamp -> LiveEpgProgram.getProgressFraction()
 * voyait "now > 0" toujours vrai -> jauge de progression figée à 100%.
 */
class EpgDtoTest {

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
