package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbCatalogMatcherTest {

    @Test
    fun isStrictlyBetterScore_treatsDifferenceWithinEpsilonAsEqual() {
        assertEquals(false, TmdbCatalogMatcher.isStrictlyBetterScore(1.0 + 0.5e-9, 1.0))
        assertEquals(true, TmdbCatalogMatcher.isStrictlyBetterScore(1.0 + 2e-9, 1.0))
    }

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
        assertEquals(TmdbCatalogMatcher.YearRank.UNKNOWN, localYearUnknown?.yearRank)
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

        assertEquals(listOf(dated2021, undated), unenrichedFirst?.candidates)
        assertEquals(listOf(dated2021, undated), unenrichedLast?.candidates)
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, unenrichedFirst?.yearRank)
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, unenrichedLast?.yearRank)
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

        assertEquals(listOf(toleratedYear, undated), match?.candidates)
        assertEquals(TmdbCatalogMatcher.YearRank.TOLERATED, match?.yearRank)
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
        assertEquals(TmdbCatalogMatcher.YearRank.UNKNOWN, match?.yearRank)
    }

    @Test
    fun findBestMatches_prefersExactYearOverToleratedYear_regardlessOfCatalogOrder() {
        val exact = movie(id = 1, name = "Dune", year = 2021)
        val tolerated = movie(id = 2, name = "Dune", year = 2022)

        val toleratedFirst = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(tolerated, exact)),
            excludedIds = emptySet()
        )
        val exactFirst = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2021,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(exact, tolerated)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(exact, tolerated), toleratedFirst?.candidates)
        assertEquals(listOf(exact, tolerated), exactFirst?.candidates)
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, toleratedFirst?.yearRank)
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, exactFirst?.yearRank)
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
