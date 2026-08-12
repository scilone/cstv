package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.GithubReleaseApiService
import com.cstv.app.data.remote.dto.GithubReleaseDto
import com.cstv.app.data.update.UpdateApkStore
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.AppUpdateCheckResult
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.model.SemanticVersion
import com.cstv.app.domain.repository.AppUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val TAG = "APPUPDATE"

/**
 * Lit la Release GitHub, valide l'asset et télécharge en streaming (F35,
 * §4.4). Aucune règle de silence ici (RG2/RG4/RG5) : c'est le rôle de
 * [com.cstv.app.domain.usecase.CheckForAppUpdateUseCase].
 */
class AppUpdateRepositoryImpl @Inject constructor(
    private val apiService: GithubReleaseApiService,
    private val apkStore: UpdateApkStore
) : AppUpdateRepository {

    // F35-R1 : la copie réseau -> disque doit tourner hors du thread appelant
    // (le ViewModel l'invoque depuis viewModelScope, donc Main). Un
    // constructeur secondaire permet aux tests de forcer un dispatcher de test
    // déterministe, comme VodRepositoryImpl/SeriesRepositoryImpl.
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        apiService: GithubReleaseApiService,
        apkStore: UpdateApkStore,
        dispatcher: CoroutineDispatcher
    ) : this(apiService, apkStore) {
        this.ioDispatcher = dispatcher
    }

    override suspend fun fetchLatestRelease(): AppUpdateCheckResult {
        return try {
            mapToResult(apiService.getLatestRelease())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IptvLog.e(TAG, "Vérification de mise à jour impossible", e)
            AppUpdateCheckResult.Failure(e.message ?: "network_error")
        }
    }

    private fun mapToResult(dto: GithubReleaseDto): AppUpdateCheckResult {
        // RG9 : comportement natif de releases/latest, contrôlé défensivement.
        if (dto.draft == true || dto.prerelease == true) return AppUpdateCheckResult.NoUsableRelease
        val version = SemanticVersion.parse(dto.tagName) ?: return AppUpdateCheckResult.NoUsableRelease
        // Premier asset dont le nom se termine par .apk, sans tenir compte de la casse.
        val asset = dto.assets.orEmpty().firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
            ?: return AppUpdateCheckResult.NoUsableRelease
        val url = asset.browserDownloadUrl ?: return AppUpdateCheckResult.NoUsableRelease
        if (!isTrustedAssetUrl(url)) return AppUpdateCheckResult.NoUsableRelease
        val size = asset.size ?: return AppUpdateCheckResult.NoUsableRelease
        return AppUpdateCheckResult.ReleaseFound(
            AppUpdateRelease(
                version = version,
                assetDownloadUrl = url,
                assetName = asset.name.orEmpty(),
                assetSizeBytes = size
            )
        )
    }

    /** HTTPS, hôte `github.com`, aucun userinfo, chemin sous le dépôt attendu (§4.4). */
    private fun isTrustedAssetUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme == "https" &&
            parsed.host == ALLOWED_HOST &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty() &&
            parsed.encodedPath.startsWith(ALLOWED_PATH_PREFIX)
    }

    override suspend fun downloadApk(release: AppUpdateRelease, onProgress: suspend (percent: Int) -> Unit): File =
        withContext(ioDispatcher) {
            val versionCode = release.version.versionCode
            apkStore.findReusableApk(versionCode, release.assetSizeBytes)?.let { return@withContext it }

            apkStore.ensureDirectory()
            val partFile = apkStore.partFileFor(versionCode)
            val response = apiService.downloadAsset(release.assetDownloadUrl)
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()} lors du téléchargement de l'APK")
            val body = response.body() ?: throw IOException("Corps de réponse vide lors du téléchargement de l'APK")

            writeStreamed(body, partFile, release.assetSizeBytes, onProgress)
            apkStore.promotePart(versionCode)
            // F35-R2 : un fichier fraîchement promu n'a encore jamais été
            // contrôlé -- une réponse tronquée ou un mauvais package/version ne
            // doit jamais atteindre PackageInstaller.
            apkStore.validatePromoted(versionCode, release.assetSizeBytes)
                ?: throw IOException("APK téléchargé invalide (taille, package ou version inattendus)")
        }

    private suspend fun writeStreamed(
        body: ResponseBody,
        target: File,
        totalSizeBytes: Long,
        onProgress: suspend (percent: Int) -> Unit
    ) {
        var lastPercent = -1
        try {
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(STREAM_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (totalSizeBytes > 0) {
                            val percent = ((readTotal * 100) / totalSizeBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // L'annulation de la coroutine annule l'appel OkHttp ; le fichier
            // partiel n'a pas vocation à survivre (§4.9).
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw e
        }
    }

    companion object {
        private const val ALLOWED_HOST = "github.com"
        private const val ALLOWED_PATH_PREFIX = "/scilone/cstv/releases/download/"
        private const val STREAM_BUFFER_SIZE = 8 * 1024
    }
}
