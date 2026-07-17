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
            DummyItem(5, "8.5", null)
        )

        val selected = TopRatedSelector.selectTop10(
            items = items,
            ratingExtractor = { it.rating },
            addedExtractor = { it.added }
        )

        // Item 1 (9.5, added 100) and Item 4 (8.5, invalid_added -> 0) and Item 5 (8.5, null -> 0) are rating > 8.0.
        // Item 1 has added 100, which is larger than 0.
        // Let's assert the correct items are returned.
        assertEquals(5, selected.size)
        assertEquals(1, selected[0].id) // 9.5 rating > 8.0, highest added
        assertEquals(4, selected[1].id) // 8.5 rating > 8.0
        assertEquals(5, selected[2].id) // 8.5 rating > 8.0
    }

    @Test
    fun test_selectTop10_appliesMultiTierFallbackAlgorithmCorrectly() {
        val items = listOf(
            DummyItem(1, "9.5", "1000"), // Tier 1 (>8)
            DummyItem(2, "8.5", "500"),  // Tier 1 (>8)
            DummyItem(3, "8.1", "100"),  // Tier 1 (>8)
            DummyItem(4, "7.9", "900"),  // Tier 2 (>7)
            DummyItem(5, "7.2", "400"),  // Tier 2 (>7)
            DummyItem(6, "6.5", "800"),  // Tier 3 (>6)
            DummyItem(7, "5.5", "700"),  // Tier 4 (>5)
            DummyItem(8, "4.5", "600"),  // Tier 5 (>0)
            DummyItem(9, "0.0", "1200"), // Tier 6 (rest / <=0)
            DummyItem(10, null, "1100"), // Tier 6 (rest)
            DummyItem(11, "8.2", "1500") // Tier 1 (>8) - newest!
        )

        val selected = TopRatedSelector.selectTop10(
            items = items,
            ratingExtractor = { it.rating },
            addedExtractor = { it.added }
        )

        // Let's analyze selected items and their order:
        // Tier 1 (>8): Items 11 (1500), 1 (1000), 2 (500), 3 (100).
        // Tier 2 (>7): Items 4 (900), 5 (400).
        // Tier 3 (>6): Item 6 (800).
        // Tier 4 (>5): Item 7 (700).
        // Tier 5 (>0): Item 8 (600).
        // Tier 6 (rest): Items 9 (1200), 10 (1100).
        // Total items is 11, selectTop10 should return exactly 10 in correct tier order.
        assertEquals(10, selected.size)
        assertEquals(11, selected[0].id)
        assertEquals(1, selected[1].id)
        assertEquals(2, selected[2].id)
        assertEquals(3, selected[3].id)
        assertEquals(4, selected[4].id)
        assertEquals(5, selected[5].id)
        assertEquals(6, selected[6].id)
        assertEquals(7, selected[7].id)
        assertEquals(8, selected[8].id)
        assertEquals(9, selected[9].id) // newest from Tier 6 is 9 (1200), item 10 (1100) is excluded as total limit is 10!
    }
}
