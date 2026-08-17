package com.cstv.app.domain.model

data class FavoriteItem(
    val id: Int,
    val type: String, // "live", "movie", "series"
    val name: String,
    val cover: String?,
    val categoryId: String,
    /** F39 : tags T21, toujours nuls pour un `type == "live"`. */
    val languageTag: String? = null,
    val qualityTag: String? = null,
    /** F39 (évolution) : libellé combiné affiché en badge, toujours nul pour un `type == "live"`. */
    val versionLabel: String? = null
)
