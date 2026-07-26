package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerLookupMatcherTest {

    @Test
    fun select_normalizesCatalogTagsAndKeepsFirstTmdbResultOnTie() {
        val match = TrailerLookupMatcher.select(
            "Le Film [MULTI] 1080p",
            2020,
            listOf(
                TrailerLookupMatcher.Candidate(1, "Le Film", 2020),
                TrailerLookupMatcher.Candidate(2, "Le Film", 2020)
            )
        )

        assertEquals(1, match?.id)
    }

    @Test
    fun select_rejectsCandidateOutsideOneYearTolerance() {
        val match = TrailerLookupMatcher.select(
            "Le Film",
            2020,
            listOf(TrailerLookupMatcher.Candidate(1, "Le Film", 2022))
        )

        assertNull(match)
    }
}
