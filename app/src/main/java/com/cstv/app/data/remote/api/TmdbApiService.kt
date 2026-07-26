package com.cstv.app.data.remote.api

import com.cstv.app.data.remote.dto.TmdbTrendingResponseDto
import com.cstv.app.data.remote.dto.TmdbVideosResponseDto
import retrofit2.http.Path
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbVideosResponseDto

    @GET("tv/{series_id}/videos")
    suspend fun getSeriesVideos(
        @Path("series_id") seriesId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbVideosResponseDto

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "fr-FR"
    ): com.cstv.app.data.remote.dto.TmdbTrendingResponseDto

    @GET("search/tv")
    suspend fun searchSeries(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "fr-FR"
    ): TmdbTrendingResponseDto

    @GET("trending/all/week")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbTrendingResponseDto

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbTrendingResponseDto

    @GET("tv/popular")
    suspend fun getPopularSeries(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbTrendingResponseDto
}
