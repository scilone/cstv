package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveEpgNowNext
import com.poc.iptvxtream.domain.model.LiveStream

interface LiveTvRepository {
    suspend fun getLiveCategories(forceRefresh: Boolean): List<LiveCategory>
    suspend fun getLiveStreams(categoryId: String, forceRefresh: Boolean): List<LiveStream>
    suspend fun saveRecentlyWatched(stream: LiveStream)
    suspend fun getRecentlyWatched(): List<LiveStream>
    suspend fun getLiveEpg(streamId: Int, forceRefresh: Boolean = false): LiveEpgProgram?

    /**
     * Programme en cours + suivant (player Live TV, informatif). Non caché en
     * DB (le cache EPG ne stocke qu'un seul programme) : petit fetch réseau
     * get_short_epg à l'ouverture du player. Renvoie deux champs null si
     * indisponible.
     */
    suspend fun getLiveEpgNowNext(streamId: Int, forceRefresh: Boolean = false): LiveEpgNowNext

    /** Nombre de chaînes par categoryId, basé sur le cache local (sélecteur de catégorie). */
    suspend fun getCategoryCounts(): Map<String, Int>
}
