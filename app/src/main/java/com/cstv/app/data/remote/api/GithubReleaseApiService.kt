package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.GithubReleaseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Client GitHub dédié (F35) : dépôt public `scilone/cstv`, aucune
 * authentification, aucun secret. Ne doit jamais partager son OkHttpClient
 * avec Xtream (réécriture d'URL) ni CSTV (jeton `Authorization`), voir §4.1.
 */
interface GithubReleaseApiService {

    @GET("repos/scilone/cstv/releases/latest")
    @Headers("Accept: application/vnd.github+json")
    suspend fun getLatestRelease(): GithubReleaseDto

    /**
     * URL absolue (`browser_download_url` de l'asset), streamée sans jamais
     * charger l'APK en mémoire. Les redirections HTTPS de GitHub vers son
     * hébergement d'assets restent suivies par OkHttp.
     */
    @Streaming
    @GET
    suspend fun downloadAsset(@Url assetUrl: String): Response<ResponseBody>
}
