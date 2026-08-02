package com.cstv.app.domain.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class DownloadRequestFactoryTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private fun vodDetails() = VodDetails(
        streamId = 123,
        name = "Inception",
        director = "Nolan",
        actors = "DiCaprio",
        releaseDate = "2010",
        genre = "Action",
        plot = "…",
        rating = "8.8",
        coverBig = "cover.jpg",
        containerExtension = "mkv"
    )

    private fun episode() = SeriesEpisode(
        id = 456,
        episodeNum = 3,
        title = "The One With The Test",
        containerExtension = "mp4",
        plot = "…",
        duration = "45:00",
        releaseDate = "2021",
        seasonNum = 2
    )

    @Test
    fun forMovie_buildsStableContentIdAndMetadata() {
        val data = DownloadRequestFactory.forMovie(vodDetails(), "http://host:8080", "user", "pass")

        assertEquals("movie_123", data.contentId)
        assertEquals(DownloadedItem.TYPE_MOVIE, data.type)
        assertEquals(123, data.streamId)
        assertNull(data.seriesId)
        assertEquals("Inception", data.title)
        assertNull(data.subtitle)
        assertEquals("cover.jpg", data.coverUrl)
        assertEquals("mkv", data.containerExtension)
        assertEquals("http://host:8080/movie/user/pass/123.mkv", data.url)
    }

    @Test
    fun forEpisode_buildsStableContentIdAndFormattedTitle() {
        val data = DownloadRequestFactory.forEpisode(
            episode(), seriesId = 99, seriesTitle = "Friends", cover = "s.jpg",
            baseUrl = "http://host:8080", username = "user", password = "pass"
        )

        assertEquals("episode_456", data.contentId)
        assertEquals(DownloadedItem.TYPE_EPISODE, data.type)
        assertEquals(456, data.streamId)
        assertEquals(99, data.seriesId)
        assertEquals(2, data.seasonNum)
        assertEquals(3, data.episodeNum)
        // Titre = « Série — SxxExx », sous-titre = nom d'épisode.
        assertEquals("Friends — S02E03", data.title)
        assertEquals("The One With The Test", data.subtitle)
        assertEquals("s.jpg", data.coverUrl)
        assertEquals("mp4", data.containerExtension)
        assertEquals("http://host:8080/series/user/pass/456.mp4", data.url)
    }

    @Test
    fun movieAndEpisodeContentIds_neverCollide() {
        // Un film 456 et un épisode 456 doivent avoir des clés distinctes.
        assertNotEquals(
            DownloadedItem.movieContentId(456),
            DownloadedItem.episodeContentId(456)
        )
    }
}
