package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CatalogItemsResponseDto(val items: List<CatalogItemDto>? = null)
data class CatalogMatchRequestDto(val kind: String, val title: String, val year: Int? = null, val locale: String = "fr-FR")
data class CatalogMatchResponseDto(val status: String? = null, val item: CatalogItemDto? = null, val cache: CatalogCacheDto? = null)
data class CatalogVideosResponseDto(val items: List<CatalogVideoDto>? = null, val cache: CatalogCacheDto? = null)
data class CatalogCacheDto(val stale: Boolean? = null)
data class CatalogItemDto(
    val id: String? = null, val kind: String? = null, val title: String? = null,
    @SerializedName("originalTitle") val originalTitle: String? = null,
    @SerializedName("releaseYear") val releaseYear: Int? = null,
    val overview: String? = null, val rating: Double? = null,
    @SerializedName("posterUrl") val posterUrl: String? = null,
    @SerializedName("backdropUrl") val backdropUrl: String? = null,
    @SerializedName("ageRatingFr") val ageRatingFr: Int? = null,
)
data class CatalogVideoDto(val site: String? = null, val key: String? = null, val type: String? = null, val official: Boolean? = null)
