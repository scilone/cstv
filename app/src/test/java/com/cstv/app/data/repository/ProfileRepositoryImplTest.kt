package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.FavoritesDao
import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.local.storage.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock private lateinit var profileDao: ProfileDao
    @Mock private lateinit var profileManager: ProfileManager
    @Mock private lateinit var favoritesDao: FavoritesDao
    @Mock private lateinit var vodDao: VodDao
    @Mock private lateinit var liveTvDao: LiveTvDao
    @Mock private lateinit var trackPreferenceDao: com.cstv.app.data.local.dao.TrackPreferenceDao
    @Mock private lateinit var categoryPreferenceDao: com.cstv.app.data.local.dao.CategoryPreferenceDao
    @Mock private lateinit var mediaRatingDao: com.cstv.app.data.local.dao.MediaRatingDao
    @Mock private lateinit var seriesWatchStateDao: com.cstv.app.data.local.dao.SeriesWatchStateDao

    private lateinit var repository: ProfileRepositoryImpl

    private fun profile(id: Int, name: String = "P$id") =
        ProfileEntity(id = id, name = name, avatarId = 0, createdAt = id.toLong())

    /**
     * Fake à état réel (par opposition au mock d'interactions ci-dessus) : seul
     * moyen de vérifier la valeur *courante* après une écriture, requis par la
     * review F31-R1 pour prouver qu'un remplacement A → B ne laisse jamais deux
     * profils marqués ni un état intermédiaire observable.
     */
    private class FakeProfileManager(
        initialActive: Int = ProfileManager.NO_PROFILE,
        initialAutoStart: Int = ProfileManager.NO_PROFILE
    ) : ProfileManager {
        private val _activeProfileId = MutableStateFlow(initialActive)
        override val activeProfileId: StateFlow<Int> = _activeProfileId.asStateFlow()
        override fun currentProfileId(): Int = _activeProfileId.value
        override fun setActiveProfileId(id: Int) { _activeProfileId.value = id }

        private val _autoStartProfileId = MutableStateFlow(initialAutoStart)
        override val autoStartProfileId: StateFlow<Int> = _autoStartProfileId.asStateFlow()
        override fun currentAutoStartProfileId(): Int = _autoStartProfileId.value
        override fun setAutoStartProfileId(id: Int) { _autoStartProfileId.value = id }
    }

    private fun repositoryWith(fakeManager: ProfileManager) = ProfileRepositoryImpl(
        profileDao, fakeManager, favoritesDao, vodDao, liveTvDao,
        trackPreferenceDao, categoryPreferenceDao, mediaRatingDao, seriesWatchStateDao
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = ProfileRepositoryImpl(profileDao, profileManager, favoritesDao, vodDao, liveTvDao, trackPreferenceDao, categoryPreferenceDao, mediaRatingDao, seriesWatchStateDao)
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
        verify(trackPreferenceDao).deleteAllForProfile(5)
        verify(seriesWatchStateDao).deleteAllForProfile(5)
        verify(profileDao).deleteById(5)
        // Bascule vers le profil restant.
        verify(profileManager).setActiveProfileId(6)
    }

    @Test
    fun test_deleteProfile_cleansAutoStart_whenDeletedProfileWasDesignated() = runTest {
        whenever(profileDao.count()).thenReturn(2)
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentProfileId()
        doReturn(5).whenever(profileManager).currentAutoStartProfileId()
        whenever(profileDao.getAll()).thenReturn(listOf(profile(6)))

        repository.deleteProfile(5)

        verify(profileManager).setAutoStartProfileId(ProfileManager.NO_PROFILE)
    }

    @Test
    fun test_deleteProfile_doesNotTouchAutoStart_whenDeletedProfileWasNotDesignated() = runTest {
        whenever(profileDao.count()).thenReturn(2)
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentProfileId()
        doReturn(7).whenever(profileManager).currentAutoStartProfileId()
        whenever(profileDao.getAll()).thenReturn(listOf(profile(6)))

        repository.deleteProfile(5)

        verify(profileManager, never()).setAutoStartProfileId(any())
    }

    @Test
    fun test_setAutoStartProfile_switchesDesignatedProfile_withSingleWrite() = runTest {
        repository.setAutoStartProfile(2)

        verify(profileManager, times(1)).setAutoStartProfileId(2)
        verify(profileManager, never()).setActiveProfileId(any())
    }

    @Test
    fun test_setAutoStartProfile_null_disables() = runTest {
        repository.setAutoStartProfile(null)

        verify(profileManager).setAutoStartProfileId(ProfileManager.NO_PROFILE)
    }

    @Test
    fun test_resolveStartupProfile_appliesValidAutoStart_evenWithMultipleProfiles() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5), profile(6)))
        doReturn(6).whenever(profileManager).currentProfileId()
        doReturn(6).whenever(profileManager).currentAutoStartProfileId()

        val resolution = repository.resolveStartupProfile()

        assertFalse(resolution.needsSelection)
        assertEquals(2, resolution.profiles.size)
        verify(profileManager).setActiveProfileId(6)
    }

    @Test
    fun test_resolveStartupProfile_cleansOrphanAutoStart_andFallsBackToSelector() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5), profile(6)))
        doReturn(5).whenever(profileManager).currentProfileId()
        doReturn(99).whenever(profileManager).currentAutoStartProfileId()

        val resolution = repository.resolveStartupProfile()

        assertTrue(resolution.needsSelection)
        verify(profileManager).setAutoStartProfileId(ProfileManager.NO_PROFILE)
        verify(profileManager, never()).setActiveProfileId(99)
    }

    @Test
    fun test_resolveStartupProfile_noAutoStart_followsExisting0or1orNRule() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5), profile(6)))
        doReturn(5).whenever(profileManager).currentProfileId()
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentAutoStartProfileId()

        val resolution = repository.resolveStartupProfile()

        assertTrue(resolution.needsSelection)
    }

    @Test
    fun test_resolveStartupProfile_onLoadFailure_needsSelection_andDoesNotTouchAutoStart() = runTest {
        whenever(profileDao.getAll()).thenThrow(RuntimeException("boom"))

        val resolution = repository.resolveStartupProfile()

        assertTrue(resolution.needsSelection)
        assertTrue(resolution.profiles.isEmpty())
        verify(profileManager, never()).setAutoStartProfileId(any())
    }

    @Test
    fun test_setAutoStartProfile_replacesDesignatedProfile_asSingleObservableCurrentValue() = runTest {
        val fakeManager = FakeProfileManager(initialAutoStart = 1)
        val repo = repositoryWith(fakeManager)

        repo.setAutoStartProfile(2)

        // Une seule valeur courante après l'écriture : jamais 1 et 2 marqués ensemble.
        assertEquals(2, fakeManager.currentAutoStartProfileId())
    }

    @Test
    fun test_setAutoStartProfile_null_disables_asObservableCurrentValue() = runTest {
        val fakeManager = FakeProfileManager(initialAutoStart = 3)
        val repo = repositoryWith(fakeManager)

        repo.setAutoStartProfile(null)

        assertEquals(ProfileManager.NO_PROFILE, fakeManager.currentAutoStartProfileId())
    }

    @Test
    fun test_resolveStartupProfile_noAutoStart_zeroProfiles_goesDirectlyHome() = runTest {
        whenever(profileDao.getAll())
            .thenReturn(emptyList())
            .thenReturn(listOf(profile(1, "Profil 1")))
        whenever(profileDao.insert(any())).thenReturn(1L)
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentAutoStartProfileId()

        val resolution = repository.resolveStartupProfile()

        assertFalse(resolution.needsSelection)
        assertEquals(1, resolution.profiles.size)
    }

    @Test
    fun test_resolveStartupProfile_noAutoStart_singleProfile_goesDirectlyHome() = runTest {
        whenever(profileDao.getAll()).thenReturn(listOf(profile(5)))
        doReturn(5).whenever(profileManager).currentProfileId()
        doReturn(ProfileManager.NO_PROFILE).whenever(profileManager).currentAutoStartProfileId()

        val resolution = repository.resolveStartupProfile()

        assertFalse(resolution.needsSelection)
    }

    @Test
    fun test_renameProfile_doesNotTouchAutoStart() = runTest {
        whenever(profileDao.getById(5)).thenReturn(profile(5))

        repository.renameProfile(5, "Nouveau nom")

        verify(profileManager, never()).setAutoStartProfileId(any())
    }

    @Test
    fun test_updateAvatar_doesNotTouchAutoStart() = runTest {
        whenever(profileDao.getById(5)).thenReturn(profile(5))

        repository.updateAvatar(5, 3)

        verify(profileManager, never()).setAutoStartProfileId(any())
    }
}
