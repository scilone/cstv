package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.SeriesVersionPreferenceDao
import com.cstv.app.data.local.entity.SeriesVersionPreferenceEntity
import com.cstv.app.data.local.storage.ProfileManager
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

/** F39 §8.3, tâche 3 : écriture/lecture par (profileId, linkKey), délégation au DAO. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesVersionPreferenceRepositoryImplTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock
    private lateinit var dao: SeriesVersionPreferenceDao

    @Mock
    private lateinit var profileManager: ProfileManager

    private lateinit var repository: SeriesVersionPreferenceRepositoryImpl

    private val activeProfileId = 7

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(activeProfileId).whenever(profileManager).currentProfileId()
        repository = SeriesVersionPreferenceRepositoryImpl(dao, profileManager)
    }

    @Test
    fun `getPreferredSeriesId reads by profileId and linkKey`() = runTest {
        doReturn(42).whenever(dao).getPreferredSeriesId(activeProfileId, "key-a")

        assertEquals(42, repository.getPreferredSeriesId("key-a"))

        verify(dao).getPreferredSeriesId(activeProfileId, "key-a")
    }

    @Test
    fun `getPreferredSeriesId returns null when nothing is stored`() = runTest {
        doReturn(null).whenever(dao).getPreferredSeriesId(activeProfileId, "key-a")

        assertEquals(null, repository.getPreferredSeriesId("key-a"))
    }

    @Test
    fun `setPreference upserts an entity scoped to the active profile`() = runTest {
        repository.setPreference("key-a", preferredSeriesId = 42)

        argumentCaptor<SeriesVersionPreferenceEntity>().apply {
            verify(dao).upsert(capture())
            assertEquals(activeProfileId, firstValue.profileId)
            assertEquals("key-a", firstValue.linkKey)
            assertEquals(42, firstValue.preferredSeriesId)
        }
    }

    @Test
    fun `clearPreference deletes by profileId and linkKey`() = runTest {
        repository.clearPreference("key-a")

        verify(dao).delete(activeProfileId, "key-a")
    }
}
