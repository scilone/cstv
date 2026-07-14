package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.FavoritesDao
import com.poc.iptvxtream.data.local.dao.LiveTvDao
import com.poc.iptvxtream.data.local.dao.ProfileDao
import com.poc.iptvxtream.data.local.dao.VodDao
import com.poc.iptvxtream.data.local.entity.ProfileEntity
import com.poc.iptvxtream.data.local.storage.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest {

    @Mock private lateinit var profileDao: ProfileDao
    @Mock private lateinit var profileManager: ProfileManager
    @Mock private lateinit var favoritesDao: FavoritesDao
    @Mock private lateinit var vodDao: VodDao
    @Mock private lateinit var liveTvDao: LiveTvDao

    private lateinit var repository: ProfileRepositoryImpl

    private fun profile(id: Int, name: String = "P$id") =
        ProfileEntity(id = id, name = name, avatarId = 0, createdAt = id.toLong())

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = ProfileRepositoryImpl(profileDao, profileManager, favoritesDao, vodDao, liveTvDao)
    }

    @Test
    fun test_ensureInitialized_createsDefaultProfile_whenNoneExist() = runTest {
        // D'abord vide, puis contient le profil créé.
        whenever(profileDao.getAll())
            .thenReturn(emptyList())
            .thenReturn(listOf(profile(1, "Profil 1")))
        whenever(profileDao.insert(any())).thenReturn(1L)

        val result = repository.ensureInitialized()

        verify(profileDao).insert(argThat { name == "Profil 1" && avatarId == 0 })
        verify(profileManager).setActiveProfileId(1)
        assertEquals(1, result.size)
        assertEquals("Profil 1", result[0].name)
    }

    @Test
    fun test_ensureInitialized_fallsBackToFirst_whenActiveProfileInvalid() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5), profile(6)))
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentProfileId()

        repository.ensureInitialized()

        verify(profileManager).setActiveProfileId(5)
        verify(profileDao, never()).insert(any())
    }

    @Test
    fun test_ensureInitialized_keepsValidActiveProfile() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5), profile(6)))
        doReturn(6).whenever(profileManager).currentProfileId()

        repository.ensureInitialized()

        verify(profileManager, never()).setActiveProfileId(any())
    }

    @Test
    fun test_deleteProfile_refusesToDeleteLastRemainingProfile() = runTest {
        whenever(profileDao.count()).thenReturn(1)

        val deleted = repository.deleteProfile(5)

        assertFalse(deleted)
        verify(profileDao, never()).deleteById(any())
        verify(favoritesDao, never()).deleteAllForProfile(any())
    }

    @Test
    fun test_deleteProfile_removesProfileAndItsData_andSwitchesActive() = runTest {
        whenever(profileDao.count()).thenReturn(2)
        doReturn(5).whenever(profileManager).currentProfileId() // on supprime le profil actif
        whenever(profileDao.getAll()).thenReturn(listOf(profile(6)))

        val deleted = repository.deleteProfile(5)

        assertTrue(deleted)
        verify(favoritesDao).deleteAllForProfile(5)
        verify(vodDao).deleteAllPlaybackForProfile(5)
        verify(liveTvDao).deleteRecentlyWatchedForProfile(5)
        verify(profileDao).deleteById(5)
        // Bascule vers le profil restant.
        verify(profileManager).setActiveProfileId(6)
    }
}
