package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.*
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface CstvApiService {
    @POST("v1/auth/otp/request") suspend fun requestOtp(@Body request: CstvOtpRequestDto): Response<Unit>
    @POST("v1/auth/otp/verify") suspend fun verifyOtp(@Body request: CstvOtpVerifyDto): Response<CstvAccessTokenDto>
    @GET("v1/me") suspend fun getMe(): Response<CstvAccountDto>
    @POST("v1/profiles") suspend fun createProfile(@Body profile: CstvProfileCreateDto): Response<CstvProfileDto>
    /**
     * `body` est un `JsonObject` construit à la main (pas un DTO à champs
     * `?`) : F44 doit pouvoir envoyer `"maxAgeRating": null` explicitement
     * pour débrider un profil, ce que Gson omet silencieusement pour un champ
     * `null` d'un DTO réflexif (`serializeNulls()` n'est pas activé
     * globalement). Un champ absent du `JsonObject` reste, lui, inchangé côté
     * backend (§8.1).
     */
    @PATCH("v1/profiles/{id}") suspend fun updateProfile(@Path("id") id: String, @Body body: JsonObject): Response<CstvProfileDto>
    @DELETE("v1/profiles/{id}") suspend fun deleteProfile(@Path("id") id: String): Response<Unit>
}
