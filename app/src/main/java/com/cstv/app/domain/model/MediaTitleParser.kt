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
    private val ignoredVodTokens = setOf("HDR", "X265", "X264", "H265", "H264", "3D", "FR", "EN")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")
    private val year = Regex("(?:19|20)\\d{2}")
    private val emptyBrackets = Regex("[\\[({]\\s*[\\])}]")
    private val trailingSeparator = Regex("\\s+[|_+/.:\\-]+(?=\\s*$)")
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

        var selectedLanguage: Pair<MediaLanguage, String>? = null
        var selectedQuality: Pair<MediaQuality, String>? = null
        val removableRanges = mutableListOf<IntRange>()

        tokenRegex.findAll(source).forEach { match ->
            val token = match.value.uppercase(Locale.ROOT)
            languageMarkers.firstOrNull { mediaKind != MediaTitleKind.LIVE && it.token == token }?.let { marker ->
                if (selectedLanguage == null) selectedLanguage = marker.value to match.value
                removableRanges += match.range
                return@forEach
            }
            qualityMarkers.firstOrNull { it.token == token }?.let { marker ->
                val current = selectedQuality
                if (current == null || marker.value.rank > current.first.rank) {
                    selectedQuality = marker.value to match.value
                }
                removableRanges += match.range
                return@forEach
            }
            // These historic matcher-only tags are not attributes. Preserve country
            // prefixes on LIVE, where |FR| is part of the channel identity.
            if (mediaKind != MediaTitleKind.LIVE && token in ignoredVodTokens) removableRanges += match.range
        }

        var cleaned = source
        removableRanges.sortedByDescending { it.first }.forEach { range ->
            cleaned = cleaned.removeRange(range.first, range.last + 1)
        }
        cleaned = cleanupDisplayTitle(cleaned)
        if (cleaned.length < 2) cleaned = source

        val canonical = matchingTitleOf(cleaned, mediaKind)
        return ParsedMediaTitle(
            cleanTitle = cleaned,
            linkKey = if (canonical.isBlank()) singletonKey(mediaKind, providerId) else hashKey(canonical),
            language = selectedLanguage?.first,
            languageRaw = selectedLanguage?.second,
            quality = selectedQuality?.first,
            qualityRaw = selectedQuality?.second
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
            if (languageMarkers.any { it.token == token } || qualityMarkers.any { it.token == token } || token in ignoredVodTokens) {
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

    private fun cleanupDisplayTitle(value: String): String = value
        .replace(emptyBrackets, " ")
        .replace(trailingSeparator, "")
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
