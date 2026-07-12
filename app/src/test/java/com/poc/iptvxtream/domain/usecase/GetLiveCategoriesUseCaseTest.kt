package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.repository.LiveTvRepository
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
    private lateinit var settingsManager: SettingsManager

    private lateinit var useCase: GetLiveCategoriesUseCase

    private val categories = listOf(
        LiveCategory("2", "Sports", 0),
        LiveCategory("1", "Général", 0),
        LiveCategory("3", "Cinéma", 0)
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetLiveCategoriesUseCase(repository, settingsManager)
    }

    @Test
    fun test_invoke_returnsDefaultApiOrder_whenSortingSettingIsDefault() = runTest {
        whenever(settingsManager.getTvCategorySorting()).thenReturn(CategorySorting.DEFAULT)
        whenever(repository.getLiveCategories(any())).thenReturn(categories)

        val result = useCase(forceRefresh = false)

        // Result should be returned in original/API order
        assertEquals(3, result.size)
        assertEquals("Sports", result[0].categoryName)
        assertEquals("Général", result[1].categoryName)
        assertEquals("Cinéma", result[2].categoryName)
    }

    @Test
    fun test_invoke_returnsAlphabeticalOrder_whenSortingSettingIsAlphabetical() = runTest {
        whenever(settingsManager.getTvCategorySorting()).thenReturn(CategorySorting.ALPHABETICAL)
        whenever(repository.getLiveCategories(any())).thenReturn(categories)

        val result = useCase(forceRefresh = false)

        // Result should be sorted alphabetically
        assertEquals(3, result.size)
        assertEquals("Cinéma", result[0].categoryName)
        assertEquals("Général", result[1].categoryName)
        assertEquals("Sports", result[2].categoryName)
    }
}
