package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_refs",
    indices = [Index(value = ["accountKey", "kind", "providerCategoryId"], unique = true)]
)
data class CategoryRefEntity(
    @PrimaryKey(autoGenerate = true) val catUid: Long = 0,
    val accountKey: String,
    val kind: String,
    val providerCategoryId: String
)
