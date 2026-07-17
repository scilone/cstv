package com.poc.iptvxtream.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.dao.SeriesDao
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.dao.ProfileDao
import com.poc.iptvxtream.data.local.dao.TrackPreferenceDao
import com.poc.iptvxtream.data.local.dao.CategoryPreferenceDao
import com.poc.iptvxtream.data.local.dao.DownloadDao
import com.poc.iptvxtream.data.local.entity.CategoryPreferenceEntity
import com.poc.iptvxtream.data.local.entity.DownloadedMediaEntity
import com.poc.iptvxtream.data.local.entity.ProfileEntity
import com.poc.iptvxtream.data.local.entity.TrackPreferenceEntity
import com.poc.iptvxtream.data.local.entity.LiveCategoryEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.SeriesCategoryEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodCategoryEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.RecentlyWatchedLiveEntity
import com.poc.iptvxtream.data.local.entity.EpgCacheEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamFtsEntity
import com.poc.iptvxtream.data.local.entity.VodStreamFtsEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamFtsEntity

@Database(
    entities = [
        LiveCategoryEntity::class, 
        LiveStreamEntity::class,
        VodCategoryEntity::class,
        VodStreamEntity::class,
        SeriesCategoryEntity::class,
        SeriesStreamEntity::class,
        PlaybackPositionEntity::class,
        FavoriteEntity::class,
        RecentlyWatchedLiveEntity::class,
        EpgCacheEntity::class,
        ProfileEntity::class,
        TrackPreferenceEntity::class,
        LiveStreamFtsEntity::class,
        VodStreamFtsEntity::class,
        SeriesStreamFtsEntity::class,
        CategoryPreferenceEntity::class,
        DownloadedMediaEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun liveTvDao(): LiveTvDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun profileDao(): ProfileDao
    abstract fun trackPreferenceDao(): TrackPreferenceDao
    abstract fun categoryPreferenceDao(): CategoryPreferenceDao
    abstract fun downloadDao(): DownloadDao
}
