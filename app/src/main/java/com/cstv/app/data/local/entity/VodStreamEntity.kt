package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// L'index sur releaseYear sert l'appariement TMDB : la requête ne remonte que
// les films de l'année cherchée plus ceux dont l'année n'est pas encore connue,
// au lieu de charger et normaliser tout le catalogue (T-B15).
@Entity(tableName = "vod_streams", indices = [Index(value = ["releaseYear"])])
data class VodStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val cachedAt: Long,
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val orderIndex: Int = 0,
    val releaseYear: Int? = null,
    /** Métadonnées de get_vod_info conservées pour la fiche hors ligne. */
    val plot: String? = null,
    val duration: String? = null,
    val containerExtension: String? = null,
    val detailsCachedAt: Long? = null
)
