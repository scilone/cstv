package com.poc.iptvxtream.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VodCategoryDto(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("parent_id") val parentId: Int?
)
