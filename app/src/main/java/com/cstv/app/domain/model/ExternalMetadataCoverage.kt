package com.cstv.app.domain.model

/**
 * F46 : état agrégé de l'enrichissement F45 du catalogue IPTV local, uniquement films et séries
 * (les saisons/épisodes n'y participent pas). Provider-neutral — aucun concept TMDB.
 *
 * `processed = linked + unresolved` (§7.3) : un média est traité dès qu'une tentative de matching
 * a produit un résultat métier persisté, lié ou `UNRESOLVED`, indépendamment d'un `retryAfter` de
 * revalidation future.
 */
data class ExternalMetadataCoverage(
    val total: Int,
    val linked: Int,
    val unresolved: Int,
    val movies: ExternalMetadataCoverageByKind,
    val series: ExternalMetadataCoverageByKind,
) {
    val processed: Int get() = linked + unresolved
    val pending: Int get() = (total - processed).coerceAtLeast(0)
}

data class ExternalMetadataCoverageByKind(
    val total: Int,
    val linked: Int,
    val unresolved: Int,
) {
    val processed: Int get() = linked + unresolved
    val pending: Int get() = (total - processed).coerceAtLeast(0)
}

/**
 * §8.9 : `total <= 0` renvoie `null` — aucun pourcentage trompeur (0 % ou 100 %) sur un catalogue
 * vide ou un type de média absent. Résultat toujours borné à `[0.0, 100.0]` même sur des compteurs
 * incohérents (défense en profondeur, §9.5).
 */
fun coveragePercent(part: Int, total: Int): Double? {
    if (total <= 0) return null
    return (part.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)
}

/**
 * F46 §8.9 / F46-R3 : formatage de présentation, extrait de `SettingsScreen.kt` pour rester une
 * fonction pure JVM-testable (aucune dépendance Compose). Une décimale maximum, `100 %` rendu
 * sans décimale, virgule décimale française — cohérent avec le reste des libellés de l'écran.
 */
fun formatCoveragePercent(percent: Double): String {
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        "${rounded.toInt()} %"
    } else {
        String.format(java.util.Locale.FRANCE, "%.1f %%", rounded)
    }
}
