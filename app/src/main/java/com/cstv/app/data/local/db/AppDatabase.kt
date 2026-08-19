package com.cstv.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.TrackPreferenceDao
import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.dao.SeriesWatchStateDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.entity.ProfileSyncStateEntity
import com.cstv.app.data.local.entity.CategoryPreferenceEntity
import com.cstv.app.data.local.entity.DownloadedMediaEntity
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.local.entity.TrackPreferenceEntity
import com.cstv.app.data.local.entity.LiveCategoryEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.SeriesCategoryEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity
import com.cstv.app.data.local.entity.EpgCacheEntity
import com.cstv.app.data.local.entity.MediaRatingEntity
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import com.cstv.app.data.local.entity.SeriesSeasonEntity
import com.cstv.app.data.local.entity.SeriesEpisodeEntity
import com.cstv.app.data.local.entity.TrailerCacheEntity
import com.cstv.app.data.local.dao.TrailerCacheDao
import com.cstv.app.data.local.entity.SeriesWatchStateEntity
import com.cstv.app.data.local.entity.MediaRefEntity
import com.cstv.app.data.local.entity.CategoryRefEntity
import com.cstv.app.data.local.entity.DbMaintenanceEntity
import com.cstv.app.data.local.entity.CanonicalMediaLinkEntity
import com.cstv.app.data.local.entity.PlaybackRepairProfileEntity
import com.cstv.app.data.local.entity.SeriesVersionPreferenceEntity
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.dao.DbMaintenanceDao
import com.cstv.app.data.local.dao.CanonicalMediaLinkDao
import com.cstv.app.data.local.dao.PlaybackRepairProfileDao
import com.cstv.app.data.local.dao.SeriesVersionPreferenceDao
import com.cstv.app.data.local.dao.ContentClassificationDao
import com.cstv.app.data.local.entity.ContentClassificationEntity

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
        CategoryPreferenceEntity::class,
        DownloadedMediaEntity::class,
        MediaRatingEntity::class,
        CatalogSyncStateEntity::class,
        SeriesSeasonEntity::class,
        SeriesEpisodeEntity::class,
        TrailerCacheEntity::class,
        SeriesWatchStateEntity::class,
        ProfileSyncStateEntity::class,
        MediaRefEntity::class,
        CategoryRefEntity::class,
        DbMaintenanceEntity::class,
        CanonicalMediaLinkEntity::class,
        PlaybackRepairProfileEntity::class,
        SeriesVersionPreferenceEntity::class,
        ContentClassificationEntity::class
    ],
    version = 37,
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
    abstract fun mediaRatingDao(): MediaRatingDao
    abstract fun catalogSyncStateDao(): CatalogSyncStateDao
    abstract fun trailerCacheDao(): TrailerCacheDao
    abstract fun seriesWatchStateDao(): SeriesWatchStateDao
    abstract fun profileSyncStateDao(): ProfileSyncStateDao
    abstract fun mediaRefDao(): MediaRefDao
    abstract fun categoryRefDao(): CategoryRefDao
    abstract fun dbMaintenanceDao(): DbMaintenanceDao
    abstract fun canonicalMediaLinkDao(): CanonicalMediaLinkDao
    abstract fun playbackRepairProfileDao(): PlaybackRepairProfileDao
    abstract fun seriesVersionPreferenceDao(): SeriesVersionPreferenceDao
    abstract fun contentClassificationDao(): ContentClassificationDao

    companion object {
        /** Nom de fichier `.db` — partagé entre `AppModule` (ouverture) et [com.cstv.app.data.local.db.DatabaseMaintenanceRunner] (`StatFs`). */
        const val DATABASE_NAME = "iptv_xtream_cache.db"
    }
}
