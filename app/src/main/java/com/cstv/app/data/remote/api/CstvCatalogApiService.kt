package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.CatalogItemsResponseDto
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchResponseDto
import com.cstv.app.data.remote.dto.CatalogVideosResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CstvCatalogApiService {
    @GET("v1/catalog/trending") suspend fun trending(@Query("locale") locale: String = "fr-FR"): CatalogItemsResponseDto
    @GET("v1/catalog/popular") suspend fun popular(@Query("kind") kind: String, @Query("page") page: Int, @Query("locale") locale: String = "fr-FR"): CatalogItemsResponseDto
    @POST("v1/catalog/matches") suspend fun match(@Body request: CatalogMatchRequestDto): CatalogMatchResponseDto
    @GET("v1/catalog/items/{canonicalId}/videos") suspend fun videos(@Path("canonicalId") canonicalId: String, @Query("locale") locale: String = "fr-FR"): CatalogVideosResponseDto
}
