package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.CatalogItemDto
import com.cstv.app.data.remote.dto.CatalogItemsResponseDto
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchBatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchBatchResponseDto
import com.cstv.app.data.remote.dto.CatalogMatchResponseDto
import com.cstv.app.data.remote.dto.CatalogRecommendationsResponseDto
import com.cstv.app.data.remote.dto.CatalogSeasonDto
import com.cstv.app.data.remote.dto.CatalogVideosResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** F45 §8.7 : `X-CSTV-Device-Type` n'est envoyé que sur les routes qui en tiennent compte côté backend. */
interface CstvCatalogApiService {
    @GET("v1/catalog/trending") suspend fun trending(@Query("locale") locale: String = "fr-FR"): CatalogItemsResponseDto
    @GET("v1/catalog/popular") suspend fun popular(@Query("kind") kind: String, @Query("page") page: Int, @Query("locale") locale: String = "fr-FR"): CatalogItemsResponseDto

    // deviceType par défaut "mobile" : consommé aussi par l'ancien chemin F44/T22
    // (ContentClassificationRepository, migré Tâche 10) qui n'a pas encore de DeviceTypeProvider.
    @POST("v1/catalog/matches")
    suspend fun match(@Body request: CatalogMatchRequestDto, @Header("X-CSTV-Device-Type") deviceType: String = "mobile"): CatalogMatchResponseDto

    // T29 review R1 : annonce explicite de la capacité à comprendre le statut `retry` par item —
    // sans cet en-tête, le backend garde le comportement pré-T29 (erreur HTTP globale) pour ne
    // jamais envoyer `retry` à une APK qui ne saurait pas le distinguer d'un `unresolved` (§7.3).
    @Headers("X-CSTV-Catalog-Capabilities: retry")
    @POST("v1/catalog/matches/batch")
    suspend fun matchBatch(@Body request: CatalogMatchBatchRequestDto, @Header("X-CSTV-Device-Type") deviceType: String = "mobile"): CatalogMatchBatchResponseDto

    @GET("v1/catalog/items/{externalId}")
    suspend fun item(@Path("externalId") externalId: String, @Header("X-CSTV-Device-Type") deviceType: String = "mobile"): CatalogItemDto

    @GET("v1/catalog/items/{externalId}/recommendations")
    suspend fun recommendations(@Path("externalId") externalId: String): CatalogRecommendationsResponseDto

    /** `externalId` accepte aussi l'ancien `movie:<id>`/`series:<id>` le temps de la transition (§8.7). */
    @GET("v1/catalog/items/{externalId}/videos")
    suspend fun videos(@Path("externalId") externalId: String, @Query("locale") locale: String = "fr-FR"): CatalogVideosResponseDto

    @GET("v1/catalog/items/{externalId}/seasons/{seasonNumber}")
    suspend fun season(
        @Path("externalId") externalId: String,
        @Path("seasonNumber") seasonNumber: Int,
        @Header("X-CSTV-Device-Type") deviceType: String,
    ): CatalogSeasonDto
}
