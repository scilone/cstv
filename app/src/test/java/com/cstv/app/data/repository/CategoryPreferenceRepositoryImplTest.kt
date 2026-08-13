package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.CategoryPreferenceRow
import com.cstv.app.data.local.dao.CategoryPreferenceValues
import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.entity.CategoryPreferenceEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
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

    @Mock
    private lateinit var categoryRefDao: CategoryRefDao

    @Mock
    private lateinit var accountKeyProvider: CurrentAccountKeyProvider

    private lateinit var repository: CategoryPreferenceRepositoryImpl

    private val profileId = 7
    private val accountKey = "account-key"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(profileId).whenever(profileManager).currentProfileId()
        doReturn(accountKey).whenever(accountKeyProvider).current()
        repository = CategoryPreferenceRepositoryImpl(dao, profileManager, categoryRefDao, accountKeyProvider)
    }

    @Test
    fun test_getPreferences_mapsEntitiesByCategoryId_forActiveProfile() = runTest {
        whenever(dao.getForProfile(profileId, accountKey, "live")).thenReturn(
            listOf(
                CategoryPreferenceRow("1", "live", hidden = true, sortOrder = null),
                CategoryPreferenceRow("2", "live", hidden = false, sortOrder = 3)
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
        whenever(dao.get(profileId, accountKey, "vod", "5")).thenReturn(CategoryPreferenceValues(hidden = false, sortOrder = 2))
        whenever(categoryRefDao.resolve(accountKey, "vod", "5")).thenReturn(55L)

        repository.setHidden(CategoryType.VOD, "5", hidden = true)

        verify(dao).upsert(CategoryPreferenceEntity(profileId = profileId, catUid = 55L, hidden = true, sortOrder = 2))
    }

    @Test
    fun test_setHidden_createsEntry_whenNoneExists() = runTest {
        whenever(dao.get(profileId, accountKey, "series", "9")).thenReturn(null)
        whenever(categoryRefDao.resolve(accountKey, "series", "9")).thenReturn(90L)

        repository.setHidden(CategoryType.SERIES, "9", hidden = true)

        verify(dao).upsert(CategoryPreferenceEntity(profileId = profileId, catUid = 90L, hidden = true, sortOrder = null))
    }

    @Test
    fun test_saveOrder_writesSortOrderByIndex_andPreservesHiddenFlags() = runTest {
        whenever(dao.getForProfile(profileId, accountKey, "live")).thenReturn(
            listOf(CategoryPreferenceRow("b", "live", hidden = true, sortOrder = 0))
        )
        whenever(categoryRefDao.resolve(eq(accountKey), eq("live"), any())).thenAnswer { invocation ->
            when (invocation.getArgument<String>(2)) { "a" -> 10L; "b" -> 20L; else -> 30L }
        }

        repository.saveOrder(CategoryType.LIVE, listOf("a", "b", "c"))

        verify(dao).upsertAll(
            listOf(
                CategoryPreferenceEntity(profileId = profileId, catUid = 10L, hidden = false, sortOrder = 0),
                CategoryPreferenceEntity(profileId = profileId, catUid = 20L, hidden = true, sortOrder = 1),
                CategoryPreferenceEntity(profileId = profileId, catUid = 30L, hidden = false, sortOrder = 2)
            )
        )
    }
}
