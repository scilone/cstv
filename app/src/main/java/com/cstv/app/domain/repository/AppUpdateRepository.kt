package com.cstv.app.domain.repository

import com.cstv.app.domain.model.AppUpdateCheckResult
import com.cstv.app.domain.model.AppUpdateRelease
import java.io.File

/**
 * Lecture de la dernière Release GitHub et téléchargement de son APK (F35).
 * Ne connaît ni les règles de silence (RG2/RG4/RG5), ni l'UI : voir
 * [com.cstv.app.domain.usecase.CheckForAppUpdateUseCase].
 */
interface AppUpdateRepository {

    suspend fun fetchLatestRelease(): AppUpdateCheckResult

    /**
     * Réutilise un APK déjà en cache si taille/package/version correspondent,
     * sinon télécharge en streaming vers `cacheDir/app_updates`. [onProgress]
     * est appelé au maximum une fois par point de pourcentage (0–100).
     *
     * Une annulation de coroutine annule l'appel réseau et supprime le
     * fichier partiel — l'appelant n'a rien d'autre à nettoyer.
     */
    suspend fun downloadApk(release: AppUpdateRelease, onProgress: suspend (percent: Int) -> Unit): File
}
