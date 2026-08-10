package com.cstv.app.domain.model

/**
 * Résultat de la résolution du profil au démarrage (F31). Remplace le
 * `Boolean` nu de l'ancien `ensureInitializedAndNeedsSelection()` pour éviter
 * un second aller-retour vers le repository afin de récupérer la liste des
 * profils normalisée.
 */
data class StartupProfileResolution(
    val profiles: List<Profile>,
    val needsSelection: Boolean
)
