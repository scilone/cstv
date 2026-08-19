package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.domain.model.AgeRating
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MediaClassificationKind(val wireValue: String) {
    MOVIE("movie"), SERIES("series")
}

/**
 * Classification d'âge française (F44) d'un film ou d'une série, sous
 * l'identité canonique T22 (`POST /v1/catalog/matches`, `ageRatingFr`). Simple
 * fournisseur de donnée : ne décide jamais d'un accès, c'est le rôle exclusif
 * de `ParentalAccessPolicy` (§9.2).
 *
 * `ParentalAccessPolicy` ne l'appelle jamais pour un profil non bridé (§8.2) :
 * cette classe n'a donc aucun coût sur le parcours adulte tant qu'elle n'est
 * pas sollicitée.
 */
@Singleton
class ContentClassificationRepository @Inject constructor(
    private val catalogApiService: CstvCatalogApiService,
    private val timeProvider: TimeProvider,
) {
    private data class CacheKey(val kind: MediaClassificationKind, val title: String, val year: Int?)
    private data class CacheEntry(val ageRating: AgeRating?, val resolvedAt: Long)

    // Cache mémoire, best-effort : le backend applique déjà le TTL de 30 jours
    // prévu par T22 ; ce cache local évite seulement de rappeler le réseau pour
    // la même œuvre plusieurs fois pendant une session (ex. retours répétés sur
    // la même fiche).
    private val cache = LinkedHashMap<CacheKey, CacheEntry>()
    private val mutex = Mutex()

    /**
     * `null` = classification inconnue ou service indisponible (règle
     * défensive F44, §8.2) : jamais convertie en [AgeRating.ALL].
     */
    suspend fun classificationFor(kind: MediaClassificationKind, title: String, year: Int?): AgeRating? {
        val key = CacheKey(kind, title, year)
        mutex.withLock {
            val cached = cache[key]
            if (cached != null && timeProvider.nowMillis() - cached.resolvedAt < SESSION_CACHE_TTL_MS) {
                return cached.ageRating
            }
        }

        val ageRating = try {
            val response = catalogApiService.match(CatalogMatchRequestDto(kind = kind.wireValue, title = title, year = year))
            AgeRating.fromValueOrNull(response.item?.ageRatingFr)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Une erreur transitoire doit refuser l'accès dans l'instant, mais
            // ne doit pas transformer une micro-coupure en cache négatif.
            return null
        }

        mutex.withLock {
            cache[key] = CacheEntry(ageRating, timeProvider.nowMillis())
            if (cache.size > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keys.first())
            }
        }
        return ageRating
    }

    companion object {
        private const val SESSION_CACHE_TTL_MS = 30L * 60 * 1000
        private const val MAX_CACHE_ENTRIES = 64
    }
}
