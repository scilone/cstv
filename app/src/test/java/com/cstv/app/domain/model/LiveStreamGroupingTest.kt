package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Retour utilisateur F40 du 2026-08-18 : en mode automatique, une seule vignette par chaîne
 * (linkKey), la meilleure qualité déclarée servant de représentante — position du groupe et
 * chaînes sans variante inchangées.
 */
class LiveStreamGroupingTest {
    private fun stream(id: Int, name: String, linkKey: String = "", qualityTag: String? = null, num: Int = id) =
        LiveStream(id, name, null, null, num, "1", linkKey = linkKey, qualityTag = qualityTag)

    @Test
    fun `variants of the same channel collapse to their best quality`() {
        val streams = listOf(
            stream(1, "TF1 4K", linkKey = "tf1", qualityTag = "uhd_4k"),
            stream(2, "TF1 FHD", linkKey = "tf1", qualityTag = "fhd"),
            stream(3, "TF1 SD", linkKey = "tf1", qualityTag = "sd")
        )

        val collapsed = streams.collapsedForAutomaticQuality()

        assertEquals(listOf(1), collapsed.map { it.streamId })
    }

    @Test
    fun `a channel without a link key keeps its own tile`() {
        val streams = listOf(stream(1, "Chaîne locale", linkKey = ""))

        assertEquals(listOf(1), streams.collapsedForAutomaticQuality().map { it.streamId })
    }

    @Test
    fun `each group appears once, at the position of its first member, in original order`() {
        val streams = listOf(
            stream(1, "TF1 4K", linkKey = "tf1", qualityTag = "uhd_4k"),
            stream(2, "M6 HD", linkKey = "m6", qualityTag = "hd"),
            stream(3, "TF1 SD", linkKey = "tf1", qualityTag = "sd"),
            stream(4, "Chaîne locale")
        )

        val collapsed = streams.collapsedForAutomaticQuality()

        assertEquals(listOf(1, 2, 4), collapsed.map { it.streamId })
    }
}
