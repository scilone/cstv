package com.cstv.app.domain.model

/**
 * Entrée persistée du classement TMDB. Les identifiants locaux sont conservés
 * afin de pouvoir réappliquer les catégories masquées et les suppressions
 * éventuelles au moment de l'affichage, sans rendre le cache dépendant d'un
 * profil.
 */
data class PopularCatalogItem(
    val localIds: List<Int>
)
