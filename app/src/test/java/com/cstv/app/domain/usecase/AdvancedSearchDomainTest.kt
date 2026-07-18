package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSearchDomainTest {

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private lateinit var getTopGenresUseCase: GetTopGenresUseCase
    private lateinit var getCategoriesForTypeUseCase: GetCategoriesForTypeUseCase
    private lateinit var advancedCatalogSearchUseCase: AdvancedCatalogSearchUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        getTopGenresUseCase = GetTopGenresUseCase(vodRepository, seriesRepository)
        getCategoriesForTypeUseCase = GetCategoriesForTypeUseCase(vodRepository, seriesRepository, categoryPreferenceRepository)
        advancedCatalogSearchUseCase = AdvancedCatalogSearchUseCase(vodRepository, seriesRepository, categoryPreferenceRepository)
    }

    @Test
    fun test_advancedSearchFilter_defaultAndState() {
        val filter = AdvancedSearchFilter.DEFAULT
        assertTrue(filter.isEmpty)
        assertFalse(filter.isActive)
        assertNull(filter.yearRange)
        assertNull(filter.mediaType)
        assertNull(filter.categoryId)
        assertNull(filter.minRating)
        assertTrue(filter.genres.isEmpty())

        // Modify minRating
        val ratedFilter = filter.copy(minRating = 7)
        assertFalse(ratedFilter.isEmpty)
        assertTrue(ratedFilter.isActive)

        // mediaType change resets categoryId
        val withTypeAndCat = filter.copy(mediaType = SearchMediaType.FILM, categoryId = "123")
        val withNewType = withTypeAndCat.withMediaType(SearchMediaType.SERIE)
        assertEquals(SearchMediaType.SERIE, withNewType.mediaType)
        assertNull(withNewType.categoryId)

        // same mediaType does not reset categoryId
        val withSameType = withTypeAndCat.withMediaType(SearchMediaType.FILM)
        assertEquals(SearchMediaType.FILM, withSameType.mediaType)
        assertEquals("123", withSameType.categoryId)
    }

    @Test
    fun test_getTopGenresUseCase_aggregatesCorrectly() = runTest {
        val movies = listOf(
            VodStream(1, "Movie A", null, null, null, "1", genre = "Action, Thriller, Inconnu"),
            VodStream(2, "Movie B", null, null, null, "1", genre = "Comédie, Action, -")
        )
        val series = listOf(
            SeriesStream(1, "Series A", null, null, null, "1", genre = "action, Sci-Fi, Inconnu"),
            SeriesStream(2, "Series B", null, null, null, "1", genre = "Thriller, COMÉDIE")
        )

        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(movies)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(series)

        val topGenres = getTopGenresUseCase()
        println("DEBUG: topGenres = $topGenres")

        // Occurrences (normalized):
        // action: 3 (Action, Action, action) -> Casing counts: "Action": 2, "action": 1. Best: "Action"
        // thriller: 2 (Thriller, Thriller) -> Best: "Thriller"
        // comédie: 2 (Comédie, COMÉDIE) -> Best: "Comédie"
        // sci-fi: 1 (Sci-Fi) -> Best: "Sci-Fi"
        // Total unique top genres should be sorted by frequency: action (3), thriller (2), comédie (2), sci-fi (1)
        assertEquals(4, topGenres.size)
        assertEquals("Action", topGenres[0])
        assertTrue(topGenres[1] in listOf("Thriller", "Comédie"))
        assertTrue(topGenres[2] in listOf("Thriller", "Comédie"))
        assertEquals("Sci-Fi", topGenres[3])
    }

    @Test
    fun test_getCategoriesForTypeUseCase_returnsCategoriesWithCountAndAllEntry() = runTest {
        val rawVodCategories = listOf(
            VodCategory("cat1", "Action", 0),
            VodCategory("cat2", "Comedy", 0),
            VodCategory("cat3", "Drama", 0)
        )
        val vodCounts = mapOf("cat1" to 10, "cat2" to 5, "cat3" to 8)

        whenever(vodRepository.getVodCategories(false)).thenReturn(rawVodCategories)
        whenever(vodRepository.getCategoryCounts()).thenReturn(vodCounts)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("cat3" to CategoryPreference("cat3", hidden = true, sortOrder = null))
        )

        val categories = getCategoriesForTypeUseCase(SearchMediaType.FILM)

        // "cat3" is hidden. So active: cat1 (10), cat2 (5)
        // Total count = 10 + 5 = 15
        assertEquals(3, categories.size)
        assertEquals(CategoryWithCount("all", "Toutes les catégories", 15), categories[0])
        assertEquals(CategoryWithCount("cat1", "Action", 10), categories[1])
        assertEquals(CategoryWithCount("cat2", "Comedy", 5), categories[2])
    }

    @Test
    fun test_advancedCatalogSearchUseCase_combinesFiltersCorrectly() = runTest {
        val vodList = listOf(
            VodStream(101, "Dragon Ball Z: Battle of Gods", null, "8.2", "2013", "cat1", genre = "Animation, Action", releaseYear = 2013),
            VodStream(102, "Super Movie", null, "5.5", "2020", "cat2", genre = "Action", releaseYear = 2020),
            VodStream(103, "Old Drama", null, "9.0", "1975", "cat1", genre = "Drama", releaseYear = 1975)
        )
        val seriesList = listOf(
            SeriesStream(201, "Dragon Ball Super", null, "8.0", "2015", "cat1", genre = "Animation, Adventure", releaseYear = 2015),
            SeriesStream(202, "Another Series", null, "6.1", "2018", "cat3", genre = "Comedy", releaseYear = 2018)
        )

        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(vodList)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(seriesList)
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        // 1. Query "Dragon Ball" with no other filter (MediaType = null)
        val filter1 = AdvancedSearchFilter.DEFAULT
        val result1 = advancedCatalogSearchUseCase("Dragon Ball", filter1)
        assertEquals(1, result1.vodResults.size)
        assertEquals("Dragon Ball Z: Battle of Gods", result1.vodResults[0].name)
        assertEquals(1, result1.seriesResults.size)
        assertEquals("Dragon Ball Super", result1.seriesResults[0].name)

        // 2. Query empty, but filter by FILM, minRating 6, yearRange 2010..2025, genres ["Action"]
        val filter2 = AdvancedSearchFilter(
            mediaType = SearchMediaType.FILM,
            categoryId = null,
            minRating = 6,
            yearRange = 2010..2025,
            genres = setOf("Action")
        )
        val result2 = advancedCatalogSearchUseCase("", filter2)
        assertEquals(1, result2.vodResults.size)
        assertEquals(101, result2.vodResults[0].streamId)
        assertTrue(result2.seriesResults.isEmpty())

        // 3. Query empty, MediaType SERIE, categoryId "cat1"
        val filter3 = AdvancedSearchFilter(
            mediaType = SearchMediaType.SERIE,
            categoryId = "cat1",
            minRating = null,
            yearRange = null,
            genres = emptySet()
        )
        val result3 = advancedCatalogSearchUseCase("", filter3)
        assertTrue(result3.vodResults.isEmpty())
        assertEquals(1, result3.seriesResults.size)
        assertEquals(201, result3.seriesResults[0].seriesId)
    }

    @Test
    fun test_defaultRange_doesNotExcludeUnknownOrOutOfBoundsYears() = runTest {
        // Non-régression : yearRange = null (DEFAULT) = "toutes les années",
        // pas de filtre appliqué. Ne doit pas filtrer les items non enrichis
        // (releaseYear null) ni les items anciens (1975), sinon le compteur
        // total serait faussé.
        val vodList = listOf(
            VodStream(1, "Not enriched yet", null, "8.0", null, "cat1", genre = null, releaseYear = null),
            VodStream(2, "Old classic", null, "8.0", "1975", "cat1", genre = "Drama", releaseYear = 1975),
            VodStream(3, "Recent", null, "8.0", "2020", "cat1", genre = "Action", releaseYear = 2020)
        )
        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(vodList)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val result = advancedCatalogSearchUseCase("", AdvancedSearchFilter.DEFAULT)
        assertEquals(3, result.vodResults.size)

        // En revanche, une plage resserrée exclut bien null et hors-plage.
        val narrowed = AdvancedSearchFilter.DEFAULT.copy(yearRange = 2000..2025)
        val narrowedResult = advancedCatalogSearchUseCase("", narrowed)
        assertEquals(1, narrowedResult.vodResults.size)
        assertEquals(3, narrowedResult.vodResults[0].streamId)
    }

    @Test
    fun test_multipleGenres_useAndLogicNotOr() = runTest {
        // "Action" seul matche 2 films, "Comédie" seul matche 2 films, mais
        // seul un film a LES DEUX -> la sélection combinée doit filtrer en ET.
        val vodList = listOf(
            VodStream(1, "Action only", null, null, null, "cat1", genre = "Action"),
            VodStream(2, "Comedy only", null, null, null, "cat1", genre = "Comédie"),
            VodStream(3, "Action + Comedy", null, null, null, "cat1", genre = "Action, Comédie"),
            VodStream(4, "Neither", null, null, null, "cat1", genre = "Drame")
        )
        whenever(vodRepository.getVodStreams(eq("all"), eq(false))).thenReturn(vodList)
        whenever(seriesRepository.getSeriesStreams(eq("all"), eq(false))).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())

        val filter = AdvancedSearchFilter.DEFAULT.copy(genres = setOf("Action", "Comédie"))
        val result = advancedCatalogSearchUseCase("", filter)

        assertEquals(1, result.vodResults.size)
        assertEquals(3, result.vodResults[0].streamId)
    }
}
