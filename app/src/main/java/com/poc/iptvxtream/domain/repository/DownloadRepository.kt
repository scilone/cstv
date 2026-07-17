package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.DownloadRequestData
import com.poc.iptvxtream.domain.model.DownloadedItem
import kotlinx.coroutines.flow.Flow

/**
 * Gestion des téléchargements hors-ligne (feature #15). Abstrait le
 * `DownloadManager` media3 : la couche présentation ne dépend que de ce
 * contrat et du flux Room (source de vérité unique).
 */
interface DownloadRepository {

    /** Tous les téléchargements (en cours + terminés), réactif. */
    fun observeDownloads(): Flow<List<DownloadedItem>>

    /** Démarre (ou relance) le téléchargement décrit par [data]. */
    suspend fun startDownload(data: DownloadRequestData)

    /** Supprime le téléchargement et son fichier local. */
    suspend fun removeDownload(contentId: String)

    /** Espace disque total occupé par les téléchargements (octets). */
    suspend fun getUsedBytes(): Long
}
