package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.TmdbTrendingResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {

    @GET("trending/all/week")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbTrendingResponseDto
}
