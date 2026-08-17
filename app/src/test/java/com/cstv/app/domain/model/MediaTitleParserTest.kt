package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTitleParserTest {
    @Test
    fun `real panel live variants share a key while preserving country prefix`() {
        val hd = MediaTitleParser.parse("|FR| TF1 HD", MediaTitleKind.LIVE, providerId = 10)
        val sd = MediaTitleParser.parse("|FR| TF1 SD", MediaTitleKind.LIVE, providerId = 11)

        assertEquals("|FR| TF1", hd.cleanTitle)
        assertEquals(hd.linkKey, sd.linkKey)
        assertEquals(MediaQuality.HD, hd.quality)
        assertEquals("HD", hd.qualityRaw)
        assertEquals(MediaQuality.SD, sd.quality)
    }

    @Test
    fun `vod extraction retains raw tags and keeps highest quality`() {
        val parsed = MediaTitleParser.parse("Film X [VF] 1080p MULTI 4K", MediaTitleKind.VOD, providerId = 42)

        assertEquals("Film X", parsed.cleanTitle)
        assertEquals(MediaLanguage.VF, parsed.language)
        assertEquals("VF", parsed.languageRaw)
        assertEquals(MediaQuality.UHD_4K, parsed.quality)
        assertEquals("4K", parsed.qualityRaw)
    }

    @Test
    fun `dirty and empty labels never throw or create an empty grouping key`() {
        val empty = MediaTitleParser.parse(null, MediaTitleKind.VOD, providerId = 99)
        val punctuation = MediaTitleParser.parse("[HD]", MediaTitleKind.VOD, providerId = 100)

        assertEquals("", empty.cleanTitle)
        assertEquals("invalid:vod:99", empty.linkKey)
        assertFalse(punctuation.linkKey.isBlank())
    }

    @Test
    fun `years compatibility preserves the intentional non transitive rule`() {
        assertTrue(MediaTitleParser.yearsAreCompatible(2020, 2020))
        assertFalse(MediaTitleParser.yearsAreCompatible(2020, 2021))
        assertTrue(MediaTitleParser.yearsAreCompatible(null, 2020))
        assertTrue(MediaTitleParser.yearsAreCompatible(null, null))

        // The undated entry can join either direct comparison, but dated works
        // must never be shown as one another's version.
        assertTrue(MediaTitleParser.yearsAreCompatible(null, 2016))
        assertTrue(MediaTitleParser.yearsAreCompatible(null, 2026))
        assertFalse(MediaTitleParser.yearsAreCompatible(2016, 2026))
    }

    @Test
    fun `display title keeps punctuation and year while link key uses the year free canonical form`() {
        val first = MediaTitleParser.parse("Spider-Man: No Way Home (2021) VF HD", MediaTitleKind.VOD)
        val second = MediaTitleParser.parse("Spider-Man: No Way Home MULTI 4K", MediaTitleKind.VOD)

        assertEquals("Spider-Man: No Way Home (2021)", first.cleanTitle)
        assertEquals(first.linkKey, second.linkKey)
    }

    @Test
    fun `live language markers remain part of the channel identity`() {
        val vf = MediaTitleParser.parse("Ciné+ VF HD", MediaTitleKind.LIVE)
        val vo = MediaTitleParser.parse("Ciné+ VO HD", MediaTitleKind.LIVE)

        assertEquals("Ciné+ VF", vf.cleanTitle)
        assertEquals("Ciné+ VO", vo.cleanTitle)
        assertNull(vf.language)
        assertFalse(vf.linkKey == vo.linkKey)
    }

    @Test
    fun `FR prefix on VOD is treated as VF, not silently dropped`() {
        val parsed = MediaTitleParser.parse("|FR| Supergirl", MediaTitleKind.SERIES, providerId = 1)

        assertEquals("Supergirl", parsed.cleanTitle)
        assertEquals(MediaLanguage.VF, parsed.language)
        assertEquals("FR", parsed.versionLabel)
    }

    @Test
    fun `pipe split VO STFR STAYS as two literal fragments in versionLabel, never reformulated`() {
        val parsed = MediaTitleParser.parse("|VO|STFR|4K| Supergirl", MediaTitleKind.SERIES, providerId = 2)

        assertEquals("Supergirl", parsed.cleanTitle)
        assertEquals(MediaLanguage.VO, parsed.language)
        assertEquals("VO · STFR · 4K", parsed.versionLabel)
    }

    @Test
    fun `standalone STFR is kept literal in versionLabel, not remapped to VOSTFR`() {
        val parsed = MediaTitleParser.parse("|STFR| Supergirl", MediaTitleKind.SERIES, providerId = 3)

        assertEquals("STFR", parsed.versionLabel)
        assertNull(parsed.language)
    }

    @Test
    fun `FR and VO STFR variants of the same title now share a link key`() {
        val fr = MediaTitleParser.parse("|FR| Supergirl", MediaTitleKind.SERIES, providerId = 1)
        val voStfr = MediaTitleParser.parse("|VO|STFR| Supergirl", MediaTitleKind.SERIES, providerId = 2)

        assertEquals(fr.linkKey, voStfr.linkKey)
        assertFalse(fr.linkKey.startsWith("invalid:"))
        // Chaque version garde son propre libellé, jamais un type généralisé commun.
        assertEquals("FR", fr.versionLabel)
        assertEquals("VO · STFR", voStfr.versionLabel)
    }

    @Test
    fun `storage codes are unique and stable`() {
        assertEquals(MediaLanguage.entries.size, MediaLanguage.entries.map { it.storageCode }.toSet().size)
        assertEquals(MediaQuality.entries.size, MediaQuality.entries.map { it.storageCode }.toSet().size)
        assertEquals("vostfr", MediaLanguage.VOSTFR.storageCode)
        assertEquals("uhd_4k", MediaQuality.UHD_4K.storageCode)
        assertNull(MediaTitleParser.parse("Aucun marqueur", MediaTitleKind.VOD).quality)
    }
}
