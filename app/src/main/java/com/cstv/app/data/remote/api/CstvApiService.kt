package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface CstvApiService {
    @POST("v1/auth/otp/request") suspend fun requestOtp(@Body request: CstvOtpRequestDto): Response<Unit>
    @POST("v1/auth/otp/verify") suspend fun verifyOtp(@Body request: CstvOtpVerifyDto): Response<CstvAccessTokenDto>
    @GET("v1/me") suspend fun getMe(): Response<CstvAccountDto>
    @POST("v1/profiles") suspend fun createProfile(@Body profile: CstvProfileCreateDto): Response<CstvProfileDto>
    @PATCH("v1/profiles/{id}") suspend fun updateProfile(@Path("id") id: String, @Body profile: CstvProfileUpdateDto): Response<CstvProfileDto>
    @DELETE("v1/profiles/{id}") suspend fun deleteProfile(@Path("id") id: String): Response<Unit>
}
