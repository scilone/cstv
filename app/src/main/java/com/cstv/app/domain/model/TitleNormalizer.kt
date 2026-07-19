package com.cstv.app.domain.model

/**
 * Normalise un titre — TMDB « propre » (`Dragon Ball Z`) ou IPTV « sale »
 * (`|FR| Dragon Ball Z 1080p MULTI [x265]`) — vers une forme comparable :
 * retrait des tags langue/qualité/résolution/codec, des crochets/pipes/
 * séparateurs et de l'année entre parenthèses, puis passage en minuscules
 * avec espaces compactés.
 *
 * Objet pur et testable — aucune dépendance Android/Room. Même esprit défensif
 * que [GenreParser] / [ReleaseYearParser] : ne lève jamais, renvoie `""` pour
 * une entrée vide/nulle/entièrement composée de tags.
 */
object TitleNormalizer {

    // Tags retirés uniquement lorsqu'ils apparaissent comme token isolé (jamais
    // en sous-chaîne : un vrai titre « 4K » resterait extrêmement rare et le
    // gain de matching l'emporte). Comparaison en minuscules.
    private val TAG_TOKENS = setOf(
        // Langue / piste audio
        "fr", "vf", "vff", "vfq", "vfi", "vo", "vost", "vostfr", "vosteng",
        "multi", "truefrench", "french", "en", "eng", "sub", "subfr", "dub", "dubbed",
        // Qualité / résolution / source
        "sd", "hd", "hq", "fhd", "uhd", "hdr", "hdr10", "hdlight", "4k", "8k",
        "2160p", "1080p", "1080i", "720p", "576p", "480p", "hdrip", "bluray",
        "brrip", "bdrip", "webrip", "web", "webdl", "hdtv", "dvdrip", "dvd",
        "cam", "ts", "tc",
        // Codec / audio
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx",
        "aac", "ac3", "eac3", "dts", "10bit", "8bit"
    )

    // Année entre parenthèses : "(2019)". Retirée avant de casser la ponctuation.
    private val YEAR_IN_PARENS = Regex("\\((?:19|20)\\d{2}\\)")

    // Tout ce qui n'est ni lettre/chiffre (Unicode) devient un séparateur :
    // crochets, pipes, points, underscores, tirets, deux-points, etc.
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    private val MULTI_SPACE = Regex("\\s+")

    /** Renvoie la forme normalisée de [raw], ou `""` si rien d'exploitable. */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = YEAR_IN_PARENS.replace(raw, " ")
        s = NON_ALNUM.replace(s, " ")
        return s.trim().lowercase()
            .split(MULTI_SPACE)
            .filter { it.isNotBlank() && it !in TAG_TOKENS }
            .joinToString(" ")
    }
}
