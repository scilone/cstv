package com.cstv.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.cstv.app.presentation.livetv.LiveTvScreen
import com.cstv.app.presentation.livetv.LiveTvViewModel
import com.cstv.app.presentation.login.LoginScreen
import com.cstv.app.presentation.login.LoginViewModel
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
import com.cstv.app.presentation.settings.SettingsViewModel
import com.cstv.app.presentation.profile.ProfileViewModel
import com.cstv.app.presentation.profile.ProfileUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyListState

// Xtream stream identifiers are positive; this marks an entry reached without
// its required navigation seed, which is defensively popped below.
private const val NO_STREAM_ID = -1

/** Exécute une sortie player atomique et ne navigue jamais au-dessus d'un player non dépilé. */
private fun NavHostController.openPlayerDetails(
    action: PlayerDetailsAction,
    detailsRoute: String,
    prepareDetails: () -> Unit
): Boolean = when (action) {
    PlayerDetailsAction.POP_TO_DETAILS -> popBackStack()
    PlayerDetailsAction.REPLACE_WITH_DETAILS -> {
        if (!popBackStack()) {
            false
        } else {
            prepareDetails()
            navigate(detailsRoute)
            true
        }
    }
    PlayerDetailsAction.UNAVAILABLE -> false
}

/**
 * Owner for the tab catalogue ViewModels (Live TV, films, séries).
 *
 * A tab entry is destroyed as soon as the user leaves it, so a ViewModel scoped to
 * it refetches the whole catalogue behind a spinner on every return. The home entry
 * instead lives for the entire logged-in session and is popped only on logout, which
 * is exactly when these catalogues must be dropped.
 */
/**
 * Un écran de détail qui a fini de charger sans résultat vient forcément d'un
 * appel en échec (réponse illisible, panel injoignable, identifiants expirés).
 * Afficher un indicateur de chargement de plus laisserait l'écran tourner
 * indéfiniment alors qu'aucune requête ne repartira : le message d'erreur brut
 * est montré tel quel, avec de quoi relancer ou sortir.
 */
@Composable
private fun MediaDetailsErrorState(
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = message ?: "Impossible de charger cette fiche.",
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Réessayer") }
            TextButton(onClick = onBack) { Text("Retour") }
        }
    }
}

@Composable
private fun rememberTabViewModelOwner(
    navController: NavHostController,
    tabEntry: NavBackStackEntry
): ViewModelStoreOwner = remember(tabEntry) {
    // Invariant des onglets mobile : `home` est l'entrée racine conservée par
    // navigateToRootTab(), donc elle est toujours présente ici.
    navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE)
}

/**
 * Déclare une route dont le contenu commence sous la barre d'état.
 *
 * L'inset est posé dans la page et non sur le conteneur de navigation. Appliqué
 * au NavHost, il devrait varier selon la route affichée — or la destination
 * bascule dès le début d'une transition, si bien que l'écran sortant, encore
 * visible, se décalait d'un coup. Les fiches de détail, qui laissent leur image
 * courir sous la barre d'état, sont simplement déclarées avec `composable`.
 */
private fun NavGraphBuilder.composableBelowStatusBar(
    route: String,
    topInset: Dp,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route, arguments) { entry ->
    Box(modifier = Modifier.fillMaxSize().padding(top = topInset)) { content(entry) }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    isPlayerRoute: Boolean,
    loggedInUser: UserInfo?,
    onUserChanged: (UserInfo?) -> Unit,
    loginViewModel: LoginViewModel,
    favoritesViewModel: FavoritesViewModel,
    homeViewModel: HomeViewModel,
    profileViewModel: ProfileViewModel,
    profileState: ProfileUiState,
    favsState: com.cstv.app.presentation.favorites.FavoritesUiState,
    homeLazyListState: LazyListState,
    isTv: Boolean,
    appUpdateViewModel: com.cstv.app.presentation.update.AppUpdateViewModel,
    
    // Active states
    activeStream: LiveStream?,
    onActiveStreamChanged: (LiveStream?) -> Unit,
    activeStreamsList: List<LiveStream>,
    onActiveStreamsListChanged: (List<LiveStream>) -> Unit,
    
    activeVodMovie: VodStream?,
    onActiveVodMovieChanged: (VodStream?) -> Unit,
    activeVodDetails: VodDetails?,
    onActiveVodDetailsChanged: (VodDetails?) -> Unit,
    /** F39 §8.6 : voir MainActivity — décide `versionsEnabled` côté VodPlayerScreen. */
    isVodPlaybackOffline: Boolean = false,
    onIsVodPlaybackOfflineChanged: (Boolean) -> Unit = {},
    isSeriesPlaybackOffline: Boolean = false,
    onIsSeriesPlaybackOfflineChanged: (Boolean) -> Unit = {},
    resumePositionMs: Long,
    onResumePositionMsChanged: (Long) -> Unit,
    
    activeSeriesShow: SeriesStream?,
    onActiveSeriesShowChanged: (SeriesStream?) -> Unit,
    activeSeriesDetails: SeriesDetails?,
    onActiveSeriesDetailsChanged: (SeriesDetails?) -> Unit,
    activeEpisode: SeriesEpisode?,
    onActiveEpisodeChanged: (SeriesEpisode?) -> Unit
) {
    // Téléchargements hors-ligne (feature #15) : ViewModel partagé côté mobile.
    val downloadsViewModel: com.cstv.app.presentation.downloads.DownloadsViewModel = hiltViewModel()
    val downloadsState by downloadsViewModel.state.collectAsStateWithLifecycle()

    val layoutDirection = LocalLayoutDirection.current
    val topInset = paddingValues.calculateTopPadding()

    NavHost(
        navController = navController,
        startDestination = if (loggedInUser == null) "login" else "home",
        // Player routes intentionally ignore system insets so their video can fill the display.
        //
        //
        // L'inset du haut est délibérément absent ici : il est posé route par
        // route (voir `composableBelowStatusBar`), pour que les fiches de détail
        // puissent occuper la zone de la barre d'état sans qu'aucun padding ne
        // change en cours de transition.
        modifier = Modifier.padding(
            if (isPlayerRoute) PaddingValues(0.dp)
            else PaddingValues(
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding()
            )
        )
    ) {
        composableBelowStatusBar("login", topInset) {
            LoginScreen(
                viewModel = loginViewModel,
                isTv = isTv,
                onLoginSuccess = { userInfo ->
                    onUserChanged(userInfo)
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composableBelowStatusBar("home", topInset) {
            val activeProfile = profileState.profiles.find { it.id == profileState.activeProfileId }
            HomeScreen(
                userInfo = loggedInUser ?: UserInfo("User", true, "Active", "Inconnue", 1, 0, "Connecté"),
                isTv = isTv,
                viewModel = homeViewModel,
                activeProfileAvatarId = activeProfile?.avatarId ?: 0,
                activeProfileName = activeProfile?.name ?: loggedInUser?.username ?: "",
                lazyListState = homeLazyListState,
                onNavigateToLiveTv = {
                    navController.navigateToRootTab("tv")
                },
                onNavigateToVod = {
                    navController.navigateToRootTab("movies")
                },
                onNavigateToSeries = {
                    navController.navigateToRootTab("series")
                },
                onNavigateToVodCategory = { category ->
                    navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE)
                        .savedStateHandle[MobileNavigation.PENDING_VOD_CATEGORY] = category.categoryId
                    navController.navigateToRootTab("movies")
                },
                onNavigateToSeriesCategory = { category ->
                    navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE)
                        .savedStateHandle[MobileNavigation.PENDING_SERIES_CATEGORY] = category.categoryId
                    navController.navigateToRootTab("series")
                },
                onNavigateToFavorites = {
                    navController.navigate("favorites")
                },
                onNavigateToSearch = {
                    navController.navigateToRootTab("search")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToProfileManagement = {
                    navController.navigate("profile_selection")
                },
                onPlayResumeWatchingMovie = { position ->
                    val details = VodDetails(
                        streamId = position.streamId,
                        name = position.title ?: "Film",
                        director = "Inconnu",
                        actors = "Inconnu",
                        releaseDate = "Inconnu",
                        // Genre repris du catalogue en cache : la reprise court-circuite
                        // `get_vod_info`, et le littéral « Inconnu » s'affichait tel quel
                        // sous le titre dans le lecteur. Vide plutôt qu'« Inconnu » quand
                        // le catalogue ne le connaît pas : le lecteur masque alors la ligne.
                        genre = position.genre.orEmpty(),
                        plot = position.plot ?: "Aucun résumé disponible.",
                        rating = "0",
                        coverBig = position.coverUrl,
                        containerExtension = position.containerExtension ?: "mp4",
                        resumePositionMs = position.positionMs,
                        durationMs = position.durationMs
                    )
                    homeViewModel.requestPlayback(
                        com.cstv.app.domain.model.DownloadedItem.movieContentId(position.streamId)
                    ) {
                        onActiveVodDetailsChanged(details)
                        onIsVodPlaybackOfflineChanged(false)
                        onResumePositionMsChanged(position.positionMs)
                        navController.navigate("vod_player")
                    }
                },
                onPlayResumeWatchingSeries = { position ->
                    // Fiche synthétique (fallback) construite depuis PlaybackPosition seul,
                    // utilisée si le catalogue complet de la série n'est pas récupérable
                    // (hors ligne, série absente du cache). Sans les épisodes réels, next/
                    // previous restent indisponibles dans ce cas.
                    val sName = position.title?.substringBefore(" - ") ?: "Série"
                    val epTitle = position.title?.substringAfter(" - ")?.substringAfter(" ") ?: "Épisode"
                    val fallbackEpisode = SeriesEpisode(
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
                    val fallbackDetails = SeriesDetails(
                        seriesId = position.seriesId ?: 0,
                        name = sName,
                        cover = position.coverUrl,
                        rating = "0",
                        seasons = emptyList(),
                        episodes = emptyMap()
                    )
                    homeViewModel.requestPlayback(
                        com.cstv.app.domain.model.DownloadedItem.episodeContentId(position.streamId)
                    ) {
                        // Détails complets (même source que la catégorie « Tout » des séries)
                        // pour retrouver l'épisode en cours dans sa vraie place de la carte
                        // `episodes` : c'est ce qui permet au lecteur d'afficher/enchaîner
                        // épisode suivant/précédent. B31 : ce chemin est désormais réellement
                        // emprunté (le discriminant cassé dans HomeScreen empêchait jusque-là
                        // qu'il soit jamais atteint).
                        val fullDetails = position.seriesId?.let { homeViewModel.loadSeriesDetailsForResume(it) }
                        val realEpisode = fullDetails?.episodes?.values
                            ?.flatten()
                            ?.firstOrNull { it.id == position.streamId }
                            // Position de lecture toujours reprise depuis PlaybackPosition,
                            // absente du catalogue (elle n'est jamais persistée sur SeriesEpisode).
                            ?.copy(resumePositionMs = position.positionMs, durationMs = position.durationMs)
                        onActiveEpisodeChanged(realEpisode ?: fallbackEpisode)
                        onActiveSeriesDetailsChanged(fullDetails ?: fallbackDetails)
                        onIsSeriesPlaybackOfflineChanged(false)
                        navController.navigate("series_player")
                    }
                },
                onPlayLiveStream = { stream, list ->
                    // Un flux Live n'est jamais téléchargeable : hors ligne, il
                    // n'est pas lancé, avec un message qui le dit.
                    homeViewModel.requestPlayback(null) {
                        onActiveStreamChanged(stream)
                        onActiveStreamsListChanged(list)
                        navController.navigate("live_player")
                    }
                },
                onSelectMovieDetail = { stream ->
                    onActiveVodMovieChanged(stream)
                    navController.navigate("vod_details")
                },
                onSelectSeriesDetail = { stream ->
                    onActiveSeriesShowChanged(stream)
                    navController.navigate("series_details")
                },
                onNavigateToDownloads = {
                    navController.navigate("downloads")
                },
                onPlayDownloadedMovie = { item ->
                    onActiveVodDetailsChanged(com.cstv.app.buildOfflineVodDetails(item))
                    onIsVodPlaybackOfflineChanged(true)
                    onResumePositionMsChanged(0L)
                    navController.navigate("vod_player")
                },
                onPlayDownloadedEpisode = { item ->
                    val episode = com.cstv.app.buildOfflineEpisode(item)
                    onActiveSeriesDetailsChanged(com.cstv.app.buildOfflineSeriesDetails(item, episode))
                    onActiveEpisodeChanged(episode)
                    onIsSeriesPlaybackOfflineChanged(true)
                    navController.navigate("series_player")
                }
            )
        }
        composableBelowStatusBar("tv", topInset) { tabEntry ->
            val liveTvViewModel: LiveTvViewModel = hiltViewModel(rememberTabViewModelOwner(navController, tabEntry))
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
                    onActiveStreamChanged(stream)
                    onActiveStreamsListChanged(list)
                    navController.navigate("live_player")
                }
            )
        }
        composableBelowStatusBar("movies", topInset) { tabEntry ->
            val vodViewModel: VodViewModel = hiltViewModel(rememberTabViewModelOwner(navController, tabEntry))
            val vodState by vodViewModel.state.collectAsStateWithLifecycle()
            // Même invariant que rememberTabViewModelOwner : Home est la racine
            // du graphe d'onglets mobile lorsqu'une catégorie est transmise.
            val rootEntry = remember(tabEntry) { navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE) }
            val pendingCategory by rootEntry.savedStateHandle
                .getStateFlow<String?>(MobileNavigation.PENDING_VOD_CATEGORY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pendingCategory, vodState.categories) {
                pendingCategory?.let { categoryId ->
                    if (vodViewModel.selectCategoryById(categoryId)) {
                        rootEntry.savedStateHandle[MobileNavigation.PENDING_VOD_CATEGORY] = null
                    }
                }
            }
            VodScreen(
                viewModel = vodViewModel,
                isTv = isTv,
                favoritesList = favsState.favorites,
                onMovieSelected = { stream ->
                    onActiveVodMovieChanged(stream)
                    navController.navigate("vod_details")
                },
                onResumeMovieSelected = { position ->
                    val details = VodDetails(
                        streamId = position.streamId,
                        name = position.title ?: "Film",
                        director = "",
                        actors = "",
                        releaseDate = position.releaseDate ?: "",
                        genre = position.genre.orEmpty(),
                        plot = position.plot.orEmpty(),
                        rating = "0",
                        coverBig = position.coverUrl,
                        containerExtension = position.containerExtension ?: "mp4",
                        resumePositionMs = position.positionMs,
                        durationMs = position.durationMs
                    )
                    vodViewModel.requestPlayback(position.streamId) {
                        onActiveVodDetailsChanged(details)
                        onIsVodPlaybackOfflineChanged(false)
                        onResumePositionMsChanged(position.positionMs)
                        navController.navigate("vod_player")
                    }
                },
                // Appui long sur une vignette de film.
                onToggleFavorite = { stream ->
                    favoritesViewModel.toggleFavorite(
                        id = stream.streamId,
                        type = "movie",
                        name = stream.name,
                        cover = stream.streamIcon,
                        categoryId = stream.categoryId
                    )
                },
                onNavigateToFavorites = { navController.navigate("favorites") }
            )
        }
        composableBelowStatusBar("series", topInset) { tabEntry ->
            val seriesViewModel: SeriesViewModel = hiltViewModel(rememberTabViewModelOwner(navController, tabEntry))
            val seriesState by seriesViewModel.state.collectAsStateWithLifecycle()
            // Même invariant que rememberTabViewModelOwner : Home est la racine
            // du graphe d'onglets mobile lorsqu'une catégorie est transmise.
            val rootEntry = remember(tabEntry) { navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE) }
            val pendingCategory by rootEntry.savedStateHandle
                .getStateFlow<String?>(MobileNavigation.PENDING_SERIES_CATEGORY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pendingCategory, seriesState.categories) {
                pendingCategory?.let { categoryId ->
                    if (seriesViewModel.selectCategoryById(categoryId)) {
                        rootEntry.savedStateHandle[MobileNavigation.PENDING_SERIES_CATEGORY] = null
                    }
                }
            }
            SeriesScreen(
                viewModel = seriesViewModel,
                isTv = isTv,
                favoritesList = favsState.favorites,
                onSeriesSelected = { stream ->
                    onActiveSeriesShowChanged(stream)
                    navController.navigate("series_details")
                },
                onResumeSeriesSelected = { position ->
                    seriesViewModel.requestPlayback(position) {
                        val fallbackEpisode = SeriesEpisode(
                            id = position.streamId,
                            episodeNum = position.episodeNum ?: 1,
                            title = position.title.orEmpty(),
                            containerExtension = position.containerExtension ?: "mp4",
                            plot = position.plot.orEmpty(),
                            duration = position.duration ?: "00:00",
                            releaseDate = position.releaseDate.orEmpty(),
                            resumePositionMs = position.positionMs,
                            durationMs = position.durationMs,
                            seasonNum = position.seasonNum ?: 1
                        )
                        val fallbackDetails = SeriesDetails(
                            seriesId = position.seriesId ?: 0,
                            name = position.title?.substringBefore(" - ") ?: "Série",
                            cover = position.coverUrl,
                            rating = "0",
                            seasons = emptyList(),
                            episodes = emptyMap()
                        )
                        val fullDetails = position.seriesId?.let { seriesViewModel.loadSeriesDetailsForResume(it) }
                        val realEpisode = fullDetails?.episodes?.values?.flatten()
                            ?.firstOrNull { it.id == position.streamId }
                            ?.copy(resumePositionMs = position.positionMs, durationMs = position.durationMs)
                        onActiveEpisodeChanged(realEpisode ?: fallbackEpisode)
                        onActiveSeriesDetailsChanged(fullDetails ?: fallbackDetails)
                        onIsSeriesPlaybackOfflineChanged(false)
                        navController.navigate("series_player")
                    }
                },
                // Appui long sur une vignette de série.
                onToggleFavorite = { stream ->
                    favoritesViewModel.toggleFavorite(
                        id = stream.seriesId,
                        type = "series",
                        name = stream.name,
                        cover = stream.cover,
                        categoryId = stream.categoryId
                    )
                },
                onNavigateToFavorites = { navController.navigate("favorites") }
            )
        }
        composableBelowStatusBar("search", topInset) {
            SearchScreen(
                viewModel = favoritesViewModel,
                isTv = isTv,
                onPlayLive = { stream ->
                    favoritesViewModel.requestPlayback(null) {
                        onActiveStreamChanged(stream)
                        onActiveStreamsListChanged(listOf(stream))
                        navController.navigate("live_player")
                    }
                },
                onSelectMovie = { stream ->
                    onActiveVodMovieChanged(stream)
                    navController.navigate("vod_details")
                },
                onSelectSeries = { stream ->
                    onActiveSeriesShowChanged(stream)
                    navController.navigate("series_details")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composableBelowStatusBar("favorites", topInset) {
            FavoritesScreen(
                viewModel = favoritesViewModel,
                isTv = isTv,
                onPlayLive = { id, catId ->
                    favoritesViewModel.requestPlayback(null) {
                        val stream = LiveStream(id, "Chaîne Favorie", null, null, 1, catId)
                        onActiveStreamChanged(stream)
                        onActiveStreamsListChanged(listOf(stream))
                        navController.navigate("live_player")
                    }
                },
                onSelectMovie = { id, catId ->
                    onActiveVodMovieChanged(VodStream(id, "Film Favori", null, null, null, catId))
                    navController.navigate("vod_details")
                },
                onSelectSeries = { id, catId ->
                    onActiveSeriesShowChanged(SeriesStream(id, "Série Favorie", null, null, null, catId))
                    navController.navigate("series_details")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composableBelowStatusBar("settings", topInset) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val appUpdateState by appUpdateViewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(
                viewModel = settingsViewModel,
                isTv = isTv,
                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    loginViewModel.logout()
                    onUserChanged(null)
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onManageCategories = {
                    navController.navigate("category_management")
                },
                onManageDownloads = {
                    navController.navigate("downloads")
                },
                appUpdateState = appUpdateState,
                installedVersionName = com.cstv.app.BuildConfig.VERSION_NAME,
                onCheckForUpdate = { appUpdateViewModel.checkManually() }
            )
        }
        composableBelowStatusBar("downloads", topInset) {
            com.cstv.app.presentation.downloads.DownloadsScreen(
                viewModel = downloadsViewModel,
                isTv = isTv,
                onPlayMovie = { item ->
                    onActiveVodDetailsChanged(com.cstv.app.buildOfflineVodDetails(item))
                    onIsVodPlaybackOfflineChanged(true)
                    onResumePositionMsChanged(0L)
                    navController.navigate("vod_player")
                },
                onPlayEpisode = { item ->
                    val episode = com.cstv.app.buildOfflineEpisode(item)
                    onActiveSeriesDetailsChanged(com.cstv.app.buildOfflineSeriesDetails(item, episode))
                    onActiveEpisodeChanged(episode)
                    onIsSeriesPlaybackOfflineChanged(true)
                    navController.navigate("series_player")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composableBelowStatusBar("category_management", topInset) {
            val categoryManagementViewModel: com.cstv.app.presentation.settings.CategoryManagementViewModel = hiltViewModel()
            com.cstv.app.presentation.settings.CategoryManagementScreen(
                viewModel = categoryManagementViewModel,
                isTv = isTv,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composableBelowStatusBar("profile_selection", topInset) {
            com.cstv.app.presentation.profile.ProfileSelectionScreen(
                profiles = profileState.profiles,
                isTv = isTv,
                onProfileSelected = { profile ->
                    profileViewModel.selectProfile(profile.id)
                    navController.popBackStack()
                },
                onManageProfiles = {
                    navController.navigate("profile_management")
                },
                onLogout = {
                    loginViewModel.logout()
                    onUserChanged(null)
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composableBelowStatusBar("profile_management", topInset) {
            com.cstv.app.presentation.profile.ProfileManagementScreen(
                viewModel = profileViewModel,
                isTv = isTv,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("vod_details") {
            val vodViewModel: VodViewModel = hiltViewModel()
            val state by vodViewModel.state.collectAsStateWithLifecycle()
            
            // This value belongs to this back-stack entry. remember would be lost when
            // NavHost removes A from composition for B, then would reread B on return.
            val entryStreamId = rememberSaveable { activeVodMovie?.streamId ?: NO_STREAM_ID }
            LaunchedEffect(entryStreamId) {
                if (entryStreamId != NO_STREAM_ID) {
                    vodViewModel.selectStreamId(entryStreamId)
                } else {
                    navController.popBackStack()
                }
            }

            if (state.isLoadingDetails) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                state.selectedVodDetails?.let { details ->
                    onActiveVodDetailsChanged(details)
                    onIsVodPlaybackOfflineChanged(false)
                    val isFav = favsState.favorites.any { it.id == details.streamId && it.type == "movie" }

                    // F39 §8.6 (évolution PO) : versions candidates de ce film pour le bouton
                    // « Versions » de la fiche — vide (bouton masqué) hors ligne ou si `linkKey`
                    // n'est pas encore normalisé.
                    var movieVersions by remember { mutableStateOf(emptyList<VodStream>()) }
                    LaunchedEffect(details.streamId) {
                        movieVersions = vodViewModel.getMovieVersions(details.streamId)
                    }

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
                            // Hors ligne et non téléchargé : rien n'est lancé,
                            // le message est porté par l'état de l'écran.
                            vodViewModel.requestPlayback(details.streamId) {
                                vodViewModel.clearPosition(details.streamId)
                                onResumePositionMsChanged(0L)
                                navController.navigate("vod_player")
                            }
                        },
                        onResumePlayback = { pos ->
                            vodViewModel.requestPlayback(details.streamId) {
                                onResumePositionMsChanged(pos)
                                navController.navigate("vod_player")
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onSearchQueryTriggered = { query ->
                            favoritesViewModel.searchFromCredit(query)
                            navController.navigate("search")
                        },
                        relatedStreams = state.relatedStreams,
                        onSelectRelated = { stream ->
                            onActiveVodMovieChanged(stream)
                            navController.navigate("vod_details")
                        },
                        downloadItem = downloadsState.downloads.firstOrNull {
                            it.contentId == com.cstv.app.domain.model.DownloadedItem.movieContentId(details.streamId)
                        },
                        onDownload = { downloadsViewModel.downloadMovie(details) },
                        onRemoveDownload = {
                            downloadsViewModel.remove(com.cstv.app.domain.model.DownloadedItem.movieContentId(details.streamId))
                        },
                        mediaRating = state.mediaRating,
                        isRatingSaving = state.isRatingSaving,
                        ratingError = state.ratingError,
                        onLike = { vodViewModel.setRating(if (state.mediaRating == com.cstv.app.domain.model.MediaRatingValue.LIKE) null else com.cstv.app.domain.model.MediaRatingValue.LIKE) },
                        onDislike = { vodViewModel.setRating(if (state.mediaRating == com.cstv.app.domain.model.MediaRatingValue.DISLIKE) null else com.cstv.app.domain.model.MediaRatingValue.DISLIKE) },
                        onConsumeRatingError = vodViewModel::consumeRatingError
                        ,trailerState = state.trailerPreview,
                        onTrailerReady = vodViewModel::startTrailerPreview,
                        onTrailerEnded = vodViewModel::cancelTrailerPreview,
                        onTrailerFailed = vodViewModel::reportTrailerPlaybackFailure,
                        availableVersions = movieVersions,
                        onSelectVersion = { streamId -> vodViewModel.selectStreamId(streamId) }
                    )
                } ?: MediaDetailsErrorState(
                    message = state.error,
                    onRetry = { vodViewModel.selectStreamId(entryStreamId) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("series_details") {
            val seriesViewModel: SeriesViewModel = hiltViewModel()
            val state by seriesViewModel.state.collectAsStateWithLifecycle()

            // This value belongs to this back-stack entry. remember would be lost when
            // NavHost removes A from composition for B, then would reread B on return.
            val entrySeriesId = rememberSaveable { activeSeriesShow?.seriesId ?: NO_STREAM_ID }
            LaunchedEffect(entrySeriesId) {
                if (entrySeriesId != NO_STREAM_ID) {
                    seriesViewModel.selectStreamId(entrySeriesId)
                } else {
                    navController.popBackStack()
                }
            }

            if (state.isLoadingDetails) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                state.selectedSeriesDetails?.let { details ->
                    onActiveSeriesDetailsChanged(details)
                    onIsSeriesPlaybackOfflineChanged(false)
                    val isFav = favsState.favorites.any { it.id == details.seriesId && it.type == "series" }

                    // F39 §8.6 (évolution PO) : voir "vod_details" — même motif.
                    var seriesVersions by remember { mutableStateOf(emptyList<SeriesStream>()) }
                    LaunchedEffect(details.seriesId) {
                        seriesVersions = seriesViewModel.getSeriesVersions(details.seriesId)
                    }

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
                            seriesViewModel.requestPlayback(episode.id) {
                                onActiveEpisodeChanged(episode)
                                navController.navigate("series_player")
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onSearchQueryTriggered = { query ->
                            favoritesViewModel.searchFromCredit(query)
                            navController.navigate("search")
                        },
                        relatedSeries = state.relatedSeries,
                        onSelectRelated = { stream ->
                            onActiveSeriesShowChanged(stream)
                            navController.navigate("series_details")
                        },
                        episodeDownloads = downloadsState.downloads
                            .filter { it.type == com.cstv.app.domain.model.DownloadedItem.TYPE_EPISODE }
                            .associateBy { it.streamId },
                        onDownloadEpisode = { episode ->
                            downloadsViewModel.downloadEpisode(episode, details.seriesId, details.name, details.cover)
                        },
                        onRemoveEpisodeDownload = { episodeId ->
                            downloadsViewModel.remove(com.cstv.app.domain.model.DownloadedItem.episodeContentId(episodeId))
                        },
                        mediaRating = state.mediaRating,
                        isRatingSaving = state.isRatingSaving,
                        ratingError = state.ratingError,
                        onLike = { seriesViewModel.setRating(if (state.mediaRating == com.cstv.app.domain.model.MediaRatingValue.LIKE) null else com.cstv.app.domain.model.MediaRatingValue.LIKE) },
                        onDislike = { seriesViewModel.setRating(if (state.mediaRating == com.cstv.app.domain.model.MediaRatingValue.DISLIKE) null else com.cstv.app.domain.model.MediaRatingValue.DISLIKE) },
                        onConsumeRatingError = seriesViewModel::consumeRatingError
                        ,trailerState = state.trailerPreview,
                        onTrailerReady = seriesViewModel::startTrailerPreview,
                        onTrailerEnded = seriesViewModel::cancelTrailerPreview,
                        onTrailerFailed = seriesViewModel::reportTrailerPlaybackFailure,
                        availableVersions = seriesVersions,
                        onSelectVersion = { newSeriesId -> seriesViewModel.selectStreamId(newSeriesId) }
                    )
                } ?: MediaDetailsErrorState(
                    message = state.error,
                    onRetry = { seriesViewModel.selectStreamId(entrySeriesId) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("live_player") {
            val liveTvViewModel: LiveTvViewModel = hiltViewModel()
            val creds = liveTvViewModel.getCredentials()
            if (creds != null && activeStream != null) {
                PlayerScreen(
                    initialStream = activeStream,
                    streamsList = activeStreamsList,
                    credentials = creds,
                    isTv = isTv,
                    viewModel = liveTvViewModel,
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
                val details = activeVodDetails
                val navigationAction = remember {
                    PlayerDetailsNavigation.resolve(
                        detailsRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                        targetId = details.streamId,
                        previousRoute = navController.previousBackStackEntry?.destination?.route,
                        previousTargetId = activeVodMovie?.streamId
                    )
                }
                VodPlayerScreen(
                    details = details,
                    initialPositionMs = resumePositionMs,
                    credentials = creds,
                    isTv = isTv,
                    viewModel = vodViewModel,
                    versionsEnabled = !isVodPlaybackOffline,
                    onClose = {
                        navController.popBackStack()
                    },
                    canOpenDetails = navigationAction != PlayerDetailsAction.UNAVAILABLE,
                    onOpenDetails = {
                        navController.openPlayerDetails(
                            action = navigationAction,
                            detailsRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE
                        ) {
                            onActiveVodMovieChanged(
                                VodStream(
                                    streamId = details.streamId,
                                    name = details.name,
                                    streamIcon = details.coverBig,
                                    rating = details.rating,
                                    added = null,
                                    categoryId = "0"
                                )
                            )
                        }
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
                val episode = activeEpisode
                val details = activeSeriesDetails
                val navigationAction = remember {
                    PlayerDetailsNavigation.resolve(
                        detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                        targetId = details?.seriesId,
                        previousRoute = navController.previousBackStackEntry?.destination?.route,
                        previousTargetId = activeSeriesShow?.seriesId
                    )
                }
                SeriesPlayerScreen(
                    episode = episode,
                    seriesId = details?.seriesId ?: 0,
                    seriesName = details?.name ?: "Série",
                    seriesCover = details?.cover,
                    seriesEpisodes = details?.episodes ?: emptyMap(),
                    credentials = creds,
                    isTv = isTv,
                    viewModel = seriesViewModel,
                    versionsEnabled = !isSeriesPlaybackOffline,
                    onClose = {
                        navController.popBackStack()
                    },
                    canOpenDetails = navigationAction != PlayerDetailsAction.UNAVAILABLE,
                    onOpenDetails = {
                        navController.openPlayerDetails(
                            action = navigationAction,
                            detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE
                        ) {
                            onActiveSeriesShowChanged(
                                SeriesStream(
                                    seriesId = details?.seriesId ?: 0,
                                    name = details?.name ?: "Série",
                                    cover = details?.cover,
                                    rating = null,
                                    added = null,
                                    categoryId = "0"
                                )
                            )
                        }
                    }
                )
            } else {
                navController.popBackStack()
            }
        }
        composableBelowStatusBar(
            "recently_added/{isSeries}",
            topInset,
            arguments = listOf(navArgument("isSeries") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isSeriesParam = backStackEntry.arguments?.getBoolean("isSeries") ?: false
            val recentlyAddedViewModel: RecentlyAddedViewModel = hiltViewModel()
            RecentlyAddedScreen(
                viewModel = recentlyAddedViewModel,
                isTv = isTv,
                isSeries = isSeriesParam,
                onBack = {
                    navController.popBackStack()
                },
                onSelectMovieDetail = { stream ->
                    onActiveVodMovieChanged(stream)
                    navController.navigate("vod_details")
                },
                onSelectSeriesDetail = { stream ->
                    onActiveSeriesShowChanged(stream)
                    navController.navigate("series_details")
                }
            )
        }
    }
}
