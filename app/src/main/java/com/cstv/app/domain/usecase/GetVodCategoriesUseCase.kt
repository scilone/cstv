package com.cstv.app.domain.usecase

import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** Voir [GetLiveCategoriesUseCase] : lecture locale observable, jamais réseau. */
class GetVodCategoriesUseCase @Inject constructor(
    private val repository: VodRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    /**
     * Les deux étages sont chronométrés séparément.
     *
     * La mesure d'ensemble, côté `VodViewModel`, a montré une première émission
     * à 12,6 s sur un téléviseur d'entrée de gamme alors que les écrans Séries
     * et Live ouverts juste après répondaient en 70 ms — et alors que la base
     * était déjà ouverte, les recommandations l'ayant lue quelques secondes
     * plus tôt. Cette mesure globale ne dit pas *qui* attend : la requête Room
     * sur `vod_categories` (23 lignes, triviale), ou la lecture des préférences
     * de catégories qui la suit dans le `combine`. Les deux traces ci-dessous
     * tranchent, au lieu de laisser choisir entre plusieurs explications
     * plausibles.
     */
    operator fun invoke(): Flow<List<VodCategory>> {
        val subscribedAt = System.nanoTime()
        var firstRoomEmission = true
        return combine(
            repository.observeVodCategories().onEach {
                if (firstRoomEmission) {
                    firstRoomEmission = false
                    IptvLog.d(
                        "PERF",
                        "VOD catégories : requête Room en " +
                            "${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                    )
                }
            },
            categoryPreferenceRepository.changes.onStart { emit(Unit) }
        ) { categories, _ ->
            val preferencesAt = System.nanoTime()
            val preferences = categoryPreferenceRepository.getPreferences(CategoryType.VOD)
            IptvLog.d(
                "PERF",
                "VOD catégories : préférences en " +
                    "${(System.nanoTime() - preferencesAt) / 1_000_000}ms"
            )
            applyCategoryPreferences(categories, preferences) { it.categoryId }
        }
    }

    suspend fun once(): List<VodCategory> {
        val categories = repository.getCachedVodCategories()
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.VOD)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
