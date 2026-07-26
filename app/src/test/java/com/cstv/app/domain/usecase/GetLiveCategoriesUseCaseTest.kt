package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class GetLiveCategoriesUseCaseTest {

    @Mock
    private lateinit var repository: LiveTvRepository

    @Mock
    private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private lateinit var useCase: GetLiveCategoriesUseCase

    // Ordre API par défaut : Sports, Général, Cinéma.
    private val categories = listOf(
        LiveCategory("2", "Sports", 0),
        LiveCategory("1", "Général", 0),
        LiveCategory("3", "Cinéma", 0)
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetLiveCategoriesUseCase(repository, categoryPreferenceRepository)
    }

    @Test
    fun test_invoke_returnsDefaultApiOrder_whenNoPreferences() = runTest {
        whenever(repository.getCachedLiveCategories()).thenReturn(categories)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(emptyMap())

        val result = useCase.once()

        assertEquals(listOf("Sports", "Général", "Cinéma"), result.map { it.categoryName })
    }

    @Test
    fun test_invoke_filtersHiddenCategories() = runTest {
        whenever(repository.getCachedLiveCategories()).thenReturn(categories)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf("1" to CategoryPreference("1", hidden = true, sortOrder = null))
        )

        val result = useCase.once()

        assertEquals(listOf("Sports", "Cinéma"), result.map { it.categoryName })
    }

    @Test
    fun test_invoke_appliesCustomOrder() = runTest {
        whenever(repository.getCachedLiveCategories()).thenReturn(categories)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf(
                "2" to CategoryPreference("2", hidden = false, sortOrder = 2),
                "1" to CategoryPreference("1", hidden = false, sortOrder = 1),
                "3" to CategoryPreference("3", hidden = false, sortOrder = 0)
            )
        )

        val result = useCase.once()

        assertEquals(listOf("Cinéma", "Général", "Sports"), result.map { it.categoryName })
    }

    @Test
    fun test_invoke_hiddenAndReordered_combined() = runTest {
        whenever(repository.getCachedLiveCategories()).thenReturn(categories)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf(
                "2" to CategoryPreference("2", hidden = true, sortOrder = 0),
                "1" to CategoryPreference("1", hidden = false, sortOrder = 2),
                "3" to CategoryPreference("3", hidden = false, sortOrder = 1)
            )
        )

        val result = useCase.once()

        assertEquals(listOf("Cinéma", "Général"), result.map { it.categoryName })
    }

    @Test
    fun test_invoke_partialPreferences_keepDefaultPositionForOthers() = runTest {
        // Seule "Cinéma" a un sortOrder (cas transitoire : saveOrder écrit
        // normalement toutes les catégories). Les autres gardent leur position
        // API ; à sortOrder égal, l'ordre API départage.
        whenever(repository.getCachedLiveCategories()).thenReturn(categories)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf("3" to CategoryPreference("3", hidden = false, sortOrder = 0))
        )

        val result = useCase.once()

        assertEquals(listOf("Sports", "Cinéma", "Général"), result.map { it.categoryName })
    }
}
