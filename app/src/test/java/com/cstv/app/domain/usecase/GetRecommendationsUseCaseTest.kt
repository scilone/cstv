package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.data.local.storage.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetRecommendationsUseCaseTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun test_coldStart_returnsEmptyLists_ifHistoryIsSmall() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(flowOf(emptyList()))

        whenever(profileManager.currentProfileId()).thenReturn(1)
        
        // Only 2 items in history (< 3 threshold)
        val history = listOf(
            PlaybackPosition(streamId = 10, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 20, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "episode", seriesId = 99)
        )
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(history)

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))

        val result = useCase(currentTimeMs = 1000L)

        assertTrue(result.movies.isEmpty())
        assertTrue(result.series.isEmpty())
        // Should not even try to fetch the massive catalog
        verify(vodRepository, times(0)).getCachedVodStreams(any())
    }

    @Test
    fun test_cacheLogic_ttlAndProfileChange() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(flowOf(emptyList()))

        // 3 items in history to pass cold start
        val history = listOf(
            PlaybackPosition(streamId = 10, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 11, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 12, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie")
        )
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(history)
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))

        // 1st call for Profile 1
        whenever(profileManager.currentProfileId()).thenReturn(1)
        useCase(currentTimeMs = 1000L)
        
        // 2nd call for Profile 1 immediately after -> should use cache
        useCase(currentTimeMs = 2000L)
        
        // Repo should only be called once so far
        verify(vodRepository, times(1)).getCachedVodStreams("all")

        // 3rd call for Profile 1 but 25 hours later -> cache expired
        useCase(currentTimeMs = 1000L + (25L * 3600 * 1000L))
        verify(vodRepository, times(2)).getCachedVodStreams("all")

        // 4th call immediately, but for Profile 2 -> cache invalidate due to profile switch
        whenever(profileManager.currentProfileId()).thenReturn(2)
        useCase(currentTimeMs = 1000L + (25L * 3600 * 1000L) + 10L)
        verify(vodRepository, times(3)).getCachedVodStreams("all")
    }

    @Test
    fun test_excludesHiddenCategories() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(flowOf(emptyList()))

        whenever(profileManager.currentProfileId()).thenReturn(1)
        
        // 3 items in history (cat_ok)
        val history = listOf(
            PlaybackPosition(streamId = 1, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 2, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 3, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie")
        )
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(history)

        val movieCatOk = VodStream(10, "Ok Movie", "icon", "5", "0", "cat_ok")
        val movieCatHidden = VodStream(11, "Hidden Movie", "icon", "5", "0", "cat_hidden")

        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(listOf(movieCatOk, movieCatHidden))
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        
        // "cat_hidden" is marked as hidden
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat_hidden" to CategoryPreference("cat_hidden", hidden = true, sortOrder = null))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(emptyMap())

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))
        val result = useCase(currentTimeMs = 1000L)

        // The hidden movie should not be recommended
        assertEquals(1, result.movies.size)
        assertEquals("Ok Movie", result.movies[0].name)
    }

    /**
     * Le filtrage des catégories masquées a lieu au calcul, dont le résultat est
     * conservé 24 h : un masquage décidé après coup laissait la rangée
     * « recommandé pour vous » proposer les médias de la catégorie masquée
     * jusqu'à l'expiration du cache. La lecture du cache les écarte désormais.
     */
    @Test
    fun test_cachedResultExcludesCategoriesHiddenAfterComputation() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(flowOf(emptyList()))

        whenever(profileManager.currentProfileId()).thenReturn(1)

        val history = listOf(
            PlaybackPosition(streamId = 1, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 2, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie"),
            PlaybackPosition(streamId = 3, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie")
        )
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(history)

        val movieCatOk = VodStream(10, "Ok Movie", "icon", "5", "0", "cat_ok")
        val movieCatLater = VodStream(11, "Later Hidden Movie", "icon", "5", "0", "cat_later")
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(listOf(movieCatOk, movieCatLater))
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        // Premier calcul : aucune catégorie masquée, les deux films entrent en cache.
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))
        assertEquals(2, useCase(currentTimeMs = 1000L).movies.size)

        // Masquage décidé après coup : même profil, cache encore frais.
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat_later" to CategoryPreference("cat_later", hidden = true, sortOrder = null))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(emptyMap())

        val fromCache = useCase(currentTimeMs = 2000L)

        // Le cache n'a pas été recalculé…
        verify(vodRepository, times(1)).getCachedVodStreams("all")
        // …mais le film de la catégorie masquée n'est plus proposé.
        assertEquals(1, fromCache.movies.size)
        assertEquals("Ok Movie", fromCache.movies[0].name)
    }

    /**
     * Un favori ne se re-propose pas.
     *
     * Il est déjà dans la liste de l'utilisateur ; l'y renvoyer sous
     * « Recommandé pour vous » occupe une place au détriment d'une découverte.
     */
    @Test
    fun `a favourite is never proposed back as a recommendation`() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(
            flowOf(listOf(FavoriteItem(11, "movie", "Favourite Movie", null, "cat_ok")))
        )
        whenever(profileManager.currentProfileId()).thenReturn(1)

        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(
            (1..3).map {
                PlaybackPosition(streamId = it, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie")
            }
        )
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(
            listOf(
                VodStream(10, "Ok Movie", "icon", "5", "0", "cat_ok"),
                VodStream(11, "Favourite Movie", "icon", "5", "0", "cat_ok")
            )
        )
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))
        val result = useCase(currentTimeMs = 1000L)

        assertEquals(listOf("Ok Movie"), result.movies.map { it.name })
    }

    /**
     * Le genre d'un favori pèse davantage que celui d'un média simplement
     * regardé.
     *
     * Le profil est volontairement partagé : un film seulement vu porte le genre
     * `Comedy`, un film mis en favori porte `Horror`. Les deux candidats sont
     * par ailleurs identiques (même catégorie, même note, même date d'ajout) —
     * seul le poids du signal peut donc les départager.
     */
    @Test
    fun `a favourite genre outweighs a merely watched genre`() = runTest {
        val vodRepository = mock<VodRepository>()
        val seriesRepository = mock<SeriesRepository>()
        val categoryPreferenceRepository = mock<CategoryPreferenceRepository>()
        val profileManager = mock<ProfileManager>()
        val mediaRatingRepository = mock<com.cstv.app.domain.repository.MediaRatingRepository>()
        whenever(mediaRatingRepository.getAllRatings()).thenReturn(emptyList())
        val favoritesRepository = mock<com.cstv.app.domain.repository.FavoritesRepository>()
        whenever(favoritesRepository.observeFavorites()).thenReturn(
            flowOf(listOf(FavoriteItem(2, "movie", "Watched Horror", null, "cat")))
        )
        whenever(profileManager.currentProfileId()).thenReturn(1)

        // Trois entrées d'historique pour passer le démarrage à froid ; seules
        // les deux premières existent au catalogue et portent un genre.
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(
            (1..3).map {
                PlaybackPosition(streamId = it, positionMs = 1000L, durationMs = 5000L, lastAccessedAt = 0L, type = "movie")
            }
        )
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(
            listOf(
                VodStream(1, "Watched Comedy", "icon", "5", "0", "cat", genre = "Comedy"),
                VodStream(2, "Watched Horror", "icon", "5", "0", "cat", genre = "Horror"),
                VodStream(50, "Candidate Comedy", "icon", "5", "0", "cat", genre = "Comedy"),
                VodStream(51, "Candidate Horror", "icon", "5", "0", "cat", genre = "Horror")
            )
        )
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val useCase = GetRecommendationsUseCase(vodRepository, seriesRepository, categoryPreferenceRepository, profileManager, mediaRatingRepository, favoritesRepository, CoroutineScope(SupervisorJob()))
        val result = useCase(currentTimeMs = 1000L)

        assertEquals("Candidate Horror", result.movies.first().name)
    }
}
