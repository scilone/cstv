package com.poc.iptvxtream.di

import android.content.Context
import androidx.room.Room
import com.poc.iptvxtream.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.poc.iptvxtream.data.local.db.AppDatabase
import com.poc.iptvxtream.data.local.db.ALL_MIGRATIONS
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
import com.poc.iptvxtream.data.remote.api.XtreamRequestGate
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
import okhttp3.Dispatcher
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
        // Pas de fallbackToDestructiveMigration() : toute évolution de schéma doit
        // fournir une Migration réelle (voir Migrations.kt) pour préserver le cache
        // et les données utilisateur (favoris, historique, positions, profils).
        // Le fallback destructif est réservé aux breaking changes majeurs
        // explicitement décidés et documentés (voir AGENTS.md).
        .addMigrations(*ALL_MIGRATIONS)
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
    fun provideTrackPreferenceDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.TrackPreferenceDao {
        return database.trackPreferenceDao()
    }

    @Provides
    @Singleton
    fun provideTrackPreferenceRepository(
        dao: com.poc.iptvxtream.data.local.dao.TrackPreferenceDao,
        profileManager: ProfileManager
    ): com.poc.iptvxtream.domain.repository.TrackPreferenceRepository {
        return com.poc.iptvxtream.data.repository.TrackPreferenceRepositoryImpl(dao, profileManager)
    }

    @Provides
    @Singleton
    fun provideCategoryPreferenceDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.CategoryPreferenceDao {
        return database.categoryPreferenceDao()
    }

    @Provides
    @Singleton
    fun provideCategoryPreferenceRepository(
        dao: com.poc.iptvxtream.data.local.dao.CategoryPreferenceDao,
        profileManager: ProfileManager
    ): com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository {
        return com.poc.iptvxtream.data.repository.CategoryPreferenceRepositoryImpl(dao, profileManager)
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
        liveTvDao: LiveTvDao,
        trackPreferenceDao: com.poc.iptvxtream.data.local.dao.TrackPreferenceDao,
        categoryPreferenceDao: com.poc.iptvxtream.data.local.dao.CategoryPreferenceDao
    ): ProfileRepository {
        return ProfileRepositoryImpl(profileDao, profileManager, favoritesDao, vodDao, liveTvDao, trackPreferenceDao, categoryPreferenceDao)
    }

    @Provides
    @Singleton
    fun provideDynamicBaseUrlInterceptor(): DynamicBaseUrlInterceptor {
        return DynamicBaseUrlInterceptor()
    }

    // XtreamRequestGate a un constructeur @Inject : Hilt le fournit
    // automatiquement, pas besoin de @Provides ici (créerait un doublon).

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
        // Phase 36 : BODY loggue l'intégralité des réponses (catalogues de
        // plusieurs Mo) et les URLs Xtream contenant username/password en
        // clair dans logcat. Réservé au debug ; aucun log HTTP en release.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // La priorité écran/arrière-plan (XtreamRequestGate) fait déjà céder le
        // sync/enrichissement dès qu'une requête écran est active : la vraie
        // cause du blocage de "Tout" pendant un sync est réglée là, pas ici.
        // Ce plafond OkHttp n'a donc plus besoin d'être restrictif — remis à 5
        // (défaut historique d'OkHttp avant l'ajout de ce Dispatcher explicite)
        // pour ne pas ralentir le chargement à froid (Home + Films/Séries tirent
        // plusieurs requêtes en parallèle : live, vod, séries, EPG...).
        val dispatcher = Dispatcher().apply {
            maxRequests = 5
            maxRequestsPerHost = 5
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
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
        baseUrlInterceptor: DynamicBaseUrlInterceptor,
        requestGate: XtreamRequestGate
    ): AuthRepository {
        return AuthRepositoryImpl(apiService, credentialsManager, baseUrlInterceptor, requestGate)
    }

    @Provides
    @Singleton
    fun provideLiveTvRepository(
        apiService: XtreamApiService,
        liveTvDao: LiveTvDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager,
        requestGate: XtreamRequestGate,
        settingsManager: SettingsManager
    ): LiveTvRepository {
        return LiveTvRepositoryImpl(apiService, liveTvDao, credentialsManager, profileManager, requestGate, settingsManager)
    }

    @Provides
    @Singleton
    fun provideVodRepository(
        apiService: XtreamApiService,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager,
        requestGate: XtreamRequestGate,
        settingsManager: SettingsManager
    ): VodRepository {
        return VodRepositoryImpl(apiService, vodDao, credentialsManager, profileManager, requestGate, settingsManager)
    }

    @Provides
    @Singleton
    fun provideSeriesRepository(
        apiService: XtreamApiService,
        seriesDao: SeriesDao,
        vodDao: VodDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager,
        requestGate: XtreamRequestGate,
        settingsManager: SettingsManager
    ): SeriesRepository {
        return SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager, requestGate, settingsManager)
    }

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        favoritesDao: FavoritesDao,
        profileManager: ProfileManager
    ): FavoritesRepository {
        return FavoritesRepositoryImpl(favoritesDao, profileManager)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): com.poc.iptvxtream.data.local.dao.DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        downloadDao: com.poc.iptvxtream.data.local.dao.DownloadDao
    ): com.poc.iptvxtream.domain.repository.DownloadRepository {
        return com.poc.iptvxtream.data.repository.DownloadRepositoryImpl(context, downloadDao)
    }
}
