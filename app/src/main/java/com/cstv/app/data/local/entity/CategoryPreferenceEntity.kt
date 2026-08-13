package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Personnalisation d'une catégorie (masquage / ordre) par profil (Phase 58). `sortOrder` : position
 * personnalisée dans la liste ; null = pas de personnalisation d'ordre, la catégorie garde l'ordre
 * API (`orderIndex` des tables de catégories). Une catégorie sans ligne ici est visible et à sa
 * position par défaut. T20 : cible référencée par `catUid` (`category_refs`), qui survit à la
 * disparition de la catégorie — c'est ce qui permet à la préférence de se réappliquer à son retour.
 */
@Entity(
    tableName = "category_preferences",
    primaryKeys = ["profileId", "catUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryRefEntity::class, parentColumns = ["catUid"], childColumns = ["catUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("catUid")],
)
data class CategoryPreferenceEntity(
    val profileId: Int,
    val catUid: Long,
    val hidden: Boolean,
    val sortOrder: Int?,
)
