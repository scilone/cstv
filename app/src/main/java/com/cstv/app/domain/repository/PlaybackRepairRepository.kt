package com.cstv.app.domain.repository

import com.cstv.app.domain.model.PlaybackRepairPlan

/**
 * Mémorisation appareil+média de la configuration de réparation gagnante (T23 §8.5) — sans
 * logique de lecture (§9.2). Portée par (appareil, média) : pas de `profileId`, une seule
 * configuration active par média, partagée entre tous les profils locaux (décision étape 2).
 */
interface PlaybackRepairRepository {
    suspend fun getRepairPlan(kind: String, providerId: Int): PlaybackRepairPlan?
    suspend fun saveRepairPlan(kind: String, providerId: Int, plan: PlaybackRepairPlan)
    suspend fun clearRepairPlan(kind: String, providerId: Int)
}
