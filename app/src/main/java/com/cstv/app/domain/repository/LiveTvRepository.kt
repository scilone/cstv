package com.cstv.app.domain.repository

import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveEpgNowNext
import com.cstv.app.domain.model.LiveStream
import kotlinx.coroutines.flow.Flow

interface LiveTvRepository {

    // --- Lecture : strictement locale, jamais de réseau, jamais d'exception réseau ---
    // Les Flow viennent directement des DAO : toute écriture de synchronisation
    // ré-émet vers les écrans ouverts, sans réinitialiser navigation ni profil.
    fun observeLiveCategories(): Flow<List<LiveCategory>>
    fun observeLiveStreams(categoryId: String): Flow<List<LiveStream>>
    fun getLiveStreamsPaged(categoryId: String): Flow<androidx.paging.PagingData<LiveStream>>

    /** Lecture ponctuelle du cache local (aucun appel réseau). */
    suspend fun getCachedLiveCategories(): List<LiveCategory>
    suspend fun getCachedLiveStreams(categoryId: String): List<LiveStream>

    // --- Écriture : réseau → Room, jamais consommée directement par l'UI ---
    suspend fun syncLiveCategories(): List<LiveCategory>
    suspend fun syncLiveStreams(categoryId: String = "all"): List<LiveStream>

    suspend fun saveRecentlyWatched(stream: LiveStream)
    suspend fun getRecentlyWatched(): List<LiveStream>

    /**
     * Programme en cours. Cache d'abord (TTL 15 min en ligne, illimité hors
     * ligne), réseau ensuite, persistance du résultat dans la fenêtre EPG.
     */
    suspend fun getLiveEpg(streamId: Int, forceRefresh: Boolean = false): LiveEpgProgram?

    /**
     * Programme en cours + suivant (player Live TV, informatif). Sert la
     * fenêtre EPG locale quand elle est fraîche ou que l'appareil est hors
     * ligne ; renvoie deux champs null si rien n'est disponible.
     */
    suspend fun getLiveEpgNowNext(streamId: Int, forceRefresh: Boolean = false): LiveEpgNowNext

    /** Horodatage de la dernière mise à jour EPG connue pour cette chaîne, ou null. */
    suspend fun getEpgCachedAt(streamId: Int): Long?

    /** Purge des programmes terminés, appelée en fin de synchronisation. */
    suspend fun purgeExpiredEpg()

    /** Nombre de chaînes par categoryId, basé sur le cache local (sélecteur de catégorie). */
    suspend fun getCategoryCounts(): Map<String, Int>
}
