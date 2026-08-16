package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.repository.TrailerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetTrailerPreviewUseCaseTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun invokesRepositoryForTheRequestedMedia() = runTest {
        val repository = mock<TrailerRepository>()
        val media = TrailerMedia.Movie(catalogId = 42, canonicalId = "movie:27205")
        val expected = TrailerPreview(media, TrailerSource.YouTube("dQw4w9WgXcQ"))
        whenever(repository.getTrailerPreview(media)).thenReturn(expected)

        assertEquals(expected, GetTrailerPreviewUseCase(repository)(media))
    }
}
