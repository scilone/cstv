package com.cstv.app

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
import com.cstv.app.presentation.cstv.CstvAuthViewModel
import com.cstv.app.presentation.cstv.CstvGateScreen
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.presentation.favorites.FavoritesViewModel
import com.cstv.app.presentation.settings.SettingsViewModel
import com.cstv.app.presentation.profile.ProfileViewModel
import com.cstv.app.presentation.profile.ProfileSelectionScreen
import com.cstv.app.presentation.bootstrap.CatalogBootstrapContent
import com.cstv.app.presentation.bootstrap.CatalogBootstrapViewModel
import com.cstv.app.presentation.bootstrap.shouldShowCatalogBootstrap
import com.cstv.app.presentation.navigation.AppNavGraph
import com.cstv.app.presentation.navigation.MobileNavigation
import com.cstv.app.presentation.navigation.SeriesDeepLink
import com.cstv.app.presentation.navigation.SeriesDeepLinkViewModel
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.presentation.navigation.navigateToRootTab
import com.cstv.app.presentation.navigation.TvNavigation
import com.cstv.app.presentation.components.TvNavigationRail
import com.cstv.app.presentation.components.TV_RAIL_COLLAPSED_WIDTH_DP
import com.cstv.app.presentation.theme.IptvXtreamTheme
import com.cstv.app.presentation.theme.mobileBackground
import com.cstv.app.presentation.theme.SystemBarsController
import com.cstv.app.presentation.theme.isImmersivePlayerRoute
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.repository.ProfileRepository

enum class MobileTab(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "Accueil", Icons.Default.Home),
    TV("tv", "TV", Icons.Default.LiveTv),
    MOVIES("movies", "Films", Icons.Default.Movie),
    SERIES("series", "Séries", Icons.Default.Tv),
    SEARCH("search", "Recherche", Icons.Default.Search)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var settingsManager: SettingsManager

    @javax.inject.Inject lateinit var cloudSyncManager: CloudSyncManager
    @javax.inject.Inject lateinit var profileRepository: ProfileRepository

    // Le deep link de notification (F12) arrive via onNewIntent, un callback
    // Activity classique hors composition : ce state hissé au niveau de la
    // classe est le seul moyen de le faire traverser jusqu'à Compose.
    private val pendingDeepLinkIntent = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val context = LocalContext.current
            val isTv = remember { isTvDevice(context) }

            // Le thème doit envelopper toute l'application dès la première composition :
            // sans lui, la Surface racine et les écrans de gate (Splash, sélection de
            // profil) résolvent leurs couleurs sur le lightColorScheme() par défaut de
            // Compose, provoquant des bandes système blanches (B19).
            IptvXtreamTheme {
            // Covers splash and profile-gate states, which are rendered outside the NavHost.
            SystemBarsController(isTv = isTv, isPlayerRoute = false)
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val loginViewModel: LoginViewModel = hiltViewModel()
                val cstvAuthViewModel: CstvAuthViewModel = hiltViewModel()
                val favoritesViewModel: FavoritesViewModel = hiltViewModel()
                val homeViewModel: HomeViewModel = hiltViewModel()
                // F35 : instance unique pour toute la session — le contrôle
                // automatique (Accueil) et le contrôle manuel (Paramètres)
                // doivent converger vers la même machine d'états.
                val appUpdateViewModel: com.cstv.app.presentation.update.AppUpdateViewModel = hiltViewModel()

                val autoLoginState by loginViewModel.autoLoginState.collectAsStateWithLifecycle()
                val cstvSessionState by cstvAuthViewModel.sessionState.collectAsStateWithLifecycle()
                var loggedInUser by remember { mutableStateOf<UserInfo?>(null) }

                LaunchedEffect(cstvSessionState) {
                    if (cstvSessionState is CstvSessionState.Active || cstvSessionState is CstvSessionState.Offline) {
                        loginViewModel.startAutoLogin()
                    }
                    if (cstvSessionState is CstvSessionState.Active) {
                        profileRepository.getProfiles().forEach { cloudSyncManager.synchronizeProfile(it.id) }
                    }
                }

                DisposableEffect(cstvSessionState) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && cstvSessionState is CstvSessionState.Active) {
                            (this@MainActivity as? ComponentActivity)?.lifecycleScope?.launch {
                                profileRepository.getProfiles().forEach { cloudSyncManager.synchronizeProfile(it.id) }
                            }
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                // F35 : reprend automatiquement le téléchargement au retour de
                // l'écran système d'autorisation « sources inconnues » (§4.6).
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) appUpdateViewModel.onAppResumed()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

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
                // `ensureInitializedAndNeedsSelection()` est suspendu : sans cet
                // indicateur, la première composition qui suit le login passe
                // avec `profileSelectionNeeded == false` et compose l'accueil,
                // remplacé l'instant d'après par l'écran de sélection. On reste
                // donc sur le splash tant que la question n'est pas tranchée.
                var profileGateChecked by remember { mutableStateOf(false) }

                LaunchedEffect(loggedInUser) {
                    if (loggedInUser != null && !profileGateResolved) {
                        val needsSelection = profileViewModel.ensureInitializedAndNeedsSelection()
                        profileSelectionNeeded = needsSelection
                        if (!needsSelection) profileGateResolved = true
                        profileGateChecked = true
                    } else if (loggedInUser == null) {
                        profileGateResolved = false
                        profileSelectionNeeded = false
                        profileGateChecked = false
                    }
                }

                // --- F12 : deep link de notification "nouveaux épisodes" ---
                var pendingSeriesId by remember { mutableStateOf<Int?>(null) }
                var pendingDeepLinkProfileId by remember { mutableStateOf<Int?>(null) }

                LaunchedEffect(Unit) {
                    SeriesDeepLink.extract(intent)?.let {
                        pendingSeriesId = it.seriesId
                        pendingDeepLinkProfileId = it.profileId
                    }
                }
                LaunchedEffect(pendingDeepLinkIntent.value) {
                    val target = SeriesDeepLink.extract(pendingDeepLinkIntent.value) ?: return@LaunchedEffect
                    pendingSeriesId = target.seriesId
                    pendingDeepLinkProfileId = target.profileId
                    pendingDeepLinkIntent.value = null
                }

                // Une seule demande de POST_NOTIFICATIONS sur mobile, après le gate de
                // profil, jamais renouvelée après un refus (règle métier F12).
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* Résultat ignoré : abandon silencieux si refusé, jamais de nouvelle demande. */ }
                LaunchedEffect(isTv, loggedInUser, profileGateResolved) {
                    if (!isTv &&
                        loggedInUser != null &&
                        profileGateResolved &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !settingsManager.getNotificationPermissionRequested()
                    ) {
                        settingsManager.setNotificationPermissionRequested(true)
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Garde le splash tant que la vérification est en cours, ET tant que
                // l'auto-login a réussi mais que loggedInUser n'est pas encore propagé
                // par le LaunchedEffect. Sans ça, sur mobile le NavHost se compose avec
                // loggedInUser==null et latche startDestination sur "login" malgré le succès.
                val showSplash = autoLoginState is AutoLoginState.Checking ||
                    (autoLoginState is AutoLoginState.Success && loggedInUser == null) ||
                    (loggedInUser != null && !profileGateChecked && !profileGateResolved)

                val showProfileSelection = loggedInUser != null &&
                    profileSelectionNeeded && !profileGateResolved

                val cstvGateResolved = cstvSessionState is CstvSessionState.Active || cstvSessionState is CstvSessionState.Offline
                if (!cstvGateResolved) {
                    CstvGateScreen(cstvAuthViewModel, cstvSessionState, isTv)
                } else if (showSplash) {
                    SplashScreen(isTv = isTv)
                } else if (showProfileSelection && showManagementFromGate) {
                    com.cstv.app.presentation.profile.ProfileManagementScreen(
                        viewModel = profileViewModel,
                        isTv = isTv,
                        onBack = { showManagementFromGate = false }
                    )
                } else if (showProfileSelection) {
                    ProfileSelectionScreen(
                        profiles = profileState.profiles,
                        isTv = isTv,
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
                    // F38 : le catalogue incomplet est un gate, pas une route.
                    // Les deep links sont déjà mémorisés ci-dessus et ne seront
                    // consommés que quand cette branche pourra composer le NavGraph.
                    val catalogBootstrapViewModel: CatalogBootstrapViewModel = hiltViewModel()
                    val catalogBootstrapState by catalogBootstrapViewModel.state.collectAsStateWithLifecycle()
                    val showCatalogBootstrap = shouldShowCatalogBootstrap(
                        hasLoggedInUser = loggedInUser != null,
                        profileGateResolved = profileGateResolved,
                        state = catalogBootstrapState
                    )
                    LaunchedEffect(showCatalogBootstrap) {
                        if (showCatalogBootstrap) catalogBootstrapViewModel.startIfNeeded()
                    }
                    CatalogBootstrapContent(
                        showBootstrap = showCatalogBootstrap,
                        state = catalogBootstrapState,
                        isTv = isTv,
                        onRetry = catalogBootstrapViewModel::retry,
                        onLogout = {
                            loginViewModel.logout()
                            loggedInUser = null
                        }
                    ) {
                    // Unified Jetpack Compose Navigation Layout for BOTH TV and Mobile.
                    // Le thème est déjà posé à la racine de setContent (voir plus haut).
                    run {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val isPlayerRoute = isImmersivePlayerRoute(currentRoute)
                        val showTvRail = isTv && TvNavigation.isRailRoute(currentRoute)
                        var railExpanded by remember { mutableStateOf(false) }
                        val activity = LocalContext.current as? android.app.Activity
                        // Rend le focus au contenu quand on quitte la barre :
                        // sans cela le focus y reste et la barre se rouvre
                        // immédiatement après un clic sur une destination.
                        val contentFocusRequester = remember { FocusRequester() }
                        // Un descendant porte le focus — donc une vraie vignette,
                        // et non le conteneur lui-même. La distinction est
                        // essentielle : tant que l'écran de destination n'a pas
                        // composé ses éléments, le conteneur se saisit du focus
                        // faute de mieux. S'arrêter sur `hasFocus` laissait donc
                        // le focus sur un groupe vide, et le pad bas repartait
                        // dans la barre latérale.
                        var contentChildFocused by remember { mutableStateOf(false) }
                        // À l'arrivée sur un écran principal, le focus doit être
                        // dans le contenu : sans cela, rien n'est focalisé et la
                        // première pression du D-pad atterrit dans la barre.
                        LaunchedEffect(currentRoute, showTvRail) {
                            if (showTvRail) {
                                railExpanded = false
                                // L'écran de destination n'a pas encore de nœud
                                // focusable au moment de la navigation : une
                                // demande unique échoue en silence et le premier
                                // appui du pad repart alors dans la barre.
                                //
                                // La fenêtre doit couvrir le chargement des
                                // données, pas seulement la composition : tant
                                // que l'écran affiche son indicateur, il n'a
                                // rien de focusable et toutes les tentatives
                                // échouent. Elle s'interrompt dès que
                                // l'utilisateur déplie la barre, pour ne pas lui
                                // reprendre le focus des mains.
                                repeat(FOCUS_REQUEST_ATTEMPTS) {
                                    if (contentChildFocused || railExpanded) return@LaunchedEffect
                                    contentFocusRequester.requestFocusSafely()
                                    kotlinx.coroutines.delay(FOCUS_REQUEST_RETRY_MS)
                                }
                            }
                        }

                        // --- F12 : ouverture de la fiche série depuis la notification ---
                        val seriesDeepLinkViewModel: SeriesDeepLinkViewModel = hiltViewModel()
                        LaunchedEffect(pendingSeriesId, loggedInUser, profileGateResolved) {
                            val id = pendingSeriesId ?: return@LaunchedEffect
                            // Attend une session valide (règle métier F12) : couvre le
                            // splash, l'auto-login, la connexion manuelle et le gate profil.
                            if (loggedInUser == null || !profileGateResolved) return@LaunchedEffect
                            val targetProfileId = pendingDeepLinkProfileId
                            pendingSeriesId = null
                            pendingDeepLinkProfileId = null
                            if (targetProfileId != null && targetProfileId != profileState.activeProfileId) {
                                profileViewModel.selectProfile(targetProfileId)
                            }
                            val stream = seriesDeepLinkViewModel.resolveSeries(id)
                            if (stream != null) {
                                activeSeriesShow = stream
                                navController.navigate("series_details")
                            }
                        }
                        val activeProfile = profileState.profiles.firstOrNull { it.id == profileState.activeProfileId }
                        SystemBarsController(isTv = isTv, isPlayerRoute = isPlayerRoute)

                        // F35 : vérification automatique au démarrage à froid,
                        // uniquement à l'Accueil, jamais pendant une lecture
                        // (RG1). Les gates CSTV/Xtream/profil sont déjà résolus
                        // dans cette branche. `checkAutomatically()` s'auto-garde
                        // contre tout second appel de ce démarrage.
                        val appUpdateState by appUpdateViewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(currentRoute, isPlayerRoute) {
                            if (currentRoute == "home" && !isPlayerRoute) appUpdateViewModel.checkAutomatically()
                        }

                        // Bottom navigation bar is visible only on mobile, when user is logged in
                        // AND we are not on the login screen or in a full screen player
                        val showBottomBar = !isTv && loggedInUser != null && !isPlayerRoute && currentRoute != "login"

                        // Android TV Safe Back Button Handler for Dashboard
                        BackHandler(enabled = isTv && railExpanded) {
                            railExpanded = false
                            contentFocusRequester.requestFocusSafely()
                        }
                        // Retour depuis l'accueil = sortie de l'application. Sans
                        // ce traitement, la pile pouvait ramener sur l'écran de
                        // connexion, qui ne doit s'afficher qu'après une
                        // déconnexion explicite.
                        BackHandler(enabled = currentRoute == "home" && !railExpanded) {
                            activity?.finish()
                        }

                        val backgroundModifier = if (isTv) {
                            Modifier.background(com.cstv.app.presentation.theme.Surface1)
                        } else {
                            Modifier.mobileBackground()
                        }

                        Box(modifier = Modifier.fillMaxSize().then(backgroundModifier)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { contentChildFocused = it.hasFocus && !it.isFocused }
                                    .focusGroup()
                            ) {
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
                                                    val selected = MobileNavigation.isTabSelected(currentRoute, tab.route)
                                                    NavigationBarItem(
                                                        selected = selected,
                                                        onClick = {
                                                            navController.navigateToRootTab(tab.route)
                                                        },
                                                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                                                        label = {
                                                            Text(
                                                                tab.title,
                                                                fontSize = 10.sp,
                                                                fontFamily = com.cstv.app.presentation.theme.AppTypography.labelSmall.fontFamily,
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        },
                                                        // Pastille de sélection retirée : l'onglet courant se
                                                        // lit à son icône teintée et à la graisse du libellé,
                                                        // sans aplat de couleur sous l'icône.
                                                        colors = NavigationBarItemDefaults.colors(
                                                            selectedIconColor = com.cstv.app.presentation.theme.AccentLavande,
                                                            selectedTextColor = com.cstv.app.presentation.theme.AccentLavande,
                                                            unselectedIconColor = com.cstv.app.presentation.theme.TextSecondary,
                                                            unselectedTextColor = com.cstv.app.presentation.theme.TextSecondary,
                                                            indicatorColor = Color.Transparent
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            ) { paddingValues ->
                                val contentPadding = if (showTvRail) {
                                    PaddingValues(start = TV_RAIL_COLLAPSED_WIDTH_DP.dp)
                                } else {
                                    paddingValues
                                }
                                AppNavGraph(
                                    navController = navController,
                                    paddingValues = contentPadding,
                                    isPlayerRoute = isPlayerRoute,
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
                                    appUpdateViewModel = appUpdateViewModel,
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
                            if (showTvRail) {
                                TvNavigationRail(
                                    expanded = railExpanded,
                                    selected = TvNavigation.railDestinationFor(currentRoute),
                                    profileAvatarId = activeProfile?.avatarId ?: 0,
                                    profileName = activeProfile?.name.orEmpty(),
                                    username = loggedInUser?.username,
                                    expiryLabel = TvNavigation.expiryLabel(loggedInUser?.expiryDate),
                                    destinations = TvNavigation.destinations,
                                    onExpandedChange = { railExpanded = it },
                                    onDestinationClick = { destination ->
                                        railExpanded = false
                                        navController.navigateToRootTab(destination.route)
                                        contentFocusRequester.requestFocusSafely()
                                    },
                                    onCloseToContent = {
                                        railExpanded = false
                                        contentFocusRequester.requestFocusSafely()
                                    },
                                    onProfileClick = {
                                        railExpanded = false
                                        // Rouvre le sélecteur de profil : c'est
                                        // le seul chemin de changement de profil
                                        // depuis un écran principal TV.
                                        profileSelectionNeeded = true
                                        profileGateResolved = false
                                    }
                                )
                            }
                            // F35 : au-dessus du NavHost, jamais au-dessus des
                            // gates (déjà résolus dans cette branche) ni du lecteur.
                            if (!isPlayerRoute) {
                                com.cstv.app.presentation.update.AppUpdateDialog(
                                    state = appUpdateState,
                                    isTv = isTv,
                                    installedVersionName = com.cstv.app.BuildConfig.VERSION_NAME,
                                    onInstall = { appUpdateViewModel.install() },
                                    onLater = { appUpdateViewModel.postponeUpdate() },
                                    onIgnore = { appUpdateViewModel.ignoreUpdate() },
                                    onCancelDownload = { appUpdateViewModel.cancelDownload() },
                                    onOpenPermissionSettings = { appUpdateViewModel.openPermissionSettings() },
                                    onRetry = { appUpdateViewModel.retry() },
                                    onDismiss = { appUpdateViewModel.dismiss() },
                                    onQuit = { activity?.finish() }
                                )
                            }
                        }
                    }
                    }
                }
            }
            }
        }
    }

    /**
     * La recherche de focus bidirectionnelle du D-pad (Compose UI 1.6, BOM
     * 2024.02.02) lit les `LayoutCoordinates` de nœuds que les rangées viennent
     * de détacher — une section d'accueil qui apparaît quand l'appariement TMDB
     * se termine, une rangée dont la liste rétrécit — et lève alors
     * « LayoutCoordinate operations are only valid when isAttached is true »
     * depuis `dispatchKeyEvent`, ce qui tuait l'application sur la TV.
     *
     * Le bug est interne à `androidx.compose.ui.focus` : l'appui perdu est sans
     * conséquence pour l'utilisateur (le suivant déplace bien le focus), le
     * crash ne l'était pas. Toute autre `IllegalStateException` continue de
     * remonter : elle signalerait un bug applicatif, pas celui-ci.
     *
     * `RestrictedApi` est levé parce que `ComponentActivity` redéfinit
     * `dispatchKeyEvent` en `@RestrictTo(LIBRARY_GROUP_PREFIX)` pour son propre
     * `KeyEventDispatcher`. Redéfinir la méthode d'`Activity` en déléguant à
     * `super` reste le point d'interception prévu par le framework : la chaîne
     * AndroidX est appelée intacte, rien n'est court-circuité.
     */
    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (e.message?.contains(DETACHED_COORDINATES_MESSAGE) != true) throw e
            com.cstv.app.di.IptvLog.e(
                "FOCUS",
                "Recherche de focus D-pad interrompue : nœud Compose détaché",
                e
            )
            true
        }

    private fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/** Extrait du message de `LayoutNodeCoordinates` — voir MainActivity.dispatchKeyEvent. */
private const val DETACHED_COORDINATES_MESSAGE = "isAttached is true"

// --- Lecture hors-ligne (feature #15) : reconstruit des modèles de lecture
// minimaux depuis un DownloadedItem, sans fetch réseau des détails (l'app peut
// être hors-ligne). L'URL est reconstruite par le player depuis les identifiants
// stockés ; le cache de téléchargement sert le fichier local. ---
/** Nombre et espacement des tentatives de prise de focus après une navigation. */
private const val FOCUS_REQUEST_ATTEMPTS = 60
private const val FOCUS_REQUEST_RETRY_MS = 120L

/**
 * Une demande de focus lève si aucun nœud focusable n'est encore attaché (écran
 * en cours de composition, contenu vide) : l'échec ne doit pas remonter en
 * crash, la navigation reste correcte sans lui. L'appelant vérifie la prise
 * effective du focus via l'état du conteneur, pas via cette fonction.
 */
private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}

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
