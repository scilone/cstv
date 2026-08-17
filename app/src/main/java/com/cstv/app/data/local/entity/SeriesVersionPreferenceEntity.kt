package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * F39 §8.3 : version de série choisie explicitement par l'utilisateur dans le
 * sélecteur, mémorisée pour toute la série et réappliquée automatiquement aux
 * épisodes suivants (décision produit étape 1). Locale et par profil, comme
 * [TrackPreferenceEntity] — mais indexée par `linkKey` (T21), pas `mediaUid` :
 * la préférence porte sur l'œuvre entière, pas sur un média précis.
 *
 * Non synchronisée tant qu'aucun namespace cloud correspondant n'est
 * spécifié. Si la série préférée ou l'épisode équivalent n'existe plus, le
 * resolver retombe sur la série ouverte et supprime paresseusement la
 * préférence obsolète (§8.3).
 */
@Entity(
    tableName = "series_version_preferences",
    primaryKeys = ["profileId", "linkKey"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index("linkKey")]
)
data class SeriesVersionPreferenceEntity(
    val profileId: Int,
    val linkKey: String,
    val preferredSeriesId: Int,
    val updatedAt: Long
)
