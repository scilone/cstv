package com.cstv.app.domain.usecase

import com.cstv.app.data.repository.ContentClassificationRepository
import com.cstv.app.data.repository.MediaClassificationKind
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.DownloadRequestData
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.domain.model.ParentalAccessPolicy
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.DownloadRepository
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * F44, tâche 5 : `StartDownloadUseCase` est l'unique point qui crée
 * effectivement un `DownloadRequest` (§8.4/§8.7) — la garde parentale s'y
 * applique, sans aucune file d'attente pour retenter plus tard (§8.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartDownloadUseCaseTest {

    private val repository: DownloadRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val vodRepository: VodRepository = mock()
    private val seriesRepository: SeriesRepository = mock()
    private val classificationRepository: ContentClassificationRepository = mock()
    private val useCase = StartDownloadUseCase(
        repository, profileRepository, vodRepository, seriesRepository, classificationRepository, ParentalAccessPolicy()
    )

    private fun profile(maxAgeRating: Int?) = Profile(id = 1, name = "Nico", avatarId = 0, createdAt = 0L, maxAgeRating = maxAgeRating)

    private fun movieRequest() = DownloadRequestData(
        contentId = DownloadedItem.movieContentId(42), url = "https://x", type = DownloadedItem.TYPE_MOVIE,
        streamId = 42, seriesId = null, seasonNum = null, episodeNum = null,
        title = "Dune", subtitle = null, coverUrl = null, containerExtension = "mp4",
    )

    private fun episodeRequest() = DownloadRequestData(
        contentId = DownloadedItem.episodeContentId(99), url = "https://x", type = DownloadedItem.TYPE_EPISODE,
        streamId = 99, seriesId = 7, seasonNum = 1, episodeNum = 1,
        title = "Supergirl — S01E01", subtitle = "Pilot", coverUrl = null, containerExtension = "mp4",
    )

    @Test
    fun `an unbridged profile starts the download without querying classification`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(null)))

        val result = useCase(movieRequest())

        assertEquals(DownloadStartResult.Started, result)
        verify(repository).startDownload(movieRequest())
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `a bridged profile is refused for an over-the-limit movie, and no request is created`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(
            VodStream(streamId = 42, name = "Dune", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2021)
        )
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)).thenReturn(16)

        val result = useCase(movieRequest())

        assertEquals(DownloadStartResult.RequiresParentalPin(BlockReason.TOO_MATURE, "movie_42"), result)
        verify(repository, never()).startDownload(org.mockito.kotlin.any())
    }

    @Test
    fun `an unknown classification refuses the download defensively`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(
            VodStream(streamId = 42, name = "Dune", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2021)
        )
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)).thenReturn(null)

        val result = useCase(movieRequest())

        assertEquals(DownloadStartResult.RequiresParentalPin(BlockReason.UNCLASSIFIED, "movie_42"), result)
        verify(repository, never()).startDownload(org.mockito.kotlin.any())
    }

    @Test
    fun `a bridged profile refuses a movie missing from the local catalog`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(12)))
        whenever(vodRepository.getStreamById(42)).thenReturn(null)

        assertEquals(
            DownloadStartResult.RequiresParentalPin(BlockReason.UNCLASSIFIED, "movie_42"),
            useCase(movieRequest())
        )
        verify(repository, never()).startDownload(org.mockito.kotlin.any())
        verifyNoInteractions(classificationRepository)
    }

    @Test
    fun `an episode download is classified against its parent series, not the episode`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(10)))
        whenever(seriesRepository.getStreamById(7)).thenReturn(
            SeriesStream(seriesId = 7, name = "Supergirl", cover = null, rating = null, added = null, categoryId = "cat", releaseYear = 2015)
        )
        whenever(classificationRepository.classificationFor(MediaClassificationKind.SERIES, "Supergirl", 2015, 7)).thenReturn(16)

        val result = useCase(episodeRequest())

        assertEquals(DownloadStartResult.RequiresParentalPin(BlockReason.TOO_MATURE, "episode_99"), result)
    }

    @Test
    fun `a compatible classification starts the download`() = runTest {
        whenever(profileRepository.currentProfileId()).thenReturn(1)
        whenever(profileRepository.getProfiles()).thenReturn(listOf(profile(16)))
        whenever(vodRepository.getStreamById(42)).thenReturn(
            VodStream(streamId = 42, name = "Dune", streamIcon = null, rating = null, added = null, categoryId = "cat", releaseYear = 2021)
        )
        whenever(classificationRepository.classificationFor(MediaClassificationKind.MOVIE, "Dune", 2021, 42)).thenReturn(12)

        assertEquals(DownloadStartResult.Started, useCase(movieRequest()))
        verify(repository).startDownload(movieRequest())
    }
}
