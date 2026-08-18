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
    fun `display title keeps punctuation and strips year while link key uses the year free canonical form`() {
        val first = MediaTitleParser.parse("Spider-Man: No Way Home (2021) VF HD", MediaTitleKind.VOD)
        val second = MediaTitleParser.parse("Spider-Man: No Way Home MULTI 4K", MediaTitleKind.VOD)

        assertEquals("Spider-Man: No Way Home", first.cleanTitle)
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
    fun `retour utilisateur 2026-08-18 - a delimited tag unknown to the lexicon never breaks linking`() {
        // "REMUX" n'est dans aucune liste de MediaTitleParser : seul son délimiteur d'origine
        // (`|...|`) le fait tomber du texte de rapprochement, pas une entrée de lexique dédiée.
        val fr = MediaTitleParser.parse("|FR| House of the Dragon (2022)", MediaTitleKind.SERIES, providerId = 1)
        val unknownTag = MediaTitleParser.parse("|4K-REMUX| House of the Dragon (2022)", MediaTitleKind.SERIES, providerId = 2)

        assertEquals(fr.linkKey, unknownTag.linkKey)
        assertFalse(fr.linkKey.startsWith("invalid:"))
    }

    @Test
    fun `AR and CAM variants are parsed correctly into versionLabel and share link key`() {
        val fr = MediaTitleParser.parse("|FR| Evil Dead Burn (2026)", MediaTitleKind.VOD, providerId = 1)
        val arCam = MediaTitleParser.parse("|AR-CAM| Evil Dead Burn (2026)", MediaTitleKind.VOD, providerId = 2)
        val ar = MediaTitleParser.parse("|AR| Evil Dead Burn (2026)", MediaTitleKind.VOD, providerId = 3)

        assertEquals("Evil Dead Burn", fr.cleanTitle)
        assertEquals("Evil Dead Burn", arCam.cleanTitle)
        assertEquals("Evil Dead Burn", ar.cleanTitle)

        assertEquals("FR", fr.versionLabel)
        assertEquals("AR-CAM", arCam.versionLabel)
        assertEquals("AR", ar.versionLabel)

        assertEquals(fr.linkKey, arCam.linkKey)
        assertEquals(fr.linkKey, ar.linkKey)
    }

    @Test
    fun `any unknown delimiter tag like PT at the beginning is dynamically extracted into versionLabel`() {
        val pt = MediaTitleParser.parse("|PT| Evil Dead Burn (2026)", MediaTitleKind.VOD, providerId = 1)
        val remuxBracket = MediaTitleParser.parse("[REMUX] Evil Dead Burn (2026)", MediaTitleKind.VOD, providerId = 2)

        assertEquals("Evil Dead Burn", pt.cleanTitle)
        assertEquals("Evil Dead Burn", remuxBracket.cleanTitle)

        assertEquals("PT", pt.versionLabel)
        assertEquals("REMUX", remuxBracket.versionLabel)

        assertEquals(pt.linkKey, remuxBracket.linkKey)
    }

    @Test
    fun `year in cleanTitle is stripped only if it matches the official release year of the media`() {
        // Film "2012" sorti en 2009
        val movie2012WithYear = MediaTitleParser.parse("2012 (2009) VF HD", MediaTitleKind.VOD, releaseYear = 2009)
        val movie2012WithoutYear = MediaTitleParser.parse("2012 VF HD", MediaTitleKind.VOD, releaseYear = 2009)

        assertEquals("2012", movie2012WithYear.cleanTitle)
        assertEquals("2012", movie2012WithoutYear.cleanTitle)

        // Film "1917" sorti en 2019
        val movie1917WithYear = MediaTitleParser.parse("1917 (2019) VF HD", MediaTitleKind.VOD, releaseYear = 2019)
        assertEquals("1917", movie1917WithYear.cleanTitle)

        // Cas standard : Spider-Man sorti en 2021
        val spiderman = MediaTitleParser.parse("Spider-Man: No Way Home (2021) VF HD", MediaTitleKind.VOD, releaseYear = 2021)
        assertEquals("Spider-Man: No Way Home", spiderman.cleanTitle)

        // Si l'année officielle n'est pas fournie (null), repli sur le nettoyage de sécurité
        val ptNoYear = MediaTitleParser.parse("|PT| Evil Dead Burn (2026)", MediaTitleKind.VOD)
        assertEquals("Evil Dead Burn", ptNoYear.cleanTitle)
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
