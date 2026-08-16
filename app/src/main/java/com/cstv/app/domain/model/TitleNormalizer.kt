package com.cstv.app.domain.model

object TitleNormalizer {
    /** Compatibility facade for raw external values. Persisted catalogue rows use cleanTitle directly. */
    fun normalize(title: String?): String = MediaTitleParser.matchingTitleOf(title)
}
