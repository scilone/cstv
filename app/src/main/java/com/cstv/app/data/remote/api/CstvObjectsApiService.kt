package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.ObjectListDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface CstvObjectsApiService {
    @GET("v1/profiles/{profileId}/objects") suspend fun listObjects(@Path("profileId") profileId: String): Response<ObjectListDto>
    @Streaming @GET("v1/profiles/{profileId}/objects/{namespace}") suspend fun getObject(@Path("profileId") profileId: String, @Path("namespace") namespace: String): Response<ResponseBody>
    @PUT("v1/profiles/{profileId}/objects/{namespace}") suspend fun putObject(@Path("profileId") profileId: String, @Path("namespace") namespace: String, @Header("X-Schema-Version") schemaVersion: Int, @Header("If-Match") ifMatch: String?, @Body body: RequestBody): Response<Unit>
    /** Contract symmetry only: nominal client flow writes an explicit empty snapshot instead. */
    @DELETE("v1/profiles/{profileId}/objects/{namespace}") suspend fun deleteObject(@Path("profileId") profileId: String, @Path("namespace") namespace: String, @Header("If-Match") ifMatch: String?): Response<Unit>
}
