package com.cstv.app.data.repository

import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.matchOrNull
import com.cstv.app.domain.repository.ExternalMetadataRepository
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MediaClassificationKind(val wireValue: String) {
    MOVIE("movie"), SERIES("series")
}

/**
 * Classification d'âge (F45 §8.13) d'un film ou d'une série, sous l'identité CSTV
 * ([ExternalMetadataRepository], `externalId`). Simple fournisseur de donnée : ne décide jamais
 * d'un accès, c'est le rôle exclusif de `ParentalAccessPolicy` (§9.2). Entier exact nullable
 * (`13`, `15`, `17`, ...) — plus de palier français `ageRatingFr` comme source de vérité.
 *
 * `ParentalAccessPolicy` ne l'appelle jamais pour un profil non bridé (§8.2) :
 * cette classe n'a donc aucun coût sur le parcours adulte tant qu'elle n'est
 * pas sollicitée.
 */
@Singleton
class ContentClassificationRepository @Inject constructor(
    private val externalMetadataRepository: ExternalMetadataRepository,
    private val timeProvider: TimeProvider,
) {
    private data class CacheKey(val kind: MediaClassificationKind, val providerId: Int)
    private data class CacheEntry(val ageRating: Int?, val resolvedAt: Long)

    // Cache mémoire, best-effort : `ExternalMetadataRepository.match()` a déjà son propre
    // court-circuit local (lien déjà résolu, §7.5) ; ce cache évite seulement de rappeler la
    // suspension/mutex pour la même œuvre plusieurs fois pendant une session (ex. retours
    // répétés sur la même fiche).
    private val cache = LinkedHashMap<CacheKey, CacheEntry>()
    private val inFlight = LinkedHashMap<CacheKey, CompletableDeferred<Int?>>()
    private val mutex = Mutex()

    /**
     * `null` = classification inconnue ou service indisponible (règle défensive F44, §8.2) :
     * jamais convertie en "tout public". `providerId` est requis (§8.13, résolution par identité,
     * plus par titre seul) : `null` renvoie `null` immédiatement, sans appel réseau.
     */
    suspend fun classificationFor(kind: MediaClassificationKind, title: String, year: Int?, providerId: Int? = null): Int? {
        if (providerId == null) return null
        val key = CacheKey(kind, providerId)
        val (request, isOwner) = mutex.withLock {
            val cached = cache[key]
            if (cached != null && timeProvider.nowMillis() - cached.resolvedAt < SESSION_CACHE_TTL_MS) {
                return@withLock CompletableDeferred(cached.ageRating) to false
            }
            inFlight[key]?.let { return@withLock it to false }
            CompletableDeferred<Int?>().also { inFlight[key] = it } to true
        }

        // Une navigation de fiche et un tap sur Lire peuvent demander la même
        // classification presque simultanément. Les deux doivent partager la
        // requête en cours et attendre sa réponse, au lieu que le second appel
        // interprète prématurément l'absence de résultat comme « inconnue ».
        if (!isOwner) return request.await()

        val outcome = try {
            // F45-R5 : une fiche/lecture ouverte est l'équivalent interactif de DETAIL_OPEN — seul
            // chemin autorisé à rafraîchir une métadonnée stale (§7.5/§7.10).
            externalMetadataRepository.match(kind.wireValue, providerId, title, year, linkKey = null, ExternalMatchHints(), allowRefresh = true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutex.withLock { inFlight.remove(key)?.complete(null) }
            // Une erreur transitoire doit refuser l'accès dans l'instant, mais
            // ne doit pas transformer une micro-coupure en cache négatif.
            return null
        }

        if (outcome is ExternalMetadataMatchOutcome.Retry) {
            // T29 §7.3 : une impossibilité *technique* temporaire (429 provider, budget TMDB local
            // épuisé) se comporte comme une erreur réseau — jamais mise en cache comme "inconnue",
            // pour que le prochain appel puisse retenter sans attendre l'expiration du cache.
            mutex.withLock { inFlight.remove(key)?.complete(null) }
            return null
        }
        val ageRating = outcome.matchOrNull?.ageRating

        mutex.withLock {
            cache[key] = CacheEntry(ageRating, timeProvider.nowMillis())
            if (cache.size > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keys.first())
            }
            inFlight.remove(key)?.complete(ageRating)
        }
        return ageRating
    }

    companion object {
        private const val SESSION_CACHE_TTL_MS = 30L * 60 * 1000
        private const val MAX_CACHE_ENTRIES = 64
    }
}
