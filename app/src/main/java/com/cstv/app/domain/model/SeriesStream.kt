package com.cstv.app.domain.model

data class SeriesStream(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val actors: String? = null,
    val director: String? = null,
    /** Blob local normalisé, réutilisé par la recherche avancée. */
    val searchText: String = "",
    val cleanTitle: String = "",
    val linkKey: String = "",
    val languageTag: String? = null,
    val languageRaw: String? = null,
    val qualityTag: String? = null,
    val qualityRaw: String? = null,
    /** F39 (évolution) : libellé combiné affiché en badge/sélecteur (ex. "VO · STFR · 4K"). */
    val versionLabel: String? = null
)
