package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.repository.TrailerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetTrailerPreviewUseCaseTest {

    @Test
    fun invokesRepositoryForTheRequestedMedia() = runTest {
        val repository = mock<TrailerRepository>()
        val media = TrailerMedia.Movie(catalogId = 42, tmdbId = 27205)
        val expected = TrailerPreview(media, TrailerSource.YouTube("dQw4w9WgXcQ"))
        whenever(repository.getTrailerPreview(media)).thenReturn(expected)

        assertEquals(expected, GetTrailerPreviewUseCase(repository)(media))
    }
}
