package com.poc.iptvxtream.data.remote.api

import kotlin.coroutines.CoroutineContext

/**
 * Élément de CoroutineContext propagé par structured concurrency : marque la
 * chaîne d'appels courante comme travail d'arrière-plan (sync planifié/forcé,
 * enrichissement du casting) plutôt que navigation utilisateur. Aucune
 * signature de repository n'a besoin de changer — seuls les points d'entrée
 * du travail d'arrière-plan (DatabaseSyncWorker, trickle d'enrichissement)
 * posent l'élément via `withContext`/`launch(RequestPriority.background)`.
 */
object RequestPriority : CoroutineContext.Key<RequestPriority.Element> {

    enum class Level { FOREGROUND, BACKGROUND }

    class Element(val level: Level) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> get() = RequestPriority
    }

    val background: CoroutineContext = Element(Level.BACKGROUND)

    suspend fun currentLevel(): Level =
        kotlin.coroutines.coroutineContext[RequestPriority]?.level ?: Level.FOREGROUND
}
