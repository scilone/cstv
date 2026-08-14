package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.PlaybackLockDto
import com.cstv.app.data.remote.dto.PlaybackLockRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

interface CstvPlaybackLockApiService {
    @POST("v1/account/playback-lock") suspend fun acquire(@Body request: PlaybackLockRequestDto): Response<PlaybackLockDto>
    @POST("v1/account/playback-lock/heartbeat") suspend fun heartbeat(@Header("If-Match") lockToken: String): Response<PlaybackLockDto>
    @DELETE("v1/account/playback-lock") suspend fun release(@Header("If-Match") lockToken: String): Response<Unit>
}
