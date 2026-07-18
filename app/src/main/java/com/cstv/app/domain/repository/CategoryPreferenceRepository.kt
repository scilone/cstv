package com.cstv.app.domain.repository

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryPreferenceRepository {

    /**
     * Émet à chaque modification de préférence (masquage/ordre) : permet aux
     * écrans de grille et à la Home, dont les ViewModels survivent en
     * backstack, de recharger leurs catégories au retour des Paramètres.
     */
    val changes: Flow<Unit>

    /** Préférences du profil actif pour ce type de média, indexées par categoryId. */
    suspend fun getPreferences(type: CategoryType): Map<String, CategoryPreference>

    /** Masque ou réaffiche une catégorie pour le profil actif (ordre conservé). */
    suspend fun setHidden(type: CategoryType, categoryId: String, hidden: Boolean)

    /**
     * Persiste l'ordre personnalisé complet (toutes catégories du type, y
     * compris masquées) pour le profil actif : `sortOrder` = index dans la liste.
     */
    suspend fun saveOrder(type: CategoryType, orderedCategoryIds: List<String>)
}
