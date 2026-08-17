package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** F39 §7.6 : au maximum deux badges, langue puis qualité, jamais fabriqué. */
class MediaVersionBadgesTest {

    @Test
    fun `no tag produces no badge`() {
        assertEquals(emptyList<String>(), mediaVersionBadges(languageTag = null, qualityTag = null))
    }

    @Test
    fun `language only produces one badge`() {
        assertEquals(listOf("VF"), mediaVersionBadges(languageTag = MediaLanguage.VF.storageCode, qualityTag = null))
    }

    @Test
    fun `quality only produces one badge`() {
        assertEquals(listOf("4K"), mediaVersionBadges(languageTag = null, qualityTag = MediaQuality.UHD_4K.storageCode))
    }

    @Test
    fun `language then quality produces two badges in that order`() {
        assertEquals(
            listOf("VOSTFR", "HD"),
            mediaVersionBadges(languageTag = MediaLanguage.VOSTFR.storageCode, qualityTag = MediaQuality.HD.storageCode)
        )
    }

    @Test
    fun `unrecognized tag never fabricates a badge`() {
        assertEquals(emptyList<String>(), mediaVersionBadges(languageTag = "unknown", qualityTag = "unknown"))
    }

    @Test
    fun `uhd_4k storage code displays as 4K, not the enum name`() {
        assertEquals(listOf("4K"), mediaVersionBadges(languageTag = null, qualityTag = "uhd_4k"))
    }

    // F39-R7 (étape 7) : libellé centralisé des sélecteurs — jamais le nom Xtream brut, même
    // quand aucun attribut n'est détecté (décision produit étape 7 : repli fixe et localisé).

    @Test
    fun `F39-R7 - with both tags, the selector label joins the badges exactly like the list badges`() {
        assertEquals(
            "VOSTFR · HD",
            mediaVersionSelectorLabel(MediaLanguage.VOSTFR.storageCode, MediaQuality.HD.storageCode, fallback = "Version standard")
        )
    }

    @Test
    fun `F39-R7 - with no attribute detected, the selector label falls back, never to a raw name`() {
        assertEquals("Version standard", mediaVersionSelectorLabel(languageTag = null, qualityTag = null, fallback = "Version standard"))
    }

    @Test
    fun `F39-R7 - unrecognized tags also fall back, not to a raw name`() {
        assertEquals("Version standard", mediaVersionSelectorLabel(languageTag = "unknown", qualityTag = "unknown", fallback = "Version standard"))
    }
}
