package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TmdbCatalogMatcherTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


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
    fun findBestMatches_requiresExactYear_andRejectsPlusOrMinusOne() {
        listOf(2023, 2025).forEach { iptvYear ->
            val match = TmdbCatalogMatcher.findBestMatches(
                tmdbTitle = "Dune",
                tmdbYear = 2024,
                catalog = TmdbCatalogMatcher.prepareMovies(listOf(movie(id = iptvYear, name = "Dune", year = iptvYear))),
                excludedIds = emptySet()
            )

            assertNull(match)
        }

        val exact = movie(id = 2024, name = "Dune", year = 2024)
        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = 2024,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(exact)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(exact), match?.candidates)
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, match?.yearRank)
    }

    @Test
    fun findBestMatches_rejectsUndatedCandidate_whenTmdbYearIsKnown() {
        // Bug B15 : « Odyssée » non enrichie (année inconnue) ne doit plus être
        // proposée comme tendance « Odyssey 2026 ».
        val undated = movie(id = 1, name = "Odyssee", year = null)
        val zeroYear = movie(id = 2, name = "Odyssee", year = 0)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Odyssey",
            tmdbYear = 2026,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(undated, zeroYear)),
            excludedIds = emptySet()
        )

        assertNull(match)
    }

    @Test
    fun findBestMatches_acceptsAnyYearWhenTmdbYearIsUnknown() {
        val dated = movie(id = 1, name = "Dune", year = 1984)

        val match = TmdbCatalogMatcher.findBestMatches(
            tmdbTitle = "Dune",
            tmdbYear = null,
            catalog = TmdbCatalogMatcher.prepareMovies(listOf(dated)),
            excludedIds = emptySet()
        )

        assertEquals(listOf(dated), match?.candidates)
        assertEquals(TmdbCatalogMatcher.YearRank.UNKNOWN, match?.yearRank)
    }

    @Test
    fun findBestMatches_rejectsZeroYearInDirectCatalogCandidate() {
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

        assertNull(match)
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
    fun findBestMatches_keepsOnlyDatedCandidateAmongHomonyms_regardlessOfCatalogOrder() {
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
        assertEquals(TmdbCatalogMatcher.YearRank.EXACT, unenrichedFirst?.yearRank)
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

    @Test
    fun prepareMovies_readsYearFromTitle_whenEnrichmentHasNotFilledIt() {
        val fromTitle = movie(id = 1, name = "[FR] Odyssée (2016) 1080p", year = null)

        val prepared = TmdbCatalogMatcher.prepareMovies(listOf(fromTitle))

        assertEquals(2016, prepared.single().releaseYear)
        assertNull(
            TmdbCatalogMatcher.findBestMatches(
                tmdbTitle = "Odyssey",
                tmdbYear = 2026,
                catalog = prepared,
                excludedIds = emptySet()
            )
        )
        assertEquals(
            listOf(fromTitle),
            TmdbCatalogMatcher.findBestMatches(
                tmdbTitle = "Odyssée",
                tmdbYear = 2016,
                catalog = prepared,
                excludedIds = emptySet()
            )?.candidates
        )
    }

    @Test
    fun prepareMovies_prefersEnrichedYearOverTitleYear() {
        val enriched = movie(id = 1, name = "Blade Runner 2049", year = 2017)

        assertEquals(2017, TmdbCatalogMatcher.prepareMovies(listOf(enriched)).single().releaseYear)
    }

    @Test
    fun yearFromTitle_ignoresTitlesThatAreThemselvesAYear() {
        assertNull(TmdbCatalogMatcher.yearFromTitle("1917"))
        assertNull(TmdbCatalogMatcher.yearFromTitle("[FR] 2012 1080p"))
        assertNull(TmdbCatalogMatcher.yearFromTitle("Sans année"))
        assertEquals(2016, TmdbCatalogMatcher.yearFromTitle("Odyssée (2016)"))
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
