package com.poc.iptvxtream.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.poc.iptvxtream.data.local.db.AppDatabase
import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.data.local.storage.ProfileManagerImpl
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.dao.ProfileDao
import com.poc.iptvxtream.data.repository.ProfileRepositoryImpl
import com.poc.iptvxtream.domain.repository.ProfileRepository
import com.poc.iptvxtream.data.remote.api.DynamicBaseUrlInterceptor
import com.poc.iptvxtream.data.remote.api.XtreamApiService
import com.poc.iptvxtream.data.remote.gson.SafeIntAdapter
import com.poc.iptvxtream.data.remote.gson.SafeLongAdapter
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.repository.AuthRepositoryImpl
import com.poc.iptvxtream.data.repository.LiveTvRepositoryImpl
import com.poc.iptvxtream.data.repository.VodRepositoryImpl
import com.poc.iptvxtream.data.repository.SeriesRepositoryImpl
import com.poc.iptvxtream.data.repository.FavoritesRepositoryImpl
import com.poc.iptvxtream.domain.repository.AuthRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.data.local.dao.SeriesDao
import com.poc.iptvxtream.data.local.dao.FavoritesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "iptv_xtream_cache.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideLiveTvDao(database: AppDatabase): LiveTvDao {
        return database.liveTvDao()
    }

    @Provides
    @Singleton
    fun provideVodDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.VodDao {
        return database.vodDao()
    }

    @Provides
    @Singleton
    fun provideSeriesDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.SeriesDao {
        return database.seriesDao()
    }

    @Provides
    @Singleton
    fun provideFavoritesDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.FavoritesDao {
        return database.favoritesDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideProfileManager(
        @ApplicationContext context: Context
    ): ProfileManager {
        return ProfileManagerImpl(context)
    }

    @Provides
    @Singleton
    fun provideProfileRepository(
        profileDao: ProfileDao,
        profileManager: ProfileManager,
        favoritesDao: FavoritesDao,
        vodDao: VodDao,
        liveTvDao: LiveTvDao
    ): ProfileRepository {
        return ProfileRepositoryImpl(profileDao, profileManager, favoritesDao, vodDao, liveTvDao)
    }

    @Provides
    @Singleton
    fun provideDynamicBaseUrlInterceptor(): DynamicBaseUrlInterceptor {
        return DynamicBaseUrlInterceptor()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Int::class.java, SafeIntAdapter())
            .registerTypeAdapter(Int::class.javaPrimitiveType, SafeIntAdapter())
            .registerTypeAdapter(Long::class.java, SafeLongAdapter())
            .registerTypeAdapter(Long::class.javaPrimitiveType, SafeLongAdapter())
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApiService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): XtreamApiService {
        // Use an arbitrary localhost placeholder, DynamicBaseUrlInterceptor will rewrite it dynamically.
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCredentialsManager(
        @ApplicationContext context: Context
    ): CredentialsManager {
        return CredentialsManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context
    ): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: XtreamApiService,
        credentialsManager: CredentialsManager,
        baseUrlInterceptor: DynamicBaseUrlInterceptor
    ): AuthRepository {
        return AuthRepositoryImpl(apiService, credentialsManager, baseUrlInterceptor)
    }

    @Provides
    @Singleton
    fun provideLiveTvRepository(
        apiService: XtreamApiService,
        liveTvDao: LiveTvDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager
    ): LiveTvRepository {
        return LiveTvRepositoryImpl(apiService, liveTvDao, credentialsManager, profileManager)
    }

    @Provides
    @Singleton
    fun provideVodRepository(
        apiService: XtreamApiService,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager
    ): VodRepository {
        return VodRepositoryImpl(apiService, vodDao, credentialsManager, profileManager)
    }

    @Provides
    @Singleton
    fun provideSeriesRepository(
        apiService: XtreamApiService,
        seriesDao: SeriesDao,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager
    ): SeriesRepository {
        return SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager)
    }

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        favoritesDao: FavoritesDao,
        profileManager: ProfileManager
    ): FavoritesRepository {
        return FavoritesRepositoryImpl(favoritesDao, profileManager)
    }
}
