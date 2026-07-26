package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbCatalogMatcherTest {

    @Test
    fun findBestMatches_rejectsRemakeWithIncompatibleYear_andFindsCorrectVersion() {
        val oldDune = movie(id = 1, name = "Dune", year = 1984)
        val newDune = movie(id = 2, name = "Dune", year = 2021)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(oldDune, newDune)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(newDune), match?.candidates)
    }

    @Test
    fun findBestMatches_acceptsYearDifferencesFromMinusOneToPlusOne() {
        listOf(2023, 2024, 2025).forEach { iptvYear ->
            val movie = movie(id = iptvYear, name = "Dune", year = iptvYear)

            val match = TmdbCatalogMatcher.findBestMatches(
                tmdbTitle = "Dune",
                tmdbYear = 2024,
                catalog = TmdbCatalogMatcher.prepareMovies(listOf(movie)),
                excludedIds = emptySet()
            )

            assertEquals(listOf(movie), match?.candidates)
        }
    }

    @Test
    fun findBestMatches_acceptsUnknownYearFromEitherSource() {
        val movieWithUnknownYear = movie(id = 1, name = "Dune", year = 0)
        val prepared = TmdbCatalogMatcher.prepareMovies(listOf(movieWithUnknownYear))

        val localYearUnknown = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = prepared,
            excludedIds = emptySet()
        )
        val tmdbYearUnknown = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = null,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(movie(id = 2, name = "Dune", year = 1984))),
            excludedIds = emptySet()
        )

        assertEquals(listOf(movieWithUnknownYear), localYearUnknown?.candidates)
        assertEquals(1, tmdbYearUnknown?.candidates?.size)
    }

    @Test
    fun findBestMatches_acceptsZeroYearInDirectCatalogCandidate() {
        val movie = movie(id = 1, name = "Dune", year = 0)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = listOf(
                TmdbCatalogMatcher.CatalogCandidate(
                    item = movie,
                    id = movie.streamId,
                    normalizedTitle = TitleNormalizer.normalize(movie.name),
                    releaseYear = 0
                )
            ),
            excludedIds = emptySet()
        )

        assertEquals(listOf(movie), match?.candidates)
    }

    @Test
    fun findBestMatches_appliesYearFilteringToSeries() {
        val oldDune = series(id = 1, name = "Dune", year = 1984)
        val newDune = series(id = 2, name = "Dune", year = 2021)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareSeries(listOf(oldDune, newDune)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(newDune), match?.candidates)
    }

    @Test
    fun findBestMatches_keepsCatalogOrderForEqualScores() {
        val first = movie(id = 1, name = "Dune", year = 2021)
        val second = movie(id = 2, name = "Dune", year = 2021)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(first, second)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(first, second), match?.candidates)
    }

    @Test
    fun findBestMatches_prefersDatedCandidateOverUnenrichedHomonym_regardlessOfCatalogOrder() {
        val undated = movie(id = 1, name = "Dune", year = null)
        val dated2021 = movie(id = 2, name = "Dune", year = 2021)

        val unenrichedFirst = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(undated, dated2021)),
            excludedIds = emptySet()
        )
        val unenrichedLast = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(dated2021, undated)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(dated2021), unenrichedFirst?.candidates)
        assertEquals(listOf(dated2021), unenrichedLast?.candidates)
    }

    @Test
    fun findBestMatches_prefersToleratedYearOverUnknownYearFallback() {
        val undated = movie(id = 1, name = "Dune", year = null)
        val toleratedYear = movie(id = 2, name = "Dune", year = 2022)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(undated, toleratedYear)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(toleratedYear), match?.candidates)
    }

    @Test
    fun findBestMatches_keepsUnknownYearFallbackWhenNoDatedCandidateExists() {
        val undated = movie(id = 1, name = "Dune", year = null)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(undated)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(undated), match?.candidates)
    }

    @Test
    fun findBestMatches_returnsNullWhenOnlyYearIsIncompatible() {
        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(movie(id = 1, name = "Dune", year = 1984))),
            excludedIds = emptySet()
        )

        assertNull(match)
    }

    private fun movie(id: Int, name: String, year: Int?) = VodStream(
        streamId = id,
        name = name,
        streamIcon = null,
        rating = null,
        added = null,
        categoryId = "movies",
        releaseYear = year
    )

    private fun series(id: Int, name: String, year: Int?) = SeriesStream(
        seriesId = id,
        name = name,
        cover = null,
        rating = null,
        added = null,
        categoryId = "series",
        releaseYear = year
    )
}
