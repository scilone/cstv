package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.IptvCredentialsDto
import com.cstv.app.data.remote.dto.IptvCredentialsRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

interface CstvIptvCredentialsApiService {
    @GET("v1/account/iptv-credentials") suspend fun get(): Response<IptvCredentialsDto>
    @PUT("v1/account/iptv-credentials") suspend fun put(@Body credentials: IptvCredentialsRequestDto, @Header("If-Match") ifMatch: String? = null): Response<Unit>
    @DELETE("v1/account/iptv-credentials") suspend fun delete(@Header("If-Match") ifMatch: String? = null): Response<Unit>
}
