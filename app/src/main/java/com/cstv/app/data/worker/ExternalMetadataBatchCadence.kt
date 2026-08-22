package com.cstv.app.data.worker

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T29 cycle backfill P1 : mémoire du dernier **départ** de requête réseau de matching, partagée par
 * tous les runs du worker.
 *
 * `drainQueue` gardait cette information dans une variable locale : à chaque nouveau run elle
 * repartait à `null`, donc le premier lot du run ignorait la cadence. Invisible en phase froide (un
 * lot dure ~7 s, largement au-dessus des 2 s), mais en phase cache un lot dure 50–100 ms : quatre
 * lots partaient d'affilée, puis le run suivant repartait aussitôt — jusqu'à ~40 lots/min face à un
 * quota de 30/min/compte, d'où les ~29 % de HTTP 429 observés en production.
 *
 * Interface plutôt qu'accès direct aux préférences : les tests pilotent la mémoire sans Android, et
 * l'implémentation reste libre de changer de support (voir `BatchCadencePreferences`).
 */
interface BatchCadenceStore {
    /** `null` tant qu'aucune requête réseau n'a jamais été émise sur cet appareil. */
    suspend fun lastNetworkStartedAtMillis(): Long?

    suspend fun setLastNetworkStartedAtMillis(startedAt: Long)
}

/**
 * Portillon de cadence : borne l'intervalle entre deux **démarrages** de requête réseau, jamais une
 * pause fixe ajoutée après coup — un lot de 4 s a déjà largement dépassé la cadence et repart
 * immédiatement, un lot de 0,2 s attend le complément (~1,8 s).
 *
 * `@Singleton` + [Mutex] : deux appelants simultanés (worker en cours et reprise programmée qui se
 * chevaucheraient) sérialisent leur attente au lieu de partir ensemble. Le backend reste l'autorité
 * finale : ce portillon est préventif (plusieurs appareils peuvent partager le même compte), le 429
 * garde son traitement propre.
 */
@Singleton
class ExternalMetadataBatchCadence @Inject constructor(private val store: BatchCadenceStore) {

    private val mutex = Mutex()

    /**
     * Suspend jusqu'à ce que [MIN_BATCH_INTERVAL_MILLIS] se soit écoulé depuis le dernier départ
     * réseau connu. `delay` (coroutine), jamais `Thread.sleep` : le worker reste annulable et les
     * tests avancent en temps virtuel.
     *
     * Le plafond sur l'attente protège d'un `now()` qui reculerait (heure murale ajustée par le
     * système, ou horodatage persisté par une session antérieure) : au pire on attend un intervalle,
     * jamais une durée absurde.
     */
    suspend fun awaitSlot(now: () -> Long) {
        mutex.withLock {
            val last = store.lastNetworkStartedAtMillis() ?: return@withLock
            val remaining = MIN_BATCH_INTERVAL_MILLIS - (now() - last)
            if (remaining > 0) delay(remaining.coerceAtMost(MIN_BATCH_INTERVAL_MILLIS))
        }
    }

    /**
     * Enregistre l'instant de départ d'une requête ayant réellement consommé du quota. Un lot
     * entièrement servi par les liens déjà présents en Room n'a rien consommé : l'appelant ne doit
     * alors pas appeler cette méthode, sinon il imposerait une cadence à un travail purement local.
     */
    suspend fun recordNetworkStart(startedAt: Long) {
        mutex.withLock { store.setLastNetworkStartedAtMillis(startedAt) }
    }

    companion object {
        /**
         * T29 débit §3 : intervalle minimum entre deux **démarrages** de requête réseau. Le quota
         * backend est de 30 requêtes/min/compte : une toutes les 2 s reste dessous sans jamais le
         * déclencher.
         */
        const val MIN_BATCH_INTERVAL_MILLIS = 2_000L
    }
}

/** Mémoire de cadence non persistée — défaut des chemins de test, et repli si les préférences sont illisibles. */
class InMemoryBatchCadenceStore(initialStartedAt: Long? = null) : BatchCadenceStore {
    @Volatile private var lastStartedAt: Long? = initialStartedAt
    override suspend fun lastNetworkStartedAtMillis(): Long? = lastStartedAt
    override suspend fun setLastNetworkStartedAtMillis(startedAt: Long) { lastStartedAt = startedAt }
}
