package com.poc.iptvxtream

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
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.UserInfo
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesEpisode
import com.poc.iptvxtream.presentation.home.HomeScreen
import com.poc.iptvxtream.presentation.home.HomeViewModel
import com.poc.iptvxtream.presentation.livetv.LiveTvScreen
import com.poc.iptvxtream.presentation.livetv.LiveTvViewModel
import com.poc.iptvxtream.presentation.login.LoginScreen
import com.poc.iptvxtream.presentation.login.LoginViewModel
import com.poc.iptvxtream.presentation.login.AutoLoginState
import com.poc.iptvxtream.presentation.login.SplashScreen
import com.poc.iptvxtream.presentation.player.PlayerScreen
import com.poc.iptvxtream.presentation.vod.VodDetailsScreen
import com.poc.iptvxtream.presentation.vod.VodPlayerScreen
import com.poc.iptvxtream.presentation.vod.VodScreen
import com.poc.iptvxtream.presentation.vod.VodViewModel
import com.poc.iptvxtream.presentation.series.SeriesScreen
import com.poc.iptvxtream.presentation.series.SeriesDetailsScreen
import com.poc.iptvxtream.presentation.series.SeriesPlayerScreen
import com.poc.iptvxtream.presentation.series.SeriesViewModel
import com.poc.iptvxtream.presentation.favorites.FavoritesScreen
import com.poc.iptvxtream.presentation.favorites.FavoritesViewModel
import com.poc.iptvxtream.presentation.search.SearchScreen
import com.poc.iptvxtream.presentation.settings.SettingsScreen
import com.poc.iptvxtream.presentation.profile.ProfileViewModel
import com.poc.iptvxtream.presentation.profile.ProfileSelectionScreen
import com.poc.iptvxtream.presentation.settings.SettingsViewModel
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
    PROFILE_MANAGEMENT
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

                val autoLoginState by loginViewModel.autoLoginState.collectAsState()
                
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

                // Get global reactive favorites list
                val favsState by favoritesViewModel.state.collectAsState()

                // --- Sélection de profil (Phase 27) ---
                // Gate unique couvrant TV et mobile : après login/auto-login, on
                // garantit un profil actif et on affiche l'écran de sélection
                // uniquement s'il existe plusieurs profils.
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val profileState by profileViewModel.state.collectAsState()
                var profileSelectionNeeded by remember { mutableStateOf(false) }
                var profileGateResolved by remember { mutableStateOf(false) }

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
                } else if (showProfileSelection) {
                    ProfileSelectionScreen(
                        profiles = profileState.profiles,
                        onProfileSelected = { profile ->
                            profileViewModel.selectProfile(profile.id)
                            profileGateResolved = true
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
                                    navigateTo(AppScreen.PROFILE_MANAGEMENT)
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
                                onMovieSelected = { stream ->
                                    activeVodMovie = stream
                                    navigateTo(AppScreen.VOD_DETAILS)
                                }
                            )
                        }
                        AppScreen.VOD_DETAILS -> {
                            val vodViewModel: VodViewModel = hiltViewModel()
                            val state by vodViewModel.state.collectAsState()
                            
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
                                onSeriesSelected = { stream ->
                                    activeSeriesShow = stream
                                    navigateTo(AppScreen.SERIES_DETAILS)
                                }
                            )
                        }
                        AppScreen.SERIES_DETAILS -> {
                            val seriesViewModel: SeriesViewModel = hiltViewModel()
                            val state by seriesViewModel.state.collectAsState()

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
                                    seriesName = activeSeriesDetails?.name ?: "Série",
                                    seriesCover = activeSeriesDetails?.cover,
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
                            val settingsViewModel: SettingsViewModel = hiltViewModel()
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
                                }
                            )
                        }
                        AppScreen.PROFILE_MANAGEMENT -> {
                            com.poc.iptvxtream.presentation.profile.ProfileManagementScreen(
                                viewModel = profileViewModel,
                                isTv = isTv,
                                onBack = {
                                    navigateBack()
                                }
                            )
                        }
                    }
                } else {
                    // Mobile Layout with Jetpack Compose Navigation
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Bottom navigation bar is visible only when user is logged in
                    // AND we are not on the login screen or in a full screen player
                    val showBottomBar = loggedInUser != null && currentRoute !in listOf("login", "live_player", "vod_player", "series_player")

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar(containerColor = Color(0xFF16161D)) {
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
                                            label = { Text(tab.title, fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = Color.Gray,
                                                unselectedTextColor = Color.Gray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = if (loggedInUser == null) "login" else "home",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable("login") {
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    isTv = false,
                                    onLoginSuccess = { userInfo ->
                                        loggedInUser = userInfo
                                        navController.navigate("home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("home") {
                                val activeProfile = profileState.profiles.find { it.id == profileState.activeProfileId }
                                HomeScreen(
                                    userInfo = loggedInUser ?: UserInfo("User", true, "Active", "Inconnue", 1, 0, "Connecté"),
                                    isTv = false,
                                    viewModel = homeViewModel,
                                    activeProfileAvatarId = activeProfile?.avatarId ?: 0,
                                    activeProfileName = activeProfile?.name ?: loggedInUser?.username ?: "",
                                    lazyListState = homeLazyListState,
                                    onNavigateToLiveTv = {
                                        navController.navigate("tv") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToVod = {
                                        navController.navigate("movies") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToSeries = {
                                        navController.navigate("series") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToFavorites = {
                                        navController.navigate("favorites")
                                    },
                                    onNavigateToSearch = {
                                        navController.navigate("search") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onNavigateToProfileManagement = {
                                        navController.navigate("profile_management")
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
                                        navController.navigate("vod_player")
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
                                        navController.navigate("series_player")
                                    },
                                    onPlayLiveStream = { stream, list ->
                                        activeStream = stream
                                        activeStreamsList = list
                                        navController.navigate("live_player")
                                    },
                                    onSelectMovieDetail = { stream ->
                                        activeVodMovie = stream
                                        navController.navigate("vod_details")
                                    },
                                    onSelectSeriesDetail = { stream ->
                                        activeSeriesShow = stream
                                        navController.navigate("series_details")
                                    }
                                )
                            }
                            composable("tv") {
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
                                    isTv = false,
                                    onStreamSelected = { stream, list ->
                                        activeStream = stream
                                        activeStreamsList = list
                                        navController.navigate("live_player")
                                    }
                                )
                            }
                            composable("movies") {
                                val vodViewModel: VodViewModel = hiltViewModel()
                                VodScreen(
                                    viewModel = vodViewModel,
                                    isTv = false,
                                    onMovieSelected = { stream ->
                                        activeVodMovie = stream
                                        navController.navigate("vod_details")
                                    }
                                )
                            }
                            composable("series") {
                                val seriesViewModel: SeriesViewModel = hiltViewModel()
                                SeriesScreen(
                                    viewModel = seriesViewModel,
                                    isTv = false,
                                    onSeriesSelected = { stream ->
                                        activeSeriesShow = stream
                                        navController.navigate("series_details")
                                    }
                                )
                            }
                            composable("search") {
                                SearchScreen(
                                    viewModel = favoritesViewModel,
                                    isTv = false,
                                    onPlayLive = { stream ->
                                        activeStream = stream
                                        activeStreamsList = listOf(stream)
                                        navController.navigate("live_player")
                                    },
                                    onSelectMovie = { stream ->
                                        activeVodMovie = stream
                                        navController.navigate("vod_details")
                                    },
                                    onSelectSeries = { stream ->
                                        activeSeriesShow = stream
                                        navController.navigate("series_details")
                                    },
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("favorites") {
                                FavoritesScreen(
                                    viewModel = favoritesViewModel,
                                    isTv = false,
                                    onPlayLive = { id, catId ->
                                        activeStream = LiveStream(id, "Chaîne Favorie", null, null, 1, catId)
                                        activeStreamsList = listOf(activeStream!!)
                                        navController.navigate("live_player")
                                    },
                                    onSelectMovie = { id, catId ->
                                        activeVodMovie = VodStream(id, "Film Favori", null, null, null, catId)
                                        navController.navigate("vod_details")
                                    },
                                    onSelectSeries = { id, catId ->
                                        activeSeriesShow = SeriesStream(id, "Série Favorie", null, null, null, catId)
                                        navController.navigate("series_details")
                                    },
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("settings") {
                                val settingsViewModel: SettingsViewModel = hiltViewModel()
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    isTv = false,
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    onLogout = {
                                        loginViewModel.logout()
                                        loggedInUser = null
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("profile_management") {
                                com.poc.iptvxtream.presentation.profile.ProfileManagementScreen(
                                    viewModel = profileViewModel,
                                    isTv = false,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("vod_details") {
                                val vodViewModel: VodViewModel = hiltViewModel()
                                val state by vodViewModel.state.collectAsState()
                                
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
                                            isTv = false,
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
                                                navController.navigate("vod_player")
                                            },
                                            onResumePlayback = { pos ->
                                                resumePositionMs = pos
                                                navController.navigate("vod_player")
                                            },
                                            onBack = {
                                                navController.popBackStack()
                                            },
                                            onSearchQueryTriggered = { query ->
                                                favoritesViewModel.onSearchQueryChanged(query)
                                                navController.navigate("search")
                                            }
                                        )
                                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            composable("series_details") {
                                val seriesViewModel: SeriesViewModel = hiltViewModel()
                                val state by seriesViewModel.state.collectAsState()

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
                                            isTv = false,
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
                                                navController.navigate("series_player")
                                            },
                                            onBack = {
                                                navController.popBackStack()
                                            },
                                            onSearchQueryTriggered = { query ->
                                                favoritesViewModel.onSearchQueryChanged(query)
                                                navController.navigate("search")
                                            }
                                        )
                                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            composable("live_player") {
                                val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                                val creds = liveTvViewModel.getCredentials()
                                if (creds != null && activeStream != null) {
                                    PlayerScreen(
                                        initialStream = activeStream!!,
                                        streamsList = activeStreamsList,
                                        credentials = creds,
                                        isTv = false,
                                        onClose = {
                                            navController.popBackStack()
                                        },
                                        onStreamChanged = { stream ->
                                            liveTvViewModel.saveRecentlyWatched(stream)
                                        }
                                    )
                                } else {
                                    navController.popBackStack()
                                }
                            }
                            composable("vod_player") {
                                val vodViewModel: VodViewModel = hiltViewModel()
                                val creds = vodViewModel.getCredentials()
                                if (creds != null && activeVodDetails != null) {
                                    VodPlayerScreen(
                                        details = activeVodDetails!!,
                                        initialPositionMs = resumePositionMs,
                                        credentials = creds,
                                        isTv = false,
                                        viewModel = vodViewModel,
                                        onClose = {
                                            navController.popBackStack()
                                        }
                                    )
                                } else {
                                    navController.popBackStack()
                                }
                            }
                            composable("series_player") {
                                val seriesViewModel: SeriesViewModel = hiltViewModel()
                                val creds = seriesViewModel.getCredentials()
                                if (creds != null && activeEpisode != null) {
                                    SeriesPlayerScreen(
                                        episode = activeEpisode!!,
                                        seriesName = activeSeriesDetails?.name ?: "Série",
                                        seriesCover = activeSeriesDetails?.cover,
                                        credentials = creds,
                                        isTv = false,
                                        viewModel = seriesViewModel,
                                        onClose = {
                                            navController.popBackStack()
                                        }
                                    )
                                } else {
                                    navController.popBackStack()
                                }
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
