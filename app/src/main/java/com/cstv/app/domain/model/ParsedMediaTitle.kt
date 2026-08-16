package com.cstv.app.domain.model

data class ParsedMediaTitle(
    val cleanTitle: String,
    val linkKey: String,
    val language: MediaLanguage? = null,
    val languageRaw: String? = null,
    val quality: MediaQuality? = null,
    val qualityRaw: String? = null
)

enum class MediaTitleKind { LIVE, VOD, SERIES }
