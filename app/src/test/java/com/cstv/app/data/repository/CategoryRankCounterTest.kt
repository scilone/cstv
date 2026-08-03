package com.cstv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRankCounterTest {

    @Test
    fun countsEachCategoryIndependentlyFromZero() {
        val counter = CategoryRankCounter()

        // Réponse « Tout » type : les catégories arrivent entrelacées, sans
        // regroupement garanti par le panel.
        assertEquals(0, counter.next("69"))
        assertEquals(0, counter.next("78"))
        assertEquals(1, counter.next("69"))
        assertEquals(1, counter.next("78"))
        assertEquals(0, counter.next("121"))
        assertEquals(2, counter.next("69"))
    }
}
