package com.cstv.app.domain.model

object TitleNormalizer {

    // Regex to find parenthesized or bracketed parts (e.g. "[MULTI]", "(2024)", "|FR|")
    private val BRACKETS_REGEX = Regex("[\\[\\|\\(][^\\)\\]\\|]*[\\]\\|\\)]")
    
    // Common tags to remove if they appear as standalone words
    private val STANDALONE_TAGS = setOf(
        "1080p", "720p", "4k", "uhd", "hdr", "x265", "x264", "h265", "h264",
        "multi", "vostfr", "vost", "vf", "vo", "hd", "sd", "3d", "fr", "en", "truefrench"
    )

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
            word !in STANDALONE_TAGS && !word.matches(Regex("\\d{4}"))
        }

        // 5. Join words back with a single space
        return cleanedWords.joinToString(" ").trim()
    }
}
