package com.cstv.app.di

import android.content.Context
import androidx.room.Room
import com.cstv.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.cstv.app.data.local.db.AppDatabase
import com.cstv.app.data.local.db.ALL_MIGRATIONS
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.data.local.storage.ProfileManagerImpl
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.repository.ProfileRepositoryImpl
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.data.remote.api.DynamicBaseUrlInterceptor
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.remote.api.XtreamThrottleInterceptor
import com.cstv.app.data.remote.gson.EmptyArrayAsAbsentTypeAdapterFactory
import com.cstv.app.data.remote.gson.SafeIntAdapter
import com.cstv.app.data.remote.gson.SafeLongAdapter
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.repository.AuthRepositoryImpl
import com.cstv.app.data.repository.LiveTvRepositoryImpl
import com.cstv.app.data.repository.VodRepositoryImpl
import com.cstv.app.data.repository.SeriesRepositoryImpl
import com.cstv.app.data.repository.FavoritesRepositoryImpl
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.repository.ViewingHistoryRepository
import com.cstv.app.data.repository.ViewingHistoryRepositoryImpl
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.MediaRatingDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.local.storage.CstvSessionManagerImpl
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.api.CstvIptvCredentialsApiService
import com.cstv.app.data.remote.api.CstvPlaybackLockApiService
import com.cstv.app.data.remote.api.CstvAuthInterceptor
import com.cstv.app.data.remote.api.CstvSessionGuardInterceptor
import com.cstv.app.data.remote.api.CstvObjectsApiService
import com.cstv.app.data.repository.CstvAuthRepositoryImpl
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.data.cloudsync.CloudSyncManagerImpl
import com.cstv.app.data.cloudsync.RoomSnapshotSerializer
import com.cstv.app.data.cloudsync.SnapshotCodec
import com.cstv.app.data.cloudsync.merge.SnapshotMerger
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
    @javax.inject.Named("applicationScope")
    fun provideApplicationScope(): kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    @Provides
    @Singleton
    fun provideTimeProvider(): com.cstv.app.domain.util.TimeProvider =
        com.cstv.app.data.util.SystemTimeProvider()

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
            AppDatabase.DATABASE_NAME
        )
        // Pas de fallbackToDestructiveMigration() : toute évolution de schéma doit
        // fournir une Migration réelle (voir Migrations.kt) pour préserver le cache
        // et les données utilisateur (favoris, historique, positions, profils).
        // Le fallback destructif est réservé aux breaking changes majeurs
        // explicitement décidés et documentés (voir AGENTS.md).
        .addMigrations(*ALL_MIGRATIONS)
        // WAL explicite plutôt que le mode `AUTOMATIC` par défaut.
        //
        // `AUTOMATIC` retombe sur le journal TRUNCATE dès que le système
        // déclare l'appareil « low RAM » — le cas des téléviseurs d'entrée de
        // gamme, dont le tas applicatif plafonne à 192 Mo. Avec TRUNCATE, une
        // écriture prend le verrou de la base et **bloque toutes les lectures**
        // le temps de sa transaction : l'enrichissement de métadonnées ou une
        // synchronisation qui insère des milliers de lignes gèlent alors les
        // requêtes qui alimentent l'écran, sans qu'aucune trace ne l'indique.
        // WAL laisse lecteurs et écrivain avancer de front.
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
    }

    @Provides
    @Singleton
    fun provideLiveTvDao(database: AppDatabase): LiveTvDao {
        return database.liveTvDao()
    }

    @Provides
    @Singleton
    fun provideVodDao(database: AppDatabase): com.cstv.app.data.local.dao.VodDao {
        return database.vodDao()
    }

    @Provides
    @Singleton
    fun provideSeriesDao(database: AppDatabase): com.cstv.app.data.local.dao.SeriesDao {
        return database.seriesDao()
    }

    @Provides
    @Singleton
    fun provideFavoritesDao(database: AppDatabase): com.cstv.app.data.local.dao.FavoritesDao {
        return database.favoritesDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    // --- T20 : identités relationnelles partagées (media_refs / category_refs) ---

    @Provides
    @Singleton
    fun provideMediaRefDao(database: AppDatabase): com.cstv.app.data.local.dao.MediaRefDao = database.mediaRefDao()

    @Provides
    @Singleton
    fun provideCategoryRefDao(database: AppDatabase): com.cstv.app.data.local.dao.CategoryRefDao = database.categoryRefDao()

    // --- T24 : association canonicalId <-> média local (Trending/Popular) ---

    @Provides
    @Singleton
    fun provideCanonicalMediaLinkDao(database: AppDatabase): com.cstv.app.data.local.dao.CanonicalMediaLinkDao = database.canonicalMediaLinkDao()

    @Provides
    @Singleton
    fun provideCanonicalMediaLinkRepository(
        dao: com.cstv.app.data.local.dao.CanonicalMediaLinkDao
    ): com.cstv.app.domain.repository.CanonicalMediaLinkRepository =
        com.cstv.app.data.repository.CanonicalMediaLinkRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideDbMaintenanceDao(database: AppDatabase): com.cstv.app.data.local.dao.DbMaintenanceDao = database.dbMaintenanceDao()

    @Provides
    @Singleton
    fun provideCurrentAccountKeyProvider(
        impl: com.cstv.app.data.local.storage.CurrentAccountKeyProviderImpl
    ): com.cstv.app.data.local.storage.CurrentAccountKeyProvider = impl

    @Provides @Singleton
    fun provideProfileSyncStateDao(database: AppDatabase): ProfileSyncStateDao = database.profileSyncStateDao()

    @Provides
    @Singleton
    fun provideDiskSpaceProbe(): com.cstv.app.data.local.db.DiskSpaceProbe =
        com.cstv.app.data.local.db.DiskSpaceProbe { path -> android.os.StatFs(path).availableBytes }

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideDownloadContentRemover(
        @ApplicationContext context: Context
    ): com.cstv.app.data.sync.DownloadContentRemover =
        com.cstv.app.data.sync.DownloadContentRemover { contentId ->
            androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                context,
                com.cstv.app.data.download.IptvDownloadService::class.java,
                contentId,
                /* foreground= */ false
            )
        }

    @Provides @Singleton
    fun provideCstvSessionManager(@ApplicationContext context: Context): CstvSessionManager = CstvSessionManagerImpl(context)

    @Provides @Singleton
    fun provideCstvErrorMapper(gson: Gson): CstvErrorMapper = CstvErrorMapper(gson)

    @Provides @Singleton fun provideSnapshotCodec(gson: Gson): SnapshotCodec = SnapshotCodec(gson)
    @Provides @Singleton fun provideSnapshotMerger(): SnapshotMerger = SnapshotMerger()
    @Provides @Singleton
    fun provideSnapshotSerializer(impl: RoomSnapshotSerializer): com.cstv.app.data.cloudsync.SnapshotSerializer = impl
    @Provides @Singleton
    fun provideCloudSyncManager(impl: CloudSyncManagerImpl): com.cstv.app.domain.sync.CloudSyncManager = impl

    @Provides
    @Singleton
    fun provideMediaRatingDao(database: AppDatabase): MediaRatingDao = database.mediaRatingDao()

    @Provides
    @Singleton
    fun provideCatalogSyncStateDao(database: AppDatabase): com.cstv.app.data.local.dao.CatalogSyncStateDao =
        database.catalogSyncStateDao()

    @Provides
    @Singleton
    fun provideTrailerCacheDao(database: AppDatabase): com.cstv.app.data.local.dao.TrailerCacheDao = database.trailerCacheDao()

    // Interface plutôt que classe concrète : isCurrentlyOnline() retourne un
    // Boolean primitif, et un mock de classe Kotlin sur retour primitif
    // provoque un NPE d'unboxing avec la config Mockito du projet (AGENTS.md).
    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): com.cstv.app.domain.network.NetworkMonitor =
        com.cstv.app.data.network.NetworkMonitorImpl(context)

    @Provides
    @Singleton
    fun provideCatalogSyncManager(
        liveTvRepository: LiveTvRepository,
        vodRepository: VodRepository,
        seriesRepository: SeriesRepository,
        syncStateDao: com.cstv.app.data.local.dao.CatalogSyncStateDao,
        credentialsManager: CredentialsManager,
        settingsManager: SettingsManager,
        networkMonitor: com.cstv.app.domain.network.NetworkMonitor,
        syncStateInitializer: com.cstv.app.data.sync.CatalogSyncStateInitializer,
        clearCatalogCacheUseCase: com.cstv.app.domain.usecase.ClearCatalogCacheUseCase,
        categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository,
        catalogReconciler: com.cstv.app.data.sync.CatalogReconciler
    ): com.cstv.app.domain.sync.CatalogSyncManager =
        com.cstv.app.data.sync.CatalogSyncManagerImpl(
            liveTvRepository,
            vodRepository,
            seriesRepository,
            syncStateDao,
            credentialsManager,
            settingsManager,
            networkMonitor,
            syncStateInitializer,
            clearCatalogCacheUseCase,
            categoryPreferenceRepository,
            catalogReconciler
        )

    @Provides
    @Singleton
    fun provideTrackPreferenceDao(database: AppDatabase): com.cstv.app.data.local.dao.TrackPreferenceDao {
        return database.trackPreferenceDao()
    }

    @Provides
    @Singleton
    fun provideTrackPreferenceRepository(
        dao: com.cstv.app.data.local.dao.TrackPreferenceDao,
        profileManager: ProfileManager,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): com.cstv.app.domain.repository.TrackPreferenceRepository {
        return com.cstv.app.data.repository.TrackPreferenceRepositoryImpl(dao, profileManager, mediaRefDao, accountKeyProvider, sync)
    }

    @Provides
    @Singleton
    fun provideCategoryPreferenceDao(database: AppDatabase): com.cstv.app.data.local.dao.CategoryPreferenceDao {
        return database.categoryPreferenceDao()
    }

    @Provides
    @Singleton
    fun provideCategoryPreferenceRepository(
        dao: com.cstv.app.data.local.dao.CategoryPreferenceDao,
        profileManager: ProfileManager,
        categoryRefDao: com.cstv.app.data.local.dao.CategoryRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): com.cstv.app.domain.repository.CategoryPreferenceRepository {
        return com.cstv.app.data.repository.CategoryPreferenceRepositoryImpl(dao, profileManager, categoryRefDao, accountKeyProvider, sync)
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
        trackPreferenceDao: com.cstv.app.data.local.dao.TrackPreferenceDao,
        categoryPreferenceDao: com.cstv.app.data.local.dao.CategoryPreferenceDao,
        mediaRatingDao: MediaRatingDao,
        seriesWatchStateDao: com.cstv.app.data.local.dao.SeriesWatchStateDao,
        profileSyncStateDao: ProfileSyncStateDao,
        cstvProfileGateway: com.cstv.app.data.repository.CstvProfileGateway,
        database: AppDatabase
    ): ProfileRepository {
        return ProfileRepositoryImpl(profileDao, profileManager, favoritesDao, vodDao, liveTvDao, trackPreferenceDao, categoryPreferenceDao, mediaRatingDao, seriesWatchStateDao, profileSyncStateDao, cstvProfileGateway, database)
    }

    @Provides
    @Singleton
    fun provideMediaRatingRepository(
        database: AppDatabase,
        mediaRatingDao: MediaRatingDao,
        favoritesDao: FavoritesDao,
        vodDao: VodDao,
        profileManager: ProfileManager,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): com.cstv.app.domain.repository.MediaRatingRepository =
        com.cstv.app.data.repository.MediaRatingRepositoryImpl(database, mediaRatingDao, favoritesDao, vodDao, profileManager, mediaRefDao, accountKeyProvider, sync)

    @Provides
    @Singleton
    fun provideViewingHistoryRepository(
        vodDao: VodDao,
        liveTvDao: LiveTvDao,
        profileManager: ProfileManager,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): ViewingHistoryRepository = ViewingHistoryRepositoryImpl(vodDao, liveTvDao, profileManager, mediaRefDao, accountKeyProvider, sync)

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
            .registerTypeAdapterFactory(EmptyArrayAsAbsentTypeAdapterFactory())
            .registerTypeAdapter(Int::class.java, SafeIntAdapter())
            .registerTypeAdapter(Int::class.javaPrimitiveType, SafeIntAdapter())
            .registerTypeAdapter(Long::class.java, SafeLongAdapter())
            .registerTypeAdapter(Long::class.javaPrimitiveType, SafeLongAdapter())
            .create()
    }

    @Provides @Singleton
    @javax.inject.Named("cstv")
    fun provideCstvOkHttpClient(auth: CstvAuthInterceptor, guard: CstvSessionGuardInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC; redactHeader("Authorization") }
        return OkHttpClient.Builder().addInterceptor(auth).addInterceptor(guard).addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    }

    @Provides @Singleton
    @javax.inject.Named("cstv")
    fun provideCstvRetrofit(@javax.inject.Named("cstv") client: OkHttpClient, gson: Gson): Retrofit {
        val baseUrl = requireNotNull(BuildConfig.CSTV_BASE_URL.takeIf { it.isNotBlank() }) { "CSTV_BASE_URL is missing" }.let { if (it.endsWith('/')) it else "$it/" }
        return Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build()
    }

    @Provides @Singleton
    @javax.inject.Named("cstvObjects")
    fun provideCstvObjectsRetrofit(@javax.inject.Named("cstv") client: OkHttpClient, gson: Gson): Retrofit {
        val baseUrl = requireNotNull(BuildConfig.CSTV_BASE_URL.takeIf { it.isNotBlank() }) { "CSTV_BASE_URL is missing" }.let { if (it.endsWith('/')) it else "$it/" }
        val objectClient = client.newBuilder().readTimeout(20, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
        return Retrofit.Builder().baseUrl(baseUrl).client(objectClient).addConverterFactory(GsonConverterFactory.create(gson)).build()
    }

    @Provides @Singleton fun provideCstvApiService(@javax.inject.Named("cstv") retrofit: Retrofit): CstvApiService = retrofit.create(CstvApiService::class.java)
    @Provides @Singleton fun provideCstvCatalogApiService(@javax.inject.Named("cstv") retrofit: Retrofit): CstvCatalogApiService = retrofit.create(CstvCatalogApiService::class.java)
    @Provides @Singleton fun provideCstvIptvCredentialsApiService(@javax.inject.Named("cstv") retrofit: Retrofit): CstvIptvCredentialsApiService = retrofit.create(CstvIptvCredentialsApiService::class.java)
    @Provides @Singleton fun provideCstvPlaybackLockApiService(@javax.inject.Named("cstv") retrofit: Retrofit): CstvPlaybackLockApiService = retrofit.create(CstvPlaybackLockApiService::class.java)
    @Provides @Singleton fun provideCstvObjectsApiService(@javax.inject.Named("cstvObjects") retrofit: Retrofit): CstvObjectsApiService = retrofit.create(CstvObjectsApiService::class.java)
    @Provides @Singleton fun providePlaybackLockRepository(impl: com.cstv.app.data.repository.PlaybackLockRepositoryImpl): com.cstv.app.domain.repository.PlaybackLockRepository = impl
    @Provides @Singleton fun providePlaybackLockRequester(impl: com.cstv.app.domain.usecase.RequestPlaybackLockUseCase): com.cstv.app.domain.usecase.PlaybackLockRequester = impl
    @Provides @Singleton fun providePlaybackLockReleaser(impl: com.cstv.app.domain.usecase.ReleasePlaybackLockUseCase): com.cstv.app.domain.usecase.PlaybackLockReleaser = impl
    @Provides @Singleton
    fun provideCloudProfileEmptinessProbe(impl: com.cstv.app.data.repository.CstvCloudProfileEmptinessProbe): com.cstv.app.data.repository.CloudProfileEmptinessProbe = impl
    @Provides @Singleton
    fun provideCstvAuthRepository(impl: CstvAuthRepositoryImpl): CstvAuthRepository = impl
    @Provides @Singleton fun provideIptvCloudBackupStore(impl: com.cstv.app.data.local.storage.IptvCloudBackupStoreImpl): com.cstv.app.data.local.storage.IptvCloudBackupStore = impl
    @Provides @Singleton fun provideIptvCredentialsBackupScheduler(impl: com.cstv.app.data.worker.WorkManagerIptvCredentialsBackupScheduler): com.cstv.app.data.worker.IptvCredentialsBackupScheduler = impl
    @Provides @Singleton fun provideIptvCredentialsBackupRepository(impl: com.cstv.app.data.repository.IptvCredentialsBackupRepositoryImpl): com.cstv.app.domain.repository.IptvCredentialsBackupRepository = impl
    @Provides @Singleton fun provideDeviceIdentityProvider(impl: com.cstv.app.data.local.storage.DeviceIdentityManager): com.cstv.app.data.local.storage.DeviceIdentityProvider = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: DynamicBaseUrlInterceptor,
        requestGate: XtreamRequestGate
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
            // Avant la réécriture d'URL : chaque rejeu doit repasser par elle.
            .addInterceptor(XtreamThrottleInterceptor(requestGate))
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Les délais ci-dessus ne couvrent pas l'attente dans la file du
            // Dispatcher : sans plafond global, une requête écran coincée derrière
            // le trafic de sync ne rend jamais la main et l'écran tourne à vide.
            .callTimeout(60, TimeUnit.SECONDS)
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
        requestGate: XtreamRequestGate,
        trailerRepository: com.cstv.app.domain.repository.TrailerRepository,
        networkMonitor: com.cstv.app.domain.network.NetworkMonitor,
        syncStateDao: com.cstv.app.data.local.dao.CatalogSyncStateDao,
        syncStateInitializer: com.cstv.app.data.sync.CatalogSyncStateInitializer,
        catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager,
        mediaRefAccountBinder: com.cstv.app.data.sync.MediaRefAccountBinder
    ): AuthRepository {
        return AuthRepositoryImpl(
            apiService,
            credentialsManager,
            baseUrlInterceptor,
            requestGate,
            trailerRepository,
            networkMonitor,
            syncStateDao,
            syncStateInitializer,
            catalogSyncManager,
            mediaRefAccountBinder
        )
    }

    @Provides
    @Singleton
    fun provideLiveTvRepository(
        apiService: XtreamApiService,
        liveTvDao: LiveTvDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager,
        requestGate: XtreamRequestGate,
        networkMonitor: com.cstv.app.domain.network.NetworkMonitor,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        cloudSyncManager: com.cstv.app.domain.sync.CloudSyncManager
    ): LiveTvRepository {
        return LiveTvRepositoryImpl(apiService, liveTvDao, credentialsManager, profileManager, requestGate, networkMonitor, mediaRefDao, accountKeyProvider, cloudSyncManager)
    }

    @Provides
    @Singleton
    fun provideVodRepository(
        apiService: XtreamApiService,
        vodDao: VodDao,
        seriesDao: SeriesDao,
        credentialsManager: CredentialsManager,
        profileManager: ProfileManager,
        requestGate: XtreamRequestGate,
        networkMonitor: com.cstv.app.domain.network.NetworkMonitor,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        cloudSyncManager: com.cstv.app.domain.sync.CloudSyncManager
    ): VodRepository {
        return VodRepositoryImpl(apiService, vodDao, seriesDao, credentialsManager, profileManager, requestGate, networkMonitor, mediaRefDao, accountKeyProvider, cloudSyncManager)
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
        networkMonitor: com.cstv.app.domain.network.NetworkMonitor,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider
    ): SeriesRepository {
        return SeriesRepositoryImpl(apiService, seriesDao, vodDao, credentialsManager, profileManager, requestGate, networkMonitor, accountKeyProvider)
    }

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        favoritesDao: FavoritesDao,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        profileManager: ProfileManager,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): FavoritesRepository {
        return FavoritesRepositoryImpl(favoritesDao, mediaRefDao, profileManager, accountKeyProvider, sync)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): com.cstv.app.data.local.dao.DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        downloadDao: com.cstv.app.data.local.dao.DownloadDao,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider
    ): com.cstv.app.domain.repository.DownloadRepository {
        return com.cstv.app.data.repository.DownloadRepositoryImpl(context, downloadDao, mediaRefDao, accountKeyProvider)
    }

    @Provides
    @Singleton
    fun provideTrendingRepository(
        @ApplicationContext context: Context,
        catalogApiService: CstvCatalogApiService,
        gson: Gson
    ): com.cstv.app.domain.repository.TrendingRepository {
        return com.cstv.app.data.repository.TrendingRepositoryImpl(context, catalogApiService, gson)
    }

    @Provides
    @Singleton
    fun providePopularRepository(
        @ApplicationContext context: Context,
        catalogApiService: CstvCatalogApiService,
        gson: Gson
    ): com.cstv.app.domain.repository.PopularRepository {
        return com.cstv.app.data.repository.PopularRepositoryImpl(context, catalogApiService, gson)
    }

    @Provides
    @Singleton
    fun provideTrailerRepository(
        xtreamApiService: XtreamApiService,
        catalogApiService: CstvCatalogApiService,
        credentialsManager: CredentialsManager,
        requestGate: XtreamRequestGate,
        trailerCacheDao: com.cstv.app.data.local.dao.TrailerCacheDao,
        timeProvider: com.cstv.app.domain.util.TimeProvider,
        @javax.inject.Named("applicationScope") applicationScope: kotlinx.coroutines.CoroutineScope
    ): com.cstv.app.domain.repository.TrailerRepository =
        com.cstv.app.data.repository.TrailerRepositoryImpl(xtreamApiService, catalogApiService, credentialsManager, requestGate, trailerCacheDao, timeProvider, applicationScope)

    // --- F12 : détection et notification de nouveaux épisodes ---

    @Provides
    @Singleton
    fun provideSeriesWatchStateDao(database: AppDatabase): com.cstv.app.data.local.dao.SeriesWatchStateDao =
        database.seriesWatchStateDao()

    @Provides
    @Singleton
    fun provideSeriesWatchStateRepository(
        dao: com.cstv.app.data.local.dao.SeriesWatchStateDao,
        favoritesDao: FavoritesDao,
        vodDao: VodDao,
        timeProvider: com.cstv.app.domain.util.TimeProvider,
        mediaRefDao: com.cstv.app.data.local.dao.MediaRefDao,
        accountKeyProvider: com.cstv.app.data.local.storage.CurrentAccountKeyProvider,
        sync: com.cstv.app.domain.sync.CloudSyncManager
    ): com.cstv.app.domain.repository.SeriesWatchStateRepository =
        com.cstv.app.data.repository.SeriesWatchStateRepositoryImpl(dao, favoritesDao, vodDao, timeProvider, mediaRefDao, accountKeyProvider, sync)

    @Provides
    @Singleton
    fun provideNewEpisodeNotifier(
        @ApplicationContext context: Context
    ): com.cstv.app.domain.notification.NewEpisodeNotifier =
        com.cstv.app.data.notification.AndroidNewEpisodeNotifier(context)

    // --- F35 : détection et installation des mises à jour in-app ---

    @Provides
    @Singleton
    fun provideAppUpdatePreferences(
        @ApplicationContext context: Context
    ): com.cstv.app.data.local.storage.AppUpdatePreferences =
        com.cstv.app.data.local.storage.AppUpdatePreferences(context)

    @Provides @Singleton
    @javax.inject.Named("github")
    fun provideGithubOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        // Client dédié (§4.1) : jamais partagé avec Xtream (réécriture d'URL,
        // identifiants) ni CSTV (jeton Authorization). Aucun secret envoyé à
        // GitHub. Pas de `callTimeout` : le téléchargement de l'APK partage ce
        // client et ne doit pas être plafonné en durée totale.
        // F35-R4 : `followSslRedirects` vaut `true` par défaut chez OkHttp, ce
        // qui autoriserait GitHub à rediriger le téléchargement de l'APK vers
        // du HTTP. `false` refuse le suivi d'une redirection HTTPS -> HTTP ;
        // l'intercepteur réseau ci-dessous est une seconde barrière qui refuse
        // explicitement tout appel réseau, y compris un hop de redirection,
        // dont l'URL n'est pas HTTPS.
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isHttps) throw java.io.IOException("Downgrade HTTP refusé : ${request.url}")
                chain.proceed(request)
            }
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideGithubReleaseApiService(
        @javax.inject.Named("github") client: OkHttpClient,
        gson: Gson
    ): com.cstv.app.data.remote.api.GithubReleaseApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(com.cstv.app.data.remote.api.GithubReleaseApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideApkInspector(
        impl: com.cstv.app.data.update.AndroidApkInspector
    ): com.cstv.app.data.update.ApkInspector = impl

    @Provides
    @Singleton
    fun provideUpdateApkStore(
        @ApplicationContext context: Context,
        apkInspector: com.cstv.app.data.update.ApkInspector
    ): com.cstv.app.data.update.UpdateApkStore =
        com.cstv.app.data.update.UpdateApkStore(
            directory = context.cacheDir.resolve("app_updates"),
            apkInspector = apkInspector,
            applicationId = BuildConfig.APPLICATION_ID
        )

    @Provides
    @Singleton
    fun provideAppUpdateRepository(
        impl: com.cstv.app.data.repository.AppUpdateRepositoryImpl
    ): com.cstv.app.domain.repository.AppUpdateRepository = impl

    @Provides
    @Singleton
    fun providePackageInstallerGateway(
        impl: com.cstv.app.data.update.AndroidPackageInstaller
    ): com.cstv.app.domain.update.PackageInstallerGateway = impl
}
