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
    private val technicalTokens = setOf("HDR", "X265", "X264", "H265", "H264", "3D", "EN", "STFR", "DV", "AR", "CAM")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")
    private val year = Regex("(?:19|20)\\d{2}")
    private val yearWithDelimiters = Regex("[(\\[{]?\\s*(?:19|20)\\d{2}\\s*[)\\]}]?")
    // Bug 2026-08-19 : après retrait des tokens reconnus (ex. `VO`/`STFR` dans `(VO|STFR)`), le
    // séparateur `|` qui les liait restait seul entre les crochets (`(|)`) — la regex d'origine ne
    // couvrait que des crochets strictement vides (espace uniquement), pas ceux ne contenant plus
    // que des séparateurs de tags résiduels.
    private val emptyBrackets = Regex("[\\[({]\\s*[|_+/.:\\-]*\\s*[\\])}]")
    private val trailingSeparator = Regex("\\s+[|_+/.:\\-]+(?=\\s*$)")
    // Symétrique de `trailingSeparator` côté début de chaîne : un tag retiré en tête
    // (ex. `|FR|`, `|VO|STFR|`) ne laisse jamais les séparateurs `|` orphelins visibles.
    private val leadingSeparator = Regex("^[\\s|_+/.:\\-]+")
    // Retour utilisateur du 2026-08-18 : le linkKey (T21) ne doit plus dépendre uniquement du
    // lexique fini ci-dessus pour exclure une étiquette du texte de rapprochement — toute
    // étiquette placée entre parenthèses/crochets/accolades, ou dans un bloc de tags `|...|`
    // (convention du catalogue), est retirée qu'elle soit connue (`HD`, `VOSTFR`…) ou non (`DV`,
    // `REMUX`, …), sans avoir à l'ajouter au lexique à chaque nouveau cas. Le lexique reste
    // nécessaire pour `cleanTitle`/`language`/`quality`/`versionLabel`, qui doivent reconnaître le
    // sens de chaque fragment (y compris hors délimiteur, ex. `Film X 1080p MULTI 4K`), pas
    // seulement l'exclure.
    // Un seul `|` d'ouverture, puis N répétitions de « contenu-sans-pipe suivi d'un `|` » : les
    // tags consécutifs du catalogue partagent leurs pipes (`|VO|STFR|` = 3 `|` pour 2 tags), donc
    // chaque répétition ne peut pas exiger son propre `|` d'ouverture.
    // Bug 2026-08-19 : `[^|]*` (autorisant les espaces) laissait le bloc déborder sur un `|`
    // réapparu plus loin dans le titre lui-même (ex. `|4K-DV| ... (VO|STFR)`), avalant tout le
    // texte entre les deux pipes comme si c'était un tag — chaque segment de tag du catalogue
    // est un seul token sans espace (`4K-DV`, `VOSTFR`, `AR-CAM`…), donc `[^|\\s]*` borne
    // correctement la répétition sans pouvoir traverser un espace.
    private val leadingPipeTagBlock = Regex("^\\s*\\|(?:[^|\\s]*\\|)+")
    private val pipePair = Regex("\\|[^|]*\\|")
    private val bracketedGroup = Regex("[(\\[{][^)\\]}]*[)\\]}]")
    private val leadingBracketTagBlock = Regex("^\\s*\\[[^\\]]+\\]")
    // Retour utilisateur du 2026-08-18 (T21/F39, lenteurs) : ces deux-là étaient
    // recompilés à chaque appel de `canonicalize`, elle-même appelée plusieurs
    // fois par titre analysé (`parse`, `matchingTitleOf`, `linkingCanonicalOf`) —
    // des milliers de compilations `Pattern.compile` évitables par synchronisation
    // de catalogue, chacune coûteuse sur ce chipset (watchdog : thread principal
    // bloqué jusqu'à plusieurs secondes dans `PatternNative.compileImpl`).
    private val combiningMarks = Regex("\\p{M}+")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
    // ThreadLocal.withInitial(Supplier) requires API 26 (minSdk 21) : sous-classe manuelle.
    private val digest = object : ThreadLocal<MessageDigest>() {
        override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
    }

    fun parse(
        rawTitle: String?,
        mediaKind: MediaTitleKind,
        // `releaseYear` deliberately does not participate in `linkKey`: consumers apply
        // yearsAreCompatible pairwise so an undated entry can remain compatible.
        releaseYear: Int? = null,
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

        // Extraction dynamique des tags du catalogue entre délimiteurs en tête de titre (VOD uniquement)
        val pipeMatch = if (isVod) leadingPipeTagBlock.find(source) else null
        val bracketMatch = if (isVod && pipeMatch == null) leadingBracketTagBlock.find(source) else null
        val delimiterMatch = pipeMatch ?: bracketMatch

        if (delimiterMatch != null) {
            removableRanges += delimiterMatch.range
            val rawBlock = delimiterMatch.value
            val cleanBlock = if (delimiterMatch == pipeMatch) rawBlock else rawBlock.trim('[', ']')
            val segments = cleanBlock.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            segments.forEach { segment ->
                tokenRegex.findAll(segment).forEach { match ->
                    val token = match.value.uppercase(Locale.ROOT)

                    val languageMatch = languageMarkers.firstOrNull { it.token == token }
                    if (languageMatch != null) {
                        if (selectedLanguage == null) selectedLanguage = languageMatch.value to match.value
                    }

                    val qualityMatch = qualityMarkers.firstOrNull { it.token == token }
                    if (qualityMatch != null) {
                        val current = selectedQuality
                        if (current == null || qualityMatch.value.rank > current.first.rank) {
                            selectedQuality = qualityMatch.value to match.value
                        }
                    }
                }
                recognizedFragments += segment
            }
        }

        tokenRegex.findAll(source).forEach { match ->
            if (removableRanges.any { it.contains(match.range.first) || it.contains(match.range.last) }) {
                return@forEach
            }
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
        if (isVod) {
            var tempCleaned = cleaned
            yearWithDelimiters.findAll(cleaned).forEach { match ->
                val matchedStr = match.value
                val extractedYear = year.find(matchedStr)?.value?.toIntOrNull()
                if (extractedYear != null) {
                    val shouldStrip = if (releaseYear != null && releaseYear > 0) {
                        extractedYear == releaseYear
                    } else {
                        val testWithout = cleanupDisplayTitle(tempCleaned.replace(matchedStr, " "), stripLeading = true)
                        canonicalize(testWithout).isNotBlank()
                    }
                    if (shouldStrip) {
                        tempCleaned = tempCleaned.replace(matchedStr, " ")
                    }
                }
            }
            cleaned = cleanupDisplayTitle(tempCleaned, stripLeading = true)
        }
        if (cleaned.length < 2) cleaned = source

        val canonical = linkingCanonicalOf(source, cleaned, mediaKind)
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
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace(nonAlphanumeric, " ")
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
        return cleanupDisplayTitle(stripLexiconTokens(source)).ifBlank { source }
    }

    /** Retire les fragments reconnus par le lexique langue/qualité/technique, sans y toucher sinon. */
    private fun stripLexiconTokens(value: String): String {
        val ranges = mutableListOf<IntRange>()
        tokenRegex.findAll(value).forEach { match ->
            val token = match.value.uppercase(Locale.ROOT)
            if (languageMarkers.any { it.token == token } || qualityMarkers.any { it.token == token } || token in technicalTokens) {
                ranges += match.range
            }
        }
        var result = value
        ranges.sortedByDescending { it.first }.forEach { result = result.removeRange(it.first, it.last + 1) }
        return result
    }

    /**
     * Texte de rapprochement du linkKey (T21), calculé depuis `source` (pas `cleanTitle`) : les
     * délimiteurs d'un tag que le lexique n'a pas reconnu (ex. `DV`/`REMUX` dans `|4K-DV|`) sont
     * déjà mangés par `cleanTitle` (`cleanupDisplayTitle(stripLeading = true)` dans `parse()`),
     * ce qui laisserait le fragment inconnu traîner sans ses délimiteurs. LIVE garde `cleanTitle`
     * tel quel (`|FR|` y est l'identité de la chaîne, jamais un tag à exclure). VOD/SERIES retire
     * tout bloc de tags `|...|` en tête, tout groupe entre parenthèses/crochets/accolades où qu'il
     * soit, puis les fragments lexicaux restés hors délimiteur (ex. `Film X 1080p MULTI 4K`, sans
     * crochets) — sans qu'il faille ajouter la moindre étiquette au lexique. Repli sur `cleanTitle`
     * si le dépouillement laisse une chaîne vide (titre entièrement entre délimiteurs).
     */
    private fun linkingCanonicalOf(source: String, cleanTitle: String, mediaKind: MediaTitleKind): String {
        if (mediaKind == MediaTitleKind.LIVE) return canonicalize(cleanTitle)
        var stripped = source
        var previous: String
        do {
            previous = stripped
            stripped = stripped
                .replace(leadingPipeTagBlock, " ")
                .replace(pipePair, " ")
                .replace(bracketedGroup, " ")
        } while (stripped != previous)
        stripped = stripLexiconTokens(stripped)
        stripped = cleanupDisplayTitle(stripped, stripLeading = true)
        val structural = canonicalize(removeYearsForMatching(stripped))
        return structural.ifBlank { canonicalize(removeYearsForMatching(cleanTitle)) }
    }

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
