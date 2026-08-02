package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.entity.CategoryPreferenceEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.CategoryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryPreferenceRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var dao: CategoryPreferenceDao

    @Mock
    private lateinit var profileManager: ProfileManager

    private lateinit var repository: CategoryPreferenceRepositoryImpl

    private val profileId = 7

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(profileId).whenever(profileManager).currentProfileId()
        repository = CategoryPreferenceRepositoryImpl(dao, profileManager)
    }

    @Test
    fun test_getPreferences_mapsEntitiesByCategoryId_forActiveProfile() = runTest {
        whenever(dao.getForProfile("live", profileId)).thenReturn(
            listOf(
                CategoryPreferenceEntity("1", "live", profileId, hidden = true, sortOrder = null),
                CategoryPreferenceEntity("2", "live", profileId, hidden = false, sortOrder = 3)
            )
        )

        val result = repository.getPreferences(CategoryType.LIVE)

        assertEquals(2, result.size)
        assertEquals(true, result["1"]?.hidden)
        assertEquals(null, result["1"]?.sortOrder)
        assertEquals(false, result["2"]?.hidden)
        assertEquals(3, result["2"]?.sortOrder)
    }

    @Test
    fun test_setHidden_preservesExistingSortOrder() = runTest {
        whenever(dao.get("5", "vod", profileId)).thenReturn(
            CategoryPreferenceEntity("5", "vod", profileId, hidden = false, sortOrder = 2)
        )

        repository.setHidden(CategoryType.VOD, "5", hidden = true)

        verify(dao).upsert(
            CategoryPreferenceEntity("5", "vod", profileId, hidden = true, sortOrder = 2)
        )
    }

    @Test
    fun test_setHidden_createsEntry_whenNoneExists() = runTest {
        whenever(dao.get("9", "series", profileId)).thenReturn(null)

        repository.setHidden(CategoryType.SERIES, "9", hidden = true)

        verify(dao).upsert(
            CategoryPreferenceEntity("9", "series", profileId, hidden = true, sortOrder = null)
        )
    }

    @Test
    fun test_saveOrder_writesSortOrderByIndex_andPreservesHiddenFlags() = runTest {
        whenever(dao.getForProfile("live", profileId)).thenReturn(
            listOf(CategoryPreferenceEntity("b", "live", profileId, hidden = true, sortOrder = 0))
        )

        repository.saveOrder(CategoryType.LIVE, listOf("a", "b", "c"))

        verify(dao).upsertAll(
            listOf(
                CategoryPreferenceEntity("a", "live", profileId, hidden = false, sortOrder = 0),
                CategoryPreferenceEntity("b", "live", profileId, hidden = true, sortOrder = 1),
                CategoryPreferenceEntity("c", "live", profileId, hidden = false, sortOrder = 2)
            )
        )
    }
}
