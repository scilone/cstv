package com.cstv.app.data.local.entity

import androidx.room.Entity

/**
 * Personnalisation d'une catégorie (masquage / ordre) par profil (Phase 58).
 *
 * `type` : "live", "vod" ou "series" (voir [com.cstv.app.domain.model.CategoryType]).
 * `sortOrder` : position personnalisée dans la liste ; null = pas de
 * personnalisation d'ordre, la catégorie garde l'ordre API (`orderIndex`
 * des tables de catégories). Une catégorie sans ligne ici est visible et
 * à sa position par défaut.
 */
@Entity(tableName = "category_preferences", primaryKeys = ["categoryId", "type", "profileId"])
data class CategoryPreferenceEntity(
    val categoryId: String,
    val type: String,
    val profileId: Int,
    val hidden: Boolean,
    val sortOrder: Int?
)
