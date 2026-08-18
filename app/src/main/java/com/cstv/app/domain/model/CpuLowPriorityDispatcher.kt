package com.cstv.app.domain.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Fil unique, priorité minimale, partagé par tout calcul CPU-bound qui relit
 * l'intégralité du catalogue local (extrait de la justification posée avec
 * `GetRecommendationsUseCase`, voir ce fichier) : `Dispatchers.Default`
 * dispose d'autant de fils que l'appareil a de cœurs et les prend tous,
 * affamant la navigation le temps du calcul — treize secondes pour ouvrir un
 * catalogue au lieu de soixante-quinze millisecondes, observé sur un
 * téléviseur d'entrée de gamme. `MIN_PRIORITY` laisse les cœurs restants à
 * l'UI et cède devant elle.
 *
 * Fil partagé (et non un par use case) : le matching TMDB des tendances et le
 * calcul des recommandations tournent parfois dans la même fenêtre de temps
 * (ouverture de l'Accueil juste après une synchronisation) — les mettre en
 * concurrence sur deux fils MIN_PRIORITY distincts recrée la même famine à
 * deux ; les sérialiser sur un seul fil dédié garde l'UI prioritaire dans
 * tous les cas.
 */
object CpuLowPriorityDispatcher {
    val instance: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cpu-low-priority").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()
    }
}
