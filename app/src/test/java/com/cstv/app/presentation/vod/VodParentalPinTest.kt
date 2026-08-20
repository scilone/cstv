package com.cstv.app.presentation.vod

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.security.FakeSharedPreferences
import com.cstv.app.data.security.ParentalPinStore
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.OneShotPlaybackGrantStore
import com.cstv.app.domain.model.ParentalPinFeedback
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.TrackPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.usecase.*
import com.cstv.app.domain.util.MonotonicClock
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * F44, tâche 6 : l'écran de refus (état exposé par `VodState`) affiche les
 * deux raisons distinctement et reflète la temporisation du PIN store, sans
 * détail exploitable sur la cause exacte de l'échec (§8.6/§8.8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodParentalPinTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var canPlayContentUseCase: CanPlayContentUseCase
    @Mock private lateinit var observeCatalogStatusUseCase: ObserveCatalogStatusUseCase
    @Mock private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager
    @Mock private lateinit var getVodCategoriesUseCase: GetVodCategoriesUseCase
    @Mock private lateinit var getVodCategoryCountsUseCase: GetVodCategoryCountsUseCase
    @Mock private lateinit var getVodStreamsUseCase: GetVodStreamsUseCase
    @Mock private lateinit var getVodDetailsUseCase: GetVodDetailsUseCase
    @Mock private lateinit var getRelatedMoviesUseCase: GetRelatedMoviesUseCase
    @Mock private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
    @Mock private lateinit var credentialsManager: CredentialsManager
    @Mock private lateinit var settingsManager: SettingsManager
    @Mock private lateinit var trackPreferenceRepository: TrackPreferenceRepository
    @Mock private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository
    @Mock private lateinit var vodRepository: VodRepository
    @Mock private lateinit var removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase
    @Mock private lateinit var mediaRatingRepository: com.cstv.app.domain.repository.MediaRatingRepository
    @Mock private lateinit var setMediaRatingUseCase: SetMediaRatingUseCase
    @Mock private lateinit var getTrailerPreviewUseCase: GetTrailerPreviewUseCase
    @Mock private lateinit var invalidateTrailerPreviewUseCase: InvalidateTrailerPreviewUseCase
    @Mock private lateinit var getRecommendationsUseCase: GetRecommendationsUseCase
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var parentalAuthorizationRepository: com.cstv.app.domain.repository.ParentalAuthorizationRepository

    private val testDispatcher = StandardTestDispatcher()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 0L }
    private val monotonicClock = object : MonotonicClock { override fun elapsedRealtimeMillis() = 0L }
    private lateinit var pinStore: ParentalPinStore
    private lateinit var parentalUnlockUseCase: ParentalUnlockUseCase
    private lateinit var viewModel: VodViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@VodParentalPinTest)
        Dispatchers.setMain(testDispatcher)

        whenever(getVodCategoriesUseCase()).thenReturn(flowOf(emptyList()))
        whenever(getVodStreamsUseCase(any())).thenReturn(flowOf(emptyList()))
        whenever(categoryPreferenceRepository.changes).thenReturn(flowOf(Unit))
        whenever(observeCatalogStatusUseCase()).thenReturn(flowOf(com.cstv.app.domain.sync.CatalogStatus()))
        whenever(catalogSyncManager.syncState)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.cstv.app.domain.sync.SyncState.Idle))
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(vodRepository.getCachedVodStreams(any())).thenReturn(emptyList())
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(profileRepository.currentProfileId()).thenReturn(1)

        pinStore = ParentalPinStore(FakeSharedPreferences(), timeProvider, monotonicClock)
        parentalUnlockUseCase = ParentalUnlockUseCase(pinStore, OneShotPlaybackGrantStore(), profileRepository, parentalAuthorizationRepository)

        viewModel = VodViewModel(
            getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase, getVodDetailsUseCase,
            getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager, settingsManager,
            trackPreferenceRepository, categoryPreferenceRepository, vodRepository, removeFromContinueWatchingUseCase,
            mediaRatingRepository, setMediaRatingUseCase, observeCatalogStatusUseCase, catalogSyncManager,
            canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase,
            testDispatcher,
            markPlaybackSyncUseCase = null, requestPlaybackLockUseCase = null, releasePlaybackLockUseCase = null,
            playbackLockManager = null, playbackRepairRepository = null,
            parentalUnlockUseCase = parentalUnlockUseCase,
        )
        runCurrent()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `TOO_MATURE and UNCLASSIFIED surface distinct refusal states`() = runTest(testDispatcher) {
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1"))
        whenever(canPlayContentUseCase("movie_2"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.UNCLASSIFIED, "movie_2"))

        viewModel.requestPlayback(1) {}
        runCurrent()
        assertEquals(BlockReason.TOO_MATURE, viewModel.state.value.parentalPinRequest?.reason)

        viewModel.consumeParentalPinRequest()
        viewModel.requestPlayback(2) {}
        runCurrent()
        assertEquals(BlockReason.UNCLASSIFIED, viewModel.state.value.parentalPinRequest?.reason)
    }

    @Test
    fun `a correct pin unlocks and triggers the pending playback callback`() = runTest(testDispatcher) {
        pinStore.createPin("1234")
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1"))
        whenever(canPlayContentUseCase(eq("movie_1"), any()))
            .thenReturn(PlaybackAvailability.Allowed)

        var playbackStarted = false
        viewModel.requestPlayback(1) { playbackStarted = true }
        runCurrent()
        assertNotNull(viewModel.state.value.parentalPinRequest)

        viewModel.submitParentalPin("1234")
        runCurrent()

        assertNull(viewModel.state.value.parentalPinRequest)
        assertTrue(playbackStarted)
    }

    @Test
    fun `an incorrect pin gives generic feedback, never which part failed`() = runTest(testDispatcher) {
        pinStore.createPin("1234")
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1"))

        viewModel.requestPlayback(1) {}
        runCurrent()
        viewModel.submitParentalPin("0000")
        runCurrent()

        assertEquals(ParentalPinFeedback.Incorrect, viewModel.state.value.parentalPinFeedback)
        // Le refus reste actif : aucun contournement par simple mauvais essai.
        assertNotNull(viewModel.state.value.parentalPinRequest)
    }

    @Test
    fun `the pin store lockout is reflected as timed feedback in the UI state`() = runTest(testDispatcher) {
        pinStore.createPin("1234")
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1"))
        viewModel.requestPlayback(1) {}
        runCurrent()

        repeat(5) { viewModel.submitParentalPin("0000"); runCurrent() }

        val feedback = viewModel.state.value.parentalPinFeedback
        assertTrue(feedback is ParentalPinFeedback.Locked)
        assertEquals(ParentalPinStore.INITIAL_LOCK_MS, (feedback as ParentalPinFeedback.Locked).remainingMillis)
    }

    @Test
    fun `F45 - a correct pin with remember checked persists a permanent authorization`() = runTest(testDispatcher) {
        pinStore.createPin("1234")
        val target = ParentalAuthorizationTarget(com.cstv.app.data.repository.MediaClassificationKind.MOVIE, 1)
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1", authorizationTarget = target))
        whenever(canPlayContentUseCase(eq("movie_1"), any()))
            .thenReturn(PlaybackAvailability.Allowed)

        viewModel.requestPlayback(1) {}
        runCurrent()
        viewModel.submitParentalPin("1234", remember = true)
        runCurrent()

        verify(parentalAuthorizationRepository).authorize(1, com.cstv.app.data.repository.MediaClassificationKind.MOVIE, 1)
    }

    @Test
    fun `F45 - a correct pin without remember checked persists nothing`() = runTest(testDispatcher) {
        pinStore.createPin("1234")
        val target = ParentalAuthorizationTarget(com.cstv.app.data.repository.MediaClassificationKind.MOVIE, 1)
        whenever(canPlayContentUseCase("movie_1"))
            .thenReturn(PlaybackAvailability.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_1", authorizationTarget = target))
        whenever(canPlayContentUseCase(eq("movie_1"), any()))
            .thenReturn(PlaybackAvailability.Allowed)

        viewModel.requestPlayback(1) {}
        runCurrent()
        viewModel.submitParentalPin("1234")
        runCurrent()

        verifyNoInteractions(parentalAuthorizationRepository)
    }
}
