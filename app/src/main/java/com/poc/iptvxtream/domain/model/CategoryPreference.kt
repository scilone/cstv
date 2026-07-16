package com.poc.iptvxtream.domain.model

/** Type de média d'une catégorie, tel que persisté dans `category_preferences`. */
enum class CategoryType(val value: String) {
    LIVE("live"),
    VOD("vod"),
    SERIES("series")
}

/**
 * Personnalisation d'une catégorie pour le profil actif (Phase 58).
 * `sortOrder` null = ordre API par défaut.
 */
data class CategoryPreference(
    val categoryId: String,
    val hidden: Boolean,
    val sortOrder: Int?
)

/**
 * Applique les préférences du profil à une liste de catégories déjà triée
 * dans l'ordre API par défaut (`orderIndex`) : retire les catégories
 * masquées puis applique l'ordre personnalisé. Les catégories sans
 * `sortOrder` gardent leur position par défaut (index dans la liste
 * visible).
 */
fun <T> applyCategoryPreferences(
    categories: List<T>,
    preferences: Map<String, CategoryPreference>,
    categoryId: (T) -> String
): List<T> {
    val visible = categories.filter { preferences[categoryId(it)]?.hidden != true }
    return visible.withIndex()
        .sortedWith(
            compareBy(
                { preferences[categoryId(it.value)]?.sortOrder ?: it.index },
                { it.index }
            )
        )
        .map { it.value }
}
