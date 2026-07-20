package com.cstv.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.presentation.home.HomeViewModel
import com.cstv.app.presentation.login.LoginViewModel
import com.cstv.app.presentation.login.AutoLoginState
import com.cstv.app.presentation.login.SplashScreen
import com.cstv.app.presentation.favorites.FavoritesViewModel
import com.cstv.app.presentation.settings.SettingsViewModel
import com.cstv.app.presentation.profile.ProfileViewModel
import com.cstv.app.presentation.profile.ProfileSelectionScreen
import com.cstv.app.presentation.navigation.AppNavGraph
import com.cstv.app.presentation.theme.IptvXtreamTheme
import com.cstv.app.presentation.theme.mobileBackground
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

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

                val autoLoginState by loginViewModel.autoLoginState.collectAsStateWithLifecycle()
                var loggedInUser by remember { mutableStateOf<UserInfo?>(null) }

                LaunchedEffect(autoLoginState) {
                    when (autoLoginState) {
                        is AutoLoginState.Success -> {
                            loggedInUser = (autoLoginState as AutoLoginState.Success).userInfo
                        }
                        is AutoLoginState.Error -> {
                            loginViewModel.setError((autoLoginState as AutoLoginState.Error).message)
                        }
                        else -> {}
                    }
                }

                val homeLazyListState = rememberLazyListState()
                
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
                val favsState by favoritesViewModel.state.collectAsStateWithLifecycle()

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
                        }
                    )
                } else {
                    // Unified Jetpack Compose Navigation Layout for BOTH TV and Mobile
                    IptvXtreamTheme {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        // Bottom navigation bar is visible only on mobile, when user is logged in
                        // AND we are not on the login screen or in a full screen player
                        val showBottomBar = !isTv && loggedInUser != null && currentRoute !in listOf("login", "live_player", "vod_player", "series_player")

                        // Android TV Safe Back Button Handler for Dashboard
                        BackHandler(enabled = isTv && currentRoute == "home") {
                            loginViewModel.logout()
                            loggedInUser = null
                        }

                        val backgroundModifier = if (isTv) {
                            Modifier.background(com.cstv.app.presentation.theme.Surface1)
                        } else {
                            Modifier.mobileBackground()
                        }

                        Box(modifier = Modifier.fillMaxSize().then(backgroundModifier)) {
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
                                    isTv = isTv,
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
