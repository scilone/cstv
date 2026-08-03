package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFacetsBuilderTest {

    private data class Item(val genre: String?, val year: Int?, val rating: String?)

    private fun build(items: List<Item>, filter: AdvancedSearchFilter = AdvancedSearchFilter.DEFAULT) =
        CatalogFacetsBuilder.build(
            items = items,
            genreOf = { it.genre },
            yearOf = { it.year },
            ratingOf = { it.rating },
            filter = filter
        )

    @Test
    fun `genres dedupliques sans tenir compte de la casse et tries`() {
        val facets = build(
            listOf(
                Item("Thriller, Action", 2020, "7.0"),
                Item("action/Aventure", 2021, "8.0")
            )
        )
        // La première graphie rencontrée est conservée, la casse ne crée pas de doublon.
        assertEquals(listOf("Action", "Aventure", "Thriller"), facets.genres)
    }

    @Test
    fun `les valeurs de remplissage ne deviennent pas des genres`() {
        val facets = build(listOf(Item("Inconnu", 2020, null), Item("N/A", 2020, null)))
        assertEquals(emptyList<String>(), facets.genres)
    }

    @Test
    fun `amplitude d annees bornee par les extremes reels`() {
        val facets = build(
            listOf(
                Item(null, 1997, null),
                Item(null, null, null),
                Item(null, 2019, null)
            )
        )
        assertEquals(1997..2019, facets.yearRange)
    }

    @Test
    fun `amplitude par defaut quand aucune annee n est connue`() {
        val facets = build(listOf(Item("Action", null, null)))
        assertEquals(CatalogFacetsBuilder.DEFAULT_YEAR_RANGE, facets.yearRange)
    }

    @Test
    fun `le compte filtre suit le filtre courant`() {
        val items = listOf(
            Item("Action", 2020, "8.0"),
            Item("Action", 1990, "8.0"),
            Item("Comédie", 2020, "8.0")
        )
        val facets = build(items, AdvancedSearchFilter.DEFAULT.copy(genres = setOf("Action")))
        assertEquals(2, facets.filteredCount)
    }

    @Test
    fun `liste vide rend des facettes neutres`() {
        val facets = build(emptyList())
        assertEquals(emptyList<String>(), facets.genres)
        assertEquals(CatalogFacetsBuilder.DEFAULT_YEAR_RANGE, facets.yearRange)
        assertEquals(0, facets.filteredCount)
    }
}
