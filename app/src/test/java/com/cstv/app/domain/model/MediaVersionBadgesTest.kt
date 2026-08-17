package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** F39 (évolution PO) : `versionLabel` combine tous les fragments reconnus, jamais fabriqué. */
class MediaVersionBadgesTest {

    @Test
    fun `no version label produces no badge`() {
        assertEquals(emptyList<String>(), mediaVersionBadges(null))
    }

    @Test
    fun `single fragment produces one badge`() {
        assertEquals(listOf("VF"), mediaVersionBadges("VF"))
    }

    @Test
    fun `several fragments produce badges in the same order, never reformulated`() {
        assertEquals(listOf("VO", "STFR", "4K"), mediaVersionBadges("VO · STFR · 4K"))
    }

    @Test
    fun `blank version label produces no badge`() {
        assertEquals(emptyList<String>(), mediaVersionBadges(" "))
    }

    // F39-R7 (étape 7) : libellé centralisé des sélecteurs — jamais le nom Xtream brut, même
    // quand aucun attribut n'est détecté (décision produit étape 7 : repli fixe et localisé).

    @Test
    fun `F39-R7 - a populated version label is returned as-is in the selector`() {
        assertEquals("VO · STFR · 4K", mediaVersionSelectorLabel("VO · STFR · 4K", fallback = "Version standard"))
    }

    @Test
    fun `F39-R7 - with no version label, the selector falls back, never to a raw name`() {
        assertEquals("Version standard", mediaVersionSelectorLabel(null, fallback = "Version standard"))
    }

    @Test
    fun `F39-R7 - a blank version label also falls back`() {
        assertEquals("Version standard", mediaVersionSelectorLabel("  ", fallback = "Version standard"))
    }
}
