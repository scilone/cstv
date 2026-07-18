package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GetCatalogYearRangeUseCaseTest {

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    private lateinit var useCase: GetCatalogYearRangeUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetCatalogYearRangeUseCase(vodRepository, seriesRepository)
    }

    @Test
    fun invoke_combinesVodAndSeriesBounds() = runTest {
        whenever(vodRepository.getReleaseYearBounds()).thenReturn(2015 to 2023)
        whenever(seriesRepository.getReleaseYearBounds()).thenReturn(1990 to 2018)

        val result = useCase()

        assertEquals(1990..2023, result)
    }

    @Test
    fun invoke_onlyVodHasData_usesVodBounds() = runTest {
        whenever(vodRepository.getReleaseYearBounds()).thenReturn(2000 to 2010)
        whenever(seriesRepository.getReleaseYearBounds()).thenReturn(null)

        val result = useCase()

        assertEquals(2000..2010, result)
    }

    @Test
    fun invoke_noDataAtAll_returnsFallback() = runTest {
        whenever(vodRepository.getReleaseYearBounds()).thenReturn(null)
        whenever(seriesRepository.getReleaseYearBounds()).thenReturn(null)

        val result = useCase()

        assertEquals(1980..2025, result)
    }

    @Test
    fun invoke_repositoryThrows_treatsAsNoData() = runTest {
        whenever(vodRepository.getReleaseYearBounds()).thenThrow(RuntimeException("boom"))
        whenever(seriesRepository.getReleaseYearBounds()).thenReturn(2005 to 2012)

        val result = useCase()

        assertEquals(2005..2012, result)
    }
}
