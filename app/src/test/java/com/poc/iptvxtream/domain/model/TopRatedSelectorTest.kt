package com.poc.iptvxtream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TopRatedSelectorTest {

    data class DummyItem(val id: Int, val rating: String?, val added: String?)

    @Test
    fun test_selectTop10_handlesNullsAndInvalidsDefensively() {
        val items = listOf(
            DummyItem(1, "9.5", "100"),
            DummyItem(2, "invalid_rating", "200"),
            DummyItem(3, null, "300"),
            DummyItem(4, "8.5", "invalid_added"),
            DummyItem(5, "8.0", null)
        )

        val selected = TopRatedSelector.selectTop10(
            items = items,
            ratingExtractor = { it.rating },
            addedExtractor = { it.added }
        )

        // Item 1 (9.5, added 100) and Item 4 (8.5, invalid_added -> 0) and Item 5 (8.0, null -> 0) are rating >= 8.0.
        // Item 1 has added 100, which is larger than 0.
        // Let's assert the correct items are returned.
        assertEquals(3, selected.size)
        assertEquals(1, selected[0].id) // 9.5 rating >= 8.0, highest added (100)
        assertEquals(4, selected[1].id) // 8.5 rating >= 8.0, added 0
        assertEquals(5, selected[2].id) // 8.0 rating >= 8.0, added 0
    }

    @Test
    fun test_selectTop10_filtersStrictlyRatingEightOrAbove() {
        val items = listOf(
            DummyItem(1, "9.5", "1000"), // OK
            DummyItem(2, "8.5", "500"),  // OK
            DummyItem(3, "8.0", "100"),  // OK (included)
            DummyItem(4, "7.9", "900"),  // Excluded (< 8.0)
            DummyItem(5, "7.2", "400"),  // Excluded
            DummyItem(6, "6.5", "800"),  // Excluded
            DummyItem(7, "5.5", "700"),  // Excluded
            DummyItem(8, "4.5", "600"),  // Excluded
            DummyItem(9, "0.0", "1200"), // Excluded
            DummyItem(10, null, "1100"), // Excluded
            DummyItem(11, "8.2", "1500") // OK
        )

        val selected = TopRatedSelector.selectTop10(
            items = items,
            ratingExtractor = { it.rating },
            addedExtractor = { it.added }
        )

        // Only items with rating >= 8.0 are returned:
        // Items 11 (1500), 1 (1000), 2 (500), 3 (100).
        // Sorted by added descending.
        assertEquals(4, selected.size)
        assertEquals(11, selected[0].id) // added 1500
        assertEquals(1, selected[1].id)  // added 1000
        assertEquals(2, selected[2].id)  // added 500
        assertEquals(3, selected[3].id)  // added 100
    }
}
