package com.poc.iptvxtream.data.remote.api

import com.poc.iptvxtream.data.remote.dto.LiveCategoryDto
import com.poc.iptvxtream.data.remote.dto.LiveStreamDto
import com.poc.iptvxtream.data.remote.dto.LoginResponseDto
import com.poc.iptvxtream.data.remote.dto.VodCategoryDto
import com.poc.iptvxtream.data.remote.dto.VodStreamDto
import com.poc.iptvxtream.data.remote.dto.VodInfoResponseDto
import com.poc.iptvxtream.data.remote.dto.SeriesCategoryDto
import com.poc.iptvxtream.data.remote.dto.SeriesStreamDto
import com.poc.iptvxtream.data.remote.dto.SeriesInfoResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {
    
    @GET("player_api.php")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String
    ): LoginResponseDto

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<LiveCategoryDto>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null,
        @Query("action") action: String = "get_live_streams"
    ): List<LiveStreamDto>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<VodCategoryDto>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null,
        @Query("action") action: String = "get_vod_streams"
    ): List<VodStreamDto>

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int,
        @Query("action") action: String = "get_vod_info"
    ): VodInfoResponseDto

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<SeriesCategoryDto>

    @GET("player_api.php")
    suspend fun getSeriesStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null,
        @Query("action") action: String = "get_series"
    ): List<SeriesStreamDto>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int,
        @Query("action") action: String = "get_series_info"
    ): SeriesInfoResponseDto
}
