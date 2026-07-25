package com.cstv.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
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
    
    // Active states
    activeStream: LiveStream?,
    onActiveStreamChanged: (LiveStream?) -> Unit,
    activeStreamsList: List<LiveStream>,
    onActiveStreamsListChanged: (List<LiveStream>) -> Unit,
    
    activeVodMovie: VodStream?,
    onActiveVodMovieChanged: (VodStream?) -> Unit,
    activeVodDetails: VodDetails?,
    onActiveVodDetailsChanged: (VodDetails?) -> Unit,
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

    NavHost(
        navController = navController,
        startDestination = if (loggedInUser == null) "login" else "home",
        // Player routes intentionally ignore system insets so their video can fill the display.
        modifier = Modifier.padding(if (isPlayerRoute) PaddingValues(0.dp) else paddingValues)
    ) {
        composable("login") {
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
        composable("home") {
            val activeProfile = profileState.profiles.find { it.id == profileState.activeProfileId }
            HomeScreen(
                userInfo = loggedInUser ?: UserInfo("User", true, "Active", "Inconnue", 1, 0, "Connecté"),
                isTv = isTv,
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
                onNavigateToRecentlyAdded = { isSeries ->
                    navController.navigate("recently_added/$isSeries")
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
                    navController.navigate("profile_selection")
                },
                onPlayResumeWatchingMovie = { position ->
                    val details = VodDetails(
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
                    onActiveVodDetailsChanged(details)
                    onResumePositionMsChanged(position.positionMs)
                    navController.navigate("vod_player")
                },
                onPlayResumeWatchingSeries = { position ->
                    val sName = position.title?.substringBefore(" - ") ?: "Série"
                    val epTitle = position.title?.substringAfter(" - ")?.substringAfter(" ") ?: "Épisode"
                    val episode = SeriesEpisode(
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
                    onActiveEpisodeChanged(episode)
                    val details = SeriesDetails(
                        seriesId = 0,
                        name = sName,
                        cover = position.coverUrl,
                        rating = "0",
                        seasons = emptyList(),
                        episodes = emptyMap()
                    )
                    onActiveSeriesDetailsChanged(details)
                    navController.navigate("series_player")
                },
                onPlayLiveStream = { stream, list ->
                    onActiveStreamChanged(stream)
                    onActiveStreamsListChanged(list)
                    navController.navigate("live_player")
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
                isTv = isTv,
                onStreamSelected = { stream, list ->
                    onActiveStreamChanged(stream)
                    onActiveStreamsListChanged(list)
                    navController.navigate("live_player")
                }
            )
        }
        composable("movies") {
            val vodViewModel: VodViewModel = hiltViewModel()
            VodScreen(
                viewModel = vodViewModel,
                isTv = isTv,
                favoritesList = favsState.favorites,
                onMovieSelected = { stream ->
                    onActiveVodMovieChanged(stream)
                    navController.navigate("vod_details")
                },
                onNavigateToFavorites = { navController.navigate("favorites") }
            )
        }
        composable("series") {
            val seriesViewModel: SeriesViewModel = hiltViewModel()
            SeriesScreen(
                viewModel = seriesViewModel,
                isTv = isTv,
                favoritesList = favsState.favorites,
                onSeriesSelected = { stream ->
                    onActiveSeriesShowChanged(stream)
                    navController.navigate("series_details")
                },
                onNavigateToFavorites = { navController.navigate("favorites") }
            )
        }
        composable("search") {
            SearchScreen(
                viewModel = favoritesViewModel,
                isTv = isTv,
                onPlayLive = { stream ->
                    onActiveStreamChanged(stream)
                    onActiveStreamsListChanged(listOf(stream))
                    navController.navigate("live_player")
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
        composable("favorites") {
            FavoritesScreen(
                viewModel = favoritesViewModel,
                isTv = isTv,
                onPlayLive = { id, catId ->
                    val stream = LiveStream(id, "Chaîne Favorie", null, null, 1, catId)
                    onActiveStreamChanged(stream)
                    onActiveStreamsListChanged(listOf(stream))
                    navController.navigate("live_player")
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
        composable("settings") {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
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
                }
            )
        }
        composable("downloads") {
            com.cstv.app.presentation.downloads.DownloadsScreen(
                viewModel = downloadsViewModel,
                isTv = isTv,
                onPlayMovie = { item ->
                    onActiveVodDetailsChanged(com.cstv.app.buildOfflineVodDetails(item))
                    onResumePositionMsChanged(0L)
                    navController.navigate("vod_player")
                },
                onPlayEpisode = { item ->
                    val episode = com.cstv.app.buildOfflineEpisode(item)
                    onActiveSeriesDetailsChanged(com.cstv.app.buildOfflineSeriesDetails(item, episode))
                    onActiveEpisodeChanged(episode)
                    navController.navigate("series_player")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("category_management") {
            val categoryManagementViewModel: com.cstv.app.presentation.settings.CategoryManagementViewModel = hiltViewModel()
            com.cstv.app.presentation.settings.CategoryManagementScreen(
                viewModel = categoryManagementViewModel,
                isTv = isTv,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("profile_selection") {
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
        composable("profile_management") {
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
                    onActiveVodDetailsChanged(details)
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
                            onResumePositionMsChanged(0L)
                            navController.navigate("vod_player")
                        },
                        onResumePlayback = { pos ->
                            onResumePositionMsChanged(pos)
                            navController.navigate("vod_player")
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
                    )
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        composable("series_details") {
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
                    onActiveSeriesDetailsChanged(details)
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
                            onActiveEpisodeChanged(episode)
                            navController.navigate("series_player")
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
                VodPlayerScreen(
                    details = activeVodDetails,
                    initialPositionMs = resumePositionMs,
                    credentials = creds,
                    isTv = isTv,
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
                    episode = activeEpisode,
                    seriesId = activeSeriesDetails?.seriesId ?: 0,
                    seriesName = activeSeriesDetails?.name ?: "Série",
                    seriesCover = activeSeriesDetails?.cover,
                    seriesEpisodes = activeSeriesDetails?.episodes ?: emptyMap(),
                    credentials = creds,
                    isTv = isTv,
                    viewModel = seriesViewModel,
                    onClose = {
                        navController.popBackStack()
                    }
                )
            } else {
                navController.popBackStack()
            }
        }
        composable(
            "recently_added/{isSeries}",
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
