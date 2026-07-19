package com.cstv.app.domain.model

object TitleNormalizer {

    // Regex to find parenthesized or bracketed parts (e.g. "[MULTI]", "(2024)", "|FR|")
    private val BRACKETS_REGEX = Regex("[\\[\\|\\(][^\\)\\]\\|]*[\\]\\|\\)]")
    
    // Common tags to remove if they appear as standalone words
    private val STANDALONE_TAGS = setOf(
        "1080p", "720p", "4k", "uhd", "hdr", "x265", "x264", "h265", "h264",
        "multi", "vostfr", "vost", "vf", "vo", "hd", "sd", "3d", "fr", "en", "truefrench"
    )

    // Année à 4 chiffres (1900-2099) : bornée pour ne pas prendre un nombre
    // arbitraire à 4 chiffres du titre pour une année.
    private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")

    fun normalize(title: String?): String {
        if (title.isNullOrBlank()) return ""

        // 1. Remove bracketed / pipe-enclosed tags (e.g., "[FR] Movie |1080p|" -> " Movie ")
        var result = BRACKETS_REGEX.replace(title, " ")

        // 2. Replace special characters / separators with spaces
        result = result.replace(Regex("[\\|\\-_/\\.\\+\\[\\]\\(\\):\\{\\}]"), " ")

        // 3. Lowercase and split into words
        val words = result.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // 4. Filter out standalone quality/language tags and years
        val cleanedWords = words.filter { word ->
            word !in STANDALONE_TAGS && !word.matches(YEAR_REGEX)
        }

        // 5. Si le retrait des années vide le titre, c'est que l'année EST le
        // titre (ex. "1917", "2012", "1984") : on le préserve alors, seuls les
        // tags qualité/langue étant écartés.
        val finalWords = if (cleanedWords.isEmpty()) {
            words.filter { it !in STANDALONE_TAGS }
        } else {
            cleanedWords
        }

        // 6. Join words back with a single space
        return finalWords.joinToString(" ").trim()
    }
}
