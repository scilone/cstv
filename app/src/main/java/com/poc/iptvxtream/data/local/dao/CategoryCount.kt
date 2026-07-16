package com.poc.iptvxtream.data.local.dao

/**
 * Résultat des requêtes de comptage de médias par catégorie (GROUP BY), utilisé
 * par les compteurs du sélecteur de catégorie (bottom sheet, iso-maquette).
 */
data class CategoryCount(
    val categoryId: String,
    val count: Int
)
