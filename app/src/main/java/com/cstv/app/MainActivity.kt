package com.cstv.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.presentation.home.HomeScreen
import com.cstv.app.presentation.home.HomeViewModel
import com.cstv.app.presentation.home.RecentlyAddedScreen
import com.cstv.app.presentation.home.RecentlyAddedViewModel
import com.cstv.app.presentation.livetv.LiveTvScreen
import com.cstv.app.presentation.livetv.LiveTvViewModel
import com.cstv.app.presentation.login.LoginScreen
import com.cstv.app.presentation.login.LoginViewModel
import com.cstv.app.presentation.login.AutoLoginState
import com.cstv.app.presentation.login.SplashScreen
import com.cstv.app.presentation.player.PlayerScreen
import com.cstv.app.presentation.vod.VodDetailsScreen
import com.cstv.app.presentation.vod.VodPlayerScreen
import com.cstv.app.presentation.vod.VodScreen
import com.cstv.app.presentation.vod.VodViewModel
import com.cstv.app.presentation.series.SeriesScreen
import com.cstv.app.presentation.series.SeriesDetailsScreen
import com.cstv.app.presentation.series.SeriesPlayerScreen
import com.cstv.app.presentation.series.SeriesViewModel
import com.cstv.app.presentation.favorites.FavoritesScreen
import com.cstv.app.presentation.favorites.FavoritesViewModel
import com.cstv.app.presentation.search.SearchScreen
import com.cstv.app.presentation.settings.SettingsScreen
import com.cstv.app.presentation.profile.ProfileViewModel
import com.cstv.app.presentation.profile.ProfileSelectionScreen
import com.cstv.app.presentation.settings.SettingsViewModel
import com.cstv.app.presentation.navigation.AppNavGraph
import com.cstv.app.presentation.theme.IptvXtreamTheme
import com.cstv.app.presentation.theme.mobileBackground
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    LIVETV,
    PLAYER,
    VOD_GRID,
    VOD_DETAILS,
    VOD_PLAYER,
    SERIES_GRID,
    SERIES_DETAILS,
    SERIES_PLAYER,
    FAVORITES,
    SEARCH,
    SETTINGS,
    CATEGORY_MANAGEMENT,
    PROFILE_SELECTION,
    PROFILE_MANAGEMENT,
    RECENTLY_ADDED,
    DOWNLOADS
}

enum class MobileTab(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "Accueil", Icons.Default.Home),
    TV("tv", "TV", Icons.Default.LiveTv),
    MOVIES("movies", "Films", Icons.Default.Movie),
    SERIES("series", "Séries", Icons.Default.Tv),
    SEARCH("search", "Recherche", Icons.Default.Search)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val isTv = remember { isTvDevice(context) }
            
            Surface(modifier = Modifier.fillMaxSize()) {
                val loginViewModel: LoginViewModel = hiltViewModel()
                val favoritesViewModel: FavoritesViewModel = hiltViewModel()
                val homeViewModel: HomeViewModel = hiltViewModel()
                val settingsViewModel: SettingsViewModel = hiltViewModel()

                val autoLoginState by loginViewModel.autoLoginState.collectAsStateWithLifecycle()
                
                var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
                var loggedInUser by remember { mutableStateOf<UserInfo?>(null) }

                LaunchedEffect(autoLoginState) {
                    when (autoLoginState) {
                        is AutoLoginState.Success -> {
                            loggedInUser = (autoLoginState as AutoLoginState.Success).userInfo
                            if (isTv) {
                                currentScreen = AppScreen.DASHBOARD
                            }
                        }
                        is AutoLoginState.Error -> {
                            loginViewModel.setError((autoLoginState as AutoLoginState.Error).message)
                            if (isTv) {
                                currentScreen = AppScreen.LOGIN
                            }
                        }
                        is AutoLoginState.NoCredentials -> {
                            if (isTv) {
                                currentScreen = AppScreen.LOGIN
                            }
                        }
                        is AutoLoginState.Checking -> {
                            // Do nothing
                        }
                    }
                }

                val screenHistory = remember { mutableStateListOf<AppScreen>() }
                val homeLazyListState = rememberLazyListState()

                fun navigateTo(screen: AppScreen) {
                    if (currentScreen != screen) {
                        screenHistory.add(currentScreen)
                        currentScreen = screen
                    }
                }

                fun navigateBack() {
                    if (screenHistory.isNotEmpty()) {
                        currentScreen = screenHistory.removeAt(screenHistory.lastIndex)
                    } else {
                        currentScreen = AppScreen.DASHBOARD
                    }
                }
                
                // Track current playing stream and list (Live TV)
                var activeStream by remember { mutableStateOf<LiveStream?>(null) }
                var activeStreamsList by remember { mutableStateOf<List<LiveStream>>(emptyList()) }

                // Track current selected VOD details and playing position (VOD)
                var activeVodMovie by remember { mutableStateOf<VodStream?>(null) }
                var activeVodDetails by remember { mutableStateOf<VodDetails?>(null) }
                var resumePositionMs by remember { mutableStateOf(0L) }

                // Track current selected Series details and active playing episode (Series)
                var activeSeriesShow by remember { mutableStateOf<SeriesStream?>(null) }
                var activeSeriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
                var activeEpisode by remember { mutableStateOf<SeriesEpisode?>(null) }
                var recentlyAddedIsSeries by remember { mutableStateOf(false) }

                // Get global reactive favorites list
                val favsState by favoritesViewModel.state.collectAsStateWithLifecycle()

                // Téléchargements hors-ligne (feature #15) : ViewModel partagé,
                // état réactif consommé par les détails + l'écran Téléchargements.
                val downloadsViewModel: com.cstv.app.presentation.downloads.DownloadsViewModel = hiltViewModel()
                val downloadsState by downloadsViewModel.state.collectAsStateWithLifecycle()

                // --- Sélection de profil (Phase 27) ---
                // Gate unique couvrant TV et mobile : après login/auto-login, on
                // garantit un profil actif et on affiche l'écran de sélection
                // uniquement s'il existe plusieurs profils.
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                var profileSelectionNeeded by remember { mutableStateOf(false) }
                var profileGateResolved by remember { mutableStateOf(false) }
                var showManagementFromGate by remember { mutableStateOf(false) }

                LaunchedEffect(loggedInUser) {
                    if (loggedInUser != null && !profileGateResolved) {
                        val needsSelection = profileViewModel.ensureInitializedAndNeedsSelection()
                        profileSelectionNeeded = needsSelection
                        if (!needsSelection) profileGateResolved = true
                    } else if (loggedInUser == null) {
                        profileGateResolved = false
                        profileSelectionNeeded = false
                    }
                }

                // Garde le splash tant que la vérification est en cours, ET tant que
                // l'auto-login a réussi mais que loggedInUser n'est pas encore propagé
                // par le LaunchedEffect. Sans ça, sur mobile le NavHost se compose avec
                // loggedInUser==null et latche startDestination sur "login" malgré le succès.
                val showSplash = autoLoginState is AutoLoginState.Checking ||
                    (autoLoginState is AutoLoginState.Success && loggedInUser == null)

                val showProfileSelection = loggedInUser != null &&
                    profileSelectionNeeded && !profileGateResolved

                if (showSplash) {
                    SplashScreen()
                } else if (showProfileSelection && showManagementFromGate) {
                    com.cstv.app.presentation.profile.ProfileManagementScreen(
                        viewModel = profileViewModel,
                        isTv = isTv,
                        onBack = { showManagementFromGate = false }
                    )
                } else if (showProfileSelection) {
                    ProfileSelectionScreen(
                        profiles = profileState.profiles,
                        onProfileSelected = { profile ->
                            profileViewModel.selectProfile(profile.id)
                            profileGateResolved = true
                        },
                        onManageProfiles = { showManagementFromGate = true },
                        onLogout = {
                            loginViewModel.logout()
                            loggedInUser = null
                            screenHistory.clear()
                            currentScreen = AppScreen.LOGIN
                        }
                    )
                } else {
                    if (isTv) {
                        // Safe Back Button Handler for Android TV / Custom Back
                    BackHandler(enabled = currentScreen != AppScreen.LOGIN) {
                        if (currentScreen == AppScreen.DASHBOARD) {
                            loginViewModel.resetState()
                            loggedInUser = null
                            screenHistory.clear()
                            currentScreen = AppScreen.LOGIN
                        } else {
                            navigateBack()
                        }
                    }

                    when (currentScreen) {
                        AppScreen.LOGIN -> {
                            LoginScreen(
                                viewModel = loginViewModel,
                                isTv = isTv,
                                onLoginSuccess = { userInfo ->
                                    loggedInUser = userInfo
                                    screenHistory.clear()
                                    currentScreen = AppScreen.DASHBOARD
                                }
                            )
                        }
                        AppScreen.DASHBOARD -> {
                            val activeProfile = profileState.profiles.find { it.id == profileState.activeProfileId }
                            HomeScreen(
                                userInfo = loggedInUser!!,
                                isTv = isTv,
                                viewModel = homeViewModel,
                                activeProfileAvatarId = activeProfile?.avatarId ?: 0,
                                activeProfileName = activeProfile?.name ?: loggedInUser?.username ?: "",
                                lazyListState = homeLazyListState,
                                onNavigateToLiveTv = {
                                    navigateTo(AppScreen.LIVETV)
                                },
                                onNavigateToVod = {
                                    navigateTo(AppScreen.VOD_GRID)
                                },
                                onNavigateToSeries = {
                                    navigateTo(AppScreen.SERIES_GRID)
                                },
                                onNavigateToRecentlyAdded = { isSeries ->
                                    recentlyAddedIsSeries = isSeries
                                    navigateTo(AppScreen.RECENTLY_ADDED)
                                },
                                onNavigateToFavorites = {
                                    navigateTo(AppScreen.FAVORITES)
                                },
                                onNavigateToSearch = {
                                    navigateTo(AppScreen.SEARCH)
                                },
                                onNavigateToSettings = {
                                    navigateTo(AppScreen.SETTINGS)
                                },
                                onNavigateToProfileManagement = {
                                    navigateTo(AppScreen.PROFILE_SELECTION)
                                },
                                onPlayResumeWatchingMovie = { position ->
                                    activeVodDetails = VodDetails(
                                        streamId = position.streamId,
                                        name = position.title ?: "Film",
                                        director = "Inconnu",
                                        actors = "Inconnu",
                                        releaseDate = "Inconnu",
                                        genre = "Inconnu",
                                        plot = position.plot ?: "Aucun résumé disponible.",
                                        rating = "0",
                                        coverBig = position.coverUrl,
                                        containerExtension = position.containerExtension ?: "mp4",
                                        resumePositionMs = position.positionMs,
                                        durationMs = position.durationMs
                                    )
                                    resumePositionMs = position.positionMs
                                    navigateTo(AppScreen.VOD_PLAYER)
                                },
                                onPlayResumeWatchingSeries = { position ->
                                    val sName = position.title?.substringBefore(" - ") ?: "Série"
                                    val epTitle = position.title?.substringAfter(" - ")?.substringAfter(" ") ?: "Épisode"
                                    activeEpisode = SeriesEpisode(
                                        id = position.streamId,
                                        episodeNum = position.episodeNum ?: 1,
                                        title = epTitle,
                                        containerExtension = position.containerExtension ?: "mp4",
                                        plot = position.plot ?: "Aucun résumé disponible.",
                                        duration = position.duration ?: "00:00",
                                        releaseDate = position.releaseDate ?: "",
                                        resumePositionMs = position.positionMs,
                                        durationMs = position.durationMs,
                                        seasonNum = position.seasonNum ?: 1
                                    )
                                    activeSeriesDetails = SeriesDetails(
                                        seriesId = 0,
                                        name = sName,
                                        cover = position.coverUrl,
                                        rating = "0",
                                        seasons = emptyList(),
                                        episodes = emptyMap()
                                    )
                                    navigateTo(AppScreen.SERIES_PLAYER)
                                },
                                onPlayLiveStream = { stream, list ->
                                    activeStream = stream
                                    activeStreamsList = list
                                    navigateTo(AppScreen.PLAYER)
                                },
                                onSelectMovieDetail = { stream ->
                                    activeVodMovie = stream
                                    navigateTo(AppScreen.VOD_DETAILS)
                                },
                                onSelectSeriesDetail = { stream ->
                                    activeSeriesShow = stream
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                }
                            )
                        }
                        AppScreen.LIVETV -> {
                            val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                            LiveTvScreen(
                                viewModel = liveTvViewModel,
                                favoritesList = favsState.favorites,
                                onToggleFavorite = { stream ->
                                    favoritesViewModel.toggleFavorite(
                                        id = stream.streamId,
                                        type = "live",
                                        name = stream.name,
                                        cover = stream.streamIcon,
                                        categoryId = stream.categoryId
                                    )
                                },
                                isTv = isTv,
                                onStreamSelected = { stream, list ->
                                    activeStream = stream
                                    activeStreamsList = list
                                    navigateTo(AppScreen.PLAYER)
                                }
                            )
                        }
                        AppScreen.PLAYER -> {
                            val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                            val creds = liveTvViewModel.getCredentials()
                            if (creds != null && activeStream != null) {
                                PlayerScreen(
                                    initialStream = activeStream!!,
                                    streamsList = activeStreamsList,
                                    credentials = creds,
                                    isTv = isTv,
                                    viewModel = liveTvViewModel,
                                    onClose = {
                                        navigateBack()
                                    },
                                    onStreamChanged = { stream ->
                                        liveTvViewModel.saveRecentlyWatched(stream)
                                    }
                                )
                            } else {
                                navigateBack()
                            }
                        }
                        AppScreen.VOD_GRID -> {
                            val vodViewModel: VodViewModel = hiltViewModel()
                            VodScreen(
                                viewModel = vodViewModel,
                                isTv = isTv,
                                favoritesList = favsState.favorites,
                                onMovieSelected = { stream ->
                                    activeVodMovie = stream
                                    navigateTo(AppScreen.VOD_DETAILS)
                                }
                            )
                        }
                        AppScreen.VOD_DETAILS -> {
                            val vodViewModel: VodViewModel = hiltViewModel()
                            val state by vodViewModel.state.collectAsStateWithLifecycle()
                            
                            LaunchedEffect(activeVodMovie) {
                                activeVodMovie?.let { 
                                    vodViewModel.selectStream(it)
                                }
                            }

                            if (state.isLoadingDetails) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                state.selectedVodDetails?.let { details ->
                                    activeVodDetails = details
                                    val isFav = favsState.favorites.any { it.id == details.streamId && it.type == "movie" }
                                    
                                    VodDetailsScreen(
                                        details = details,
                                        isTv = isTv,
                                        isFavorite = isFav,
                                        onToggleFavorite = {
                                            favoritesViewModel.toggleFavorite(
                                                id = details.streamId,
                                                type = "movie",
                                                name = details.name,
                                                cover = details.coverBig,
                                                categoryId = "0"
                                            )
                                        },
                                        onPlayFromBeginning = {
                                            vodViewModel.clearPosition(details.streamId)
                                            resumePositionMs = 0L
                                            navigateTo(AppScreen.VOD_PLAYER)
                                        },
                                        onResumePlayback = { pos ->
                                            resumePositionMs = pos
                                            navigateTo(AppScreen.VOD_PLAYER)
                                        },
                                        onBack = {
                                            navigateBack()
                                        },
                                        onSearchQueryTriggered = { query ->
                                            favoritesViewModel.onSearchQueryChanged(query)
                                            navigateTo(AppScreen.SEARCH)
                                        },
                                        relatedStreams = state.relatedStreams,
                                        onSelectRelated = { stream ->
                                            activeVodMovie = stream
                                            navigateTo(AppScreen.VOD_DETAILS)
                                        },
                                        downloadItem = downloadsState.downloads.firstOrNull {
                                            it.contentId == com.cstv.app.domain.model.DownloadedItem.movieContentId(details.streamId)
                                        },
                                        onDownload = { downloadsViewModel.downloadMovie(details) },
                                        onRemoveDownload = {
                                            downloadsViewModel.remove(com.cstv.app.domain.model.DownloadedItem.movieContentId(details.streamId))
                                        }
                                    )
                                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        AppScreen.VOD_PLAYER -> {
                            val vodViewModel: VodViewModel = hiltViewModel()
                            val creds = vodViewModel.getCredentials()
                            if (creds != null && activeVodDetails != null) {
                                VodPlayerScreen(
                                    details = activeVodDetails!!,
                                    initialPositionMs = resumePositionMs,
                                    credentials = creds,
                                    isTv = isTv,
                                    viewModel = vodViewModel,
                                    onClose = {
                                        navigateBack()
                                    }
                                )
                            } else {
                                navigateBack()
                            }
                        }
                        AppScreen.SERIES_GRID -> {
                            val seriesViewModel: SeriesViewModel = hiltViewModel()
                            SeriesScreen(
                                viewModel = seriesViewModel,
                                isTv = isTv,
                                favoritesList = favsState.favorites,
                                onSeriesSelected = { stream ->
                                    activeSeriesShow = stream
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                }
                            )
                        }
                        AppScreen.SERIES_DETAILS -> {
                            val seriesViewModel: SeriesViewModel = hiltViewModel()
                            val state by seriesViewModel.state.collectAsStateWithLifecycle()

                            LaunchedEffect(activeSeriesShow) {
                                activeSeriesShow?.let { 
                                    seriesViewModel.selectStream(it)
                                }
                            }

                            if (state.isLoadingDetails) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                state.selectedSeriesDetails?.let { details ->
                                    activeSeriesDetails = details
                                    val isFav = favsState.favorites.any { it.id == details.seriesId && it.type == "series" }
                                    
                                    SeriesDetailsScreen(
                                        details = details,
                                        isTv = isTv,
                                        isFavorite = isFav,
                                        onToggleFavorite = {
                                            favoritesViewModel.toggleFavorite(
                                                id = details.seriesId,
                                                type = "series",
                                                name = details.name,
                                                cover = details.cover,
                                                categoryId = "0"
                                            )
                                        },
                                        onEpisodeSelected = { episode ->
                                            activeEpisode = episode
                                            navigateTo(AppScreen.SERIES_PLAYER)
                                        },
                                        onBack = {
                                            navigateBack()
                                        },
                                        onSearchQueryTriggered = { query ->
                                            favoritesViewModel.onSearchQueryChanged(query)
                                            navigateTo(AppScreen.SEARCH)
                                        },
                                        relatedSeries = state.relatedSeries,
                                        onSelectRelated = { stream ->
                                            activeSeriesShow = stream
                                            navigateTo(AppScreen.SERIES_DETAILS)
                                        },
                                        episodeDownloads = downloadsState.downloads
                                            .filter { it.type == com.cstv.app.domain.model.DownloadedItem.TYPE_EPISODE }
                                            .associateBy { it.streamId },
                                        onDownloadEpisode = { episode ->
                                            downloadsViewModel.downloadEpisode(episode, details.seriesId, details.name, details.cover)
                                        },
                                        onRemoveEpisodeDownload = { episodeId ->
                                            downloadsViewModel.remove(com.cstv.app.domain.model.DownloadedItem.episodeContentId(episodeId))
                                        }
                                    )
                                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        AppScreen.SERIES_PLAYER -> {
                            val seriesViewModel: SeriesViewModel = hiltViewModel()
                            val creds = seriesViewModel.getCredentials()
                            if (creds != null && activeEpisode != null) {
                                SeriesPlayerScreen(
                                    episode = activeEpisode!!,
                                    seriesId = activeSeriesDetails?.seriesId ?: 0,
                                    seriesName = activeSeriesDetails?.name ?: "Série",
                                    seriesCover = activeSeriesDetails?.cover,
                                    seriesEpisodes = activeSeriesDetails?.episodes ?: emptyMap(),
                                    credentials = creds,
                                    isTv = isTv,
                                    viewModel = seriesViewModel,
                                    onClose = {
                                        navigateBack()
                                    }
                                )
                            } else {
                                navigateBack()
                            }
                        }
                        AppScreen.FAVORITES -> {
                            FavoritesScreen(
                                viewModel = favoritesViewModel,
                                isTv = isTv,
                                onPlayLive = { id, catId ->
                                    activeStream = LiveStream(id, "Chaîne Favorie", null, null, 1, catId)
                                    activeStreamsList = listOf(activeStream!!)
                                    navigateTo(AppScreen.PLAYER)
                                },
                                onSelectMovie = { id, catId ->
                                    activeVodMovie = VodStream(id, "Film Favori", null, null, null, catId)
                                    navigateTo(AppScreen.VOD_DETAILS)
                                },
                                onSelectSeries = { id, catId ->
                                    activeSeriesShow = SeriesStream(id, "Série Favorie", null, null, null, catId)
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                },
                                onBack = {
                                    navigateBack()
                                }
                            )
                        }
                        AppScreen.SEARCH -> {
                            SearchScreen(
                                viewModel = favoritesViewModel,
                                isTv = isTv,
                                onPlayLive = { stream ->
                                    activeStream = stream
                                    activeStreamsList = listOf(stream)
                                    navigateTo(AppScreen.PLAYER)
                                },
                                onSelectMovie = { stream ->
                                    activeVodMovie = stream
                                    navigateTo(AppScreen.VOD_DETAILS)
                                },
                                onSelectSeries = { stream ->
                                    activeSeriesShow = stream
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                },
                                onBack = {
                                    navigateBack()
                                }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                isTv = isTv,
                                onBack = {
                                    navigateBack()
                                },
                                onLogout = {
                                    loginViewModel.logout()
                                    loggedInUser = null
                                    screenHistory.clear()
                                    currentScreen = AppScreen.LOGIN
                                },
                                onManageCategories = {
                                    navigateTo(AppScreen.CATEGORY_MANAGEMENT)
                                },
                                onManageDownloads = {
                                    navigateTo(AppScreen.DOWNLOADS)
                                }
                            )
                        }
                        AppScreen.CATEGORY_MANAGEMENT -> {
                            val categoryManagementViewModel: com.cstv.app.presentation.settings.CategoryManagementViewModel = hiltViewModel()
                            com.cstv.app.presentation.settings.CategoryManagementScreen(
                                viewModel = categoryManagementViewModel,
                                isTv = isTv,
                                onBack = {
                                    navigateBack()
                                }
                            )
                        }
                        AppScreen.PROFILE_SELECTION -> {
                            com.cstv.app.presentation.profile.ProfileSelectionScreen(
                                profiles = profileState.profiles,
                                onProfileSelected = { profile ->
                                    profileViewModel.selectProfile(profile.id)
                                    navigateBack()
                                },
                                onManageProfiles = {
                                    navigateTo(AppScreen.PROFILE_MANAGEMENT)
                                },
                                onLogout = {
                                    loginViewModel.logout()
                                    loggedInUser = null
                                    screenHistory.clear()
                                    currentScreen = AppScreen.LOGIN
                                }
                            )
                        }
                        AppScreen.PROFILE_MANAGEMENT -> {
                            com.cstv.app.presentation.profile.ProfileManagementScreen(
                                viewModel = profileViewModel,
                                isTv = isTv,
                                onBack = {
                                    navigateBack()
                                }
                            )
                        }
                        AppScreen.RECENTLY_ADDED -> {
                            val recentlyAddedViewModel: RecentlyAddedViewModel = hiltViewModel()
                            RecentlyAddedScreen(
                                viewModel = recentlyAddedViewModel,
                                isTv = isTv,
                                isSeries = recentlyAddedIsSeries,
                                onBack = {
                                    navigateBack()
                                },
                                onSelectMovieDetail = { stream ->
                                    activeVodMovie = stream
                                    navigateTo(AppScreen.VOD_DETAILS)
                                },
                                onSelectSeriesDetail = { stream ->
                                    activeSeriesShow = stream
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                }
                            )
                        }
                        AppScreen.DOWNLOADS -> {
                            com.cstv.app.presentation.downloads.DownloadsScreen(
                                viewModel = downloadsViewModel,
                                isTv = isTv,
                                onPlayMovie = { item ->
                                    activeVodDetails = buildOfflineVodDetails(item)
                                    resumePositionMs = 0L
                                    navigateTo(AppScreen.VOD_PLAYER)
                                },
                                onPlayEpisode = { item ->
                                    val episode = buildOfflineEpisode(item)
                                    activeSeriesDetails = buildOfflineSeriesDetails(item, episode)
                                    activeEpisode = episode
                                    navigateTo(AppScreen.SERIES_PLAYER)
                                },
                                onBack = { navigateBack() }
                            )
                        }
                    }
                } else {
                    // Mobile Layout with Jetpack Compose Navigation
                    IptvXtreamTheme {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        // Bottom navigation bar is visible only when user is logged in
                        // AND we are not on the login screen or in a full screen player
                        val showBottomBar = loggedInUser != null && currentRoute !in listOf("login", "live_player", "vod_player", "series_player")

                        Box(modifier = Modifier.fillMaxSize().mobileBackground()) {
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (showBottomBar) {
                                        androidx.compose.foundation.layout.Column {
                                            androidx.compose.material3.HorizontalDivider(
                                                color = Color(0x10FFFFFF),
                                                thickness = 1.dp
                                            )
                                            NavigationBar(containerColor = Color(0xE60C0C10)) {
                                                val tabs = listOf(MobileTab.HOME, MobileTab.TV, MobileTab.MOVIES, MobileTab.SERIES, MobileTab.SEARCH)
                                                tabs.forEach { tab ->
                                                    val selected = currentRoute == tab.route ||
                                                            (tab.route == "movies" && currentRoute == "vod_details") ||
                                                            (tab.route == "series" && currentRoute == "series_details")
                                                    NavigationBarItem(
                                                        selected = selected,
                                                        onClick = {
                                                            if (tab == MobileTab.HOME) {
                                                                navController.navigate(tab.route) {
                                                                    popUpTo("home") {
                                                                        inclusive = false
                                                                    }
                                                                    launchSingleTop = true
                                                                }
                                                            } else {
                                                                navController.navigate(tab.route) {
                                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                                        saveState = true
                                                                    }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            }
                                                        },
                                                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                                                        label = { Text(tab.title, fontSize = 10.sp, fontFamily = com.cstv.app.presentation.theme.AppTypography.labelSmall.fontFamily) },
                                                        colors = NavigationBarItemDefaults.colors(
                                                            selectedIconColor = com.cstv.app.presentation.theme.AccentLavande,
                                                            selectedTextColor = com.cstv.app.presentation.theme.AccentLavande,
                                                            unselectedIconColor = com.cstv.app.presentation.theme.TextSecondary,
                                                            unselectedTextColor = com.cstv.app.presentation.theme.TextSecondary,
                                                            indicatorColor = com.cstv.app.presentation.theme.AccentLavande.copy(alpha = 0.16f)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            ) { paddingValues ->
                                AppNavGraph(
                                    navController = navController,
                                    paddingValues = paddingValues,
                                    loggedInUser = loggedInUser,
                                    onUserChanged = { loggedInUser = it },
                                    loginViewModel = loginViewModel,
                                    favoritesViewModel = favoritesViewModel,
                                    homeViewModel = homeViewModel,
                                    profileViewModel = profileViewModel,
                                    profileState = profileState,
                                    favsState = favsState,
                                    homeLazyListState = homeLazyListState,
                                    activeStream = activeStream,
                                    onActiveStreamChanged = { activeStream = it },
                                    activeStreamsList = activeStreamsList,
                                    onActiveStreamsListChanged = { activeStreamsList = it },
                                    activeVodMovie = activeVodMovie,
                                    onActiveVodMovieChanged = { activeVodMovie = it },
                                    activeVodDetails = activeVodDetails,
                                    onActiveVodDetailsChanged = { activeVodDetails = it },
                                    resumePositionMs = resumePositionMs,
                                    onResumePositionMsChanged = { resumePositionMs = it },
                                    activeSeriesShow = activeSeriesShow,
                                    onActiveSeriesShowChanged = { activeSeriesShow = it },
                                    activeSeriesDetails = activeSeriesDetails,
                                    onActiveSeriesDetailsChanged = { activeSeriesDetails = it },
                                    activeEpisode = activeEpisode,
                                    onActiveEpisodeChanged = { activeEpisode = it }
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    private fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

// --- Lecture hors-ligne (feature #15) : reconstruit des modèles de lecture
// minimaux depuis un DownloadedItem, sans fetch réseau des détails (l'app peut
// être hors-ligne). L'URL est reconstruite par le player depuis les identifiants
// stockés ; le cache de téléchargement sert le fichier local. ---
internal fun buildOfflineVodDetails(item: com.cstv.app.domain.model.DownloadedItem): VodDetails =
    VodDetails(
        streamId = item.streamId,
        name = item.title,
        director = "Inconnu",
        actors = "Inconnu",
        releaseDate = "Inconnu",
        genre = "",
        plot = "",
        rating = "0.0",
        coverBig = item.coverUrl,
        containerExtension = item.containerExtension
    )

internal fun buildOfflineEpisode(item: com.cstv.app.domain.model.DownloadedItem): SeriesEpisode =
    SeriesEpisode(
        id = item.streamId,
        episodeNum = item.episodeNum ?: 1,
        title = item.subtitle ?: item.title,
        containerExtension = item.containerExtension,
        plot = "",
        duration = "",
        releaseDate = "",
        movieImage = item.coverUrl,
        seasonNum = item.seasonNum ?: 1
    )

internal fun buildOfflineSeriesDetails(
    item: com.cstv.app.domain.model.DownloadedItem,
    episode: SeriesEpisode
): SeriesDetails =
    SeriesDetails(
        seriesId = item.seriesId ?: 0,
        name = item.title,
        cover = item.coverUrl,
        rating = null,
        seasons = emptyList(),
        episodes = mapOf((item.seasonNum ?: 1) to listOf(episode))
    )
