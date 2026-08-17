package com.cstv.app.domain.model

data class ParsedMediaTitle(
    val cleanTitle: String,
    val linkKey: String,
    val language: MediaLanguage? = null,
    val languageRaw: String? = null,
    val quality: MediaQuality? = null,
    val qualityRaw: String? = null,
    // F39 (évolution) : tous les fragments reconnus (langue, qualité, technique),
    // dans l'ordre du titre source, joints par « · ». Seule source d'affichage
    // des badges/sélecteurs — `language`/`quality` ne servent plus qu'au tri.
    val versionLabel: String? = null
)

enum class MediaTitleKind { LIVE, VOD, SERIES }
