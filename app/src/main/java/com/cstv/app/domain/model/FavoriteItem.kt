package com.cstv.app.domain.model

data class FavoriteItem(
    val id: Int,
    val type: String, // "live", "movie", "series"
    val name: String,
    val cover: String?,
    val categoryId: String
)
