package com.cstv.app.domain.model

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Pure catalogue-title parser. Its lexicon is deliberately finite: an unknown
 * token is always retained rather than guessed away.
 */
object MediaTitleParser {
    private data class LanguageMarker(val token: String, val value: MediaLanguage)
    private data class QualityMarker(val token: String, val value: MediaQuality)

    private val languageMarkers = listOf(
        LanguageMarker("TRUEFRENCH", MediaLanguage.TRUEFRENCH),
        LanguageMarker("SUBFRENCH", MediaLanguage.SUBFRENCH),
        LanguageMarker("VOSTFR", MediaLanguage.VOSTFR),
        LanguageMarker("MULTI", MediaLanguage.MULTI),
        LanguageMarker("VOST", MediaLanguage.VOST),
        LanguageMarker("VFQ", MediaLanguage.VFQ),
        LanguageMarker("VFF", MediaLanguage.VFF),
        LanguageMarker("VF", MediaLanguage.VF),
        // Convention observée sur ce catalogue : préfixe `|FR|` isolé = version
        // française (doublage), distincte de `VF` mais mappée sur la même valeur —
        // aucun code de stockage dédié n'existe pour cette nuance.
        LanguageMarker("FR", MediaLanguage.VF),
        LanguageMarker("VO", MediaLanguage.VO)
    )
    private val qualityMarkers = listOf(
        QualityMarker("2160P", MediaQuality.UHD_4K),
        QualityMarker("1080P", MediaQuality.FHD),
        QualityMarker("720P", MediaQuality.HD),
        QualityMarker("UHD", MediaQuality.UHD_4K),
        QualityMarker("FHD", MediaQuality.FHD),
        QualityMarker("4K", MediaQuality.UHD_4K),
        QualityMarker("HD", MediaQuality.HD),
        QualityMarker("SD", MediaQuality.SD)
    )
    // Fragments reconnus mais qui ne pilotent ni le classement langue ni le
    // classement qualité (pas de rang, pas de "meilleur gagne") : simplement
    // capturés tels quels dans `versionLabel` et retirés du titre affiché.
    // `STFR` y figure : forme éclatée de VOSTFR par le séparateur `|` du
    // catalogue (`|VO|STFR|`), jamais un marqueur autonome au sens strict —
    // mais on ne le fusionne plus dans un autre libellé (décision produit :
    // chaque fragment reste visible tel quel, jamais reformulé).
    // Retour utilisateur du 2026-08-18 : `DV` (Dolby Vision, souvent accolé à la
    // qualité — ex. `4K-DV`) restait dans le titre nettoyé et cassait le
    // rattachement au linkKey des autres versions de la même série/du même film.
    private val technicalTokens = setOf("HDR", "X265", "X264", "H265", "H264", "3D", "EN", "STFR", "DV")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")
    private val year = Regex("(?:19|20)\\d{2}")
    private val emptyBrackets = Regex("[\\[({]\\s*[\\])}]")
    private val trailingSeparator = Regex("\\s+[|_+/.:\\-]+(?=\\s*$)")
    // Symétrique de `trailingSeparator` côté début de chaîne : un tag retiré en tête
    // (ex. `|FR|`, `|VO|STFR|`) ne laisse jamais les séparateurs `|` orphelins visibles.
    private val leadingSeparator = Regex("^[\\s|_+/.:\\-]+")
    // ThreadLocal.withInitial(Supplier) requires API 26 (minSdk 21) : sous-classe manuelle.
    private val digest = object : ThreadLocal<MessageDigest>() {
        override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
    }

    fun parse(
        rawTitle: String?,
        mediaKind: MediaTitleKind,
        // `releaseYear` deliberately does not participate in `linkKey`: consumers apply
        // yearsAreCompatible pairwise so an undated entry can remain compatible.
        @Suppress("UNUSED_PARAMETER") releaseYear: Int? = null,
        providerId: Int = 0
    ): ParsedMediaTitle {
        val source = rawTitle.orEmpty().trim()
        if (source.isEmpty()) return ParsedMediaTitle("", singletonKey(mediaKind, providerId))

        val isVod = mediaKind != MediaTitleKind.LIVE
        var selectedLanguage: Pair<MediaLanguage, String>? = null
        var selectedQuality: Pair<MediaQuality, String>? = null
        val removableRanges = mutableListOf<IntRange>()
        // Libellé d'affichage : chaque fragment reconnu (langue, qualité, ou
        // technique) est gardé tel quel, dans l'ordre du titre source — jamais
        // recalculé/renommé, contrairement à `language`/`quality` qui ne
        // retiennent qu'une seule valeur chacun pour le tri des versions.
        val recognizedFragments = mutableListOf<String>()

        tokenRegex.findAll(source).forEach { match ->
            val token = match.value.uppercase(Locale.ROOT)

            val languageMatch = if (isVod) languageMarkers.firstOrNull { it.token == token } else null
            if (languageMatch != null) {
                if (selectedLanguage == null) selectedLanguage = languageMatch.value to match.value
                removableRanges += match.range
                recognizedFragments += token
                return@forEach
            }

            val qualityMatch = qualityMarkers.firstOrNull { it.token == token }
            if (qualityMatch != null) {
                val current = selectedQuality
                if (current == null || qualityMatch.value.rank > current.first.rank) {
                    selectedQuality = qualityMatch.value to match.value
                }
                removableRanges += match.range
                if (isVod) recognizedFragments += token
                return@forEach
            }

            // Preserve country prefixes on LIVE, where |FR| is part of the channel identity.
            if (isVod && token in technicalTokens) {
                removableRanges += match.range
                recognizedFragments += token
            }
        }

        var cleaned = source
        removableRanges.sortedByDescending { it.first }.forEach { range ->
            cleaned = cleaned.removeRange(range.first, range.last + 1)
        }
        cleaned = cleanupDisplayTitle(cleaned, stripLeading = isVod)
        if (cleaned.length < 2) cleaned = source

        val canonical = matchingTitleOf(cleaned, mediaKind)
        return ParsedMediaTitle(
            cleanTitle = cleaned,
            linkKey = if (canonical.isBlank()) singletonKey(mediaKind, providerId) else hashKey(canonical),
            language = selectedLanguage?.first,
            languageRaw = selectedLanguage?.second,
            quality = selectedQuality?.first,
            qualityRaw = selectedQuality?.second,
            versionLabel = recognizedFragments.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        )
    }

    fun canonicalize(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(whitespace, " ")

    /** TMDB's historic title form: canonical and year-free, without constructing a link key. */
    fun matchingTitleOf(rawTitle: String?): String {
        val parsed = parseForMatching(rawTitle)
        return canonicalize(removeYearsForMatching(parsed))
    }

    fun yearsAreCompatible(left: Int?, right: Int?): Boolean =
        left == null || left <= 0 || right == null || right <= 0 || left == right

    private fun parseForMatching(rawTitle: String?): String {
        val source = rawTitle.orEmpty().trim()
        if (source.isEmpty()) return ""
        val ranges = mutableListOf<IntRange>()
        tokenRegex.findAll(source).forEach { match ->
            val token = match.value.uppercase(Locale.ROOT)
            if (languageMarkers.any { it.token == token } || qualityMarkers.any { it.token == token } || token in technicalTokens) {
                ranges += match.range
            }
        }
        var cleaned = source
        ranges.sortedByDescending { it.first }.forEach { cleaned = cleaned.removeRange(it.first, it.last + 1) }
        return cleanupDisplayTitle(cleaned).ifBlank { source }
    }

    private fun matchingTitleOf(cleanTitle: String, mediaKind: MediaTitleKind): String =
        if (mediaKind == MediaTitleKind.LIVE) canonicalize(cleanTitle)
        else canonicalize(removeYearsForMatching(cleanTitle))

    private fun removeYearsForMatching(value: String): String {
        val withoutYears = cleanupDisplayTitle(year.replace(value, " "))
        return withoutYears.takeIf { canonicalize(it).isNotBlank() } ?: value
    }

    // `stripLeading` reste opt-in : sur LIVE, `|FR|` n'est jamais retiré du texte
    // (identité de chaîne), donc son `|` de tête n'est jamais orphelin et doit
    // rester visible — seul un appelant VOD/SERIES sait qu'un tag de tête a pu
    // être excisé et que les séparateurs restants sont, eux, orphelins.
    private fun cleanupDisplayTitle(value: String, stripLeading: Boolean = false): String = value
        .replace(emptyBrackets, " ")
        .replace(trailingSeparator, "")
        .let { if (stripLeading) it.replace(leadingSeparator, "") else it }
        .replace(whitespace, " ")
        .trim()

    private fun hashKey(canonical: String): String {
        val bytes = requireNotNull(digest.get()).digest(canonical.toByteArray(Charsets.UTF_8))
        val hex = CharArray(32)
        val digits = "0123456789abcdef"
        for (index in 0 until 16) {
            val value = bytes[index].toInt() and 0xff
            hex[index * 2] = digits[value ushr 4]
            hex[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(hex)
    }

    private fun singletonKey(kind: MediaTitleKind, providerId: Int): String = "invalid:${kind.name.lowercase(Locale.ROOT)}:$providerId"
}
