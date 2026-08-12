package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.dto.CstvProfileDto
import com.cstv.app.domain.model.CstvAccount
import com.cstv.app.domain.model.CstvProfile
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * F33 §5.5 — fusion idempotente, jamais par nom/avatar seul.
 */
class ProfileCloudReconcilerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var profileDao: ProfileDao
    @Mock private lateinit var api: CstvApiService
    @Mock private lateinit var emptinessProbe: CloudProfileEmptinessProbe

    private lateinit var reconciler: ProfileCloudReconciler

    private fun local(id: Int, name: String, remoteId: String? = null, createdAt: Long = id.toLong()) =
        ProfileEntity(id = id, name = name, avatarId = 0, createdAt = createdAt, remoteId = remoteId)

    private fun cloud(id: String, name: String, createdAtMillis: Long = 1L) =
        CstvProfile(id, name, 0, createdAtMillis)

    private fun account(vararg profiles: CstvProfile) =
        CstvAccount("account", "mail@example.test", true, Long.MAX_VALUE, profiles.toList())

    private fun createdProfileResponse(id: String, name: String, avatarId: Int = 0) =
        Response.success(CstvProfileDto(id, name, avatarId, "2026-01-01T00:00:00Z"))

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        reconciler = ProfileCloudReconciler(profileDao, api, CstvErrorMapper(Gson()), emptinessProbe)
    }

    @Test
    fun blankCloudAccount_pairsTheAutomaticProfile1_withTheFirstLocalProfile_insteadOfImportingIt() = runTest {
        val cloudProfile1 = cloud("cloud-1", "Profil 1")
        whenever(profileDao.getAll())
            .thenReturn(listOf(local(5, "Nicolas")))
            .thenReturn(listOf(local(5, "Nicolas"))) // relecture après tentative d'association par remoteId (aucune)
            .thenReturn(listOf(local(5, "Nicolas", remoteId = "cloud-1"))) // relecture après appariement automatique
        whenever(emptinessProbe.isEmpty("cloud-1")).thenReturn(true)
        whenever(api.updateProfile(eq("cloud-1"), any())).thenReturn(createdProfileResponse("cloud-1", "Nicolas"))
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())

        reconciler.reconcile(account(cloudProfile1))

        verify(profileDao).setRemoteId(5, "cloud-1")
        verify(profileDao, never()).insert(any())
    }

    @Test
    fun blankCloudAccount_withUnknownEmptiness_neverPairsAutomatically() = runTest {
        val cloudProfile1 = cloud("cloud-1", "Profil 1")
        whenever(profileDao.getAll()).thenReturn(listOf(local(5, "Nicolas")))
        whenever(emptinessProbe.isEmpty("cloud-1")).thenReturn(null) // inconnu : ne jamais deviner
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(listOf(local(5, "Nicolas")))
        whenever(api.createProfile(any())).thenReturn(createdProfileResponse("new-cloud-id", "Nicolas"))

        reconciler.reconcile(account(cloudProfile1))

        verify(profileDao, never()).setRemoteId(5, "cloud-1")
        // Le profil cloud vierge est importé tel quel, et le profil local exporté séparément : deux entrées distinctes.
        verify(profileDao).insert(org.mockito.kotlin.argThat { name == "Profil 1" && remoteId == "cloud-1" })
        verify(profileDao).setRemoteId(5, "new-cloud-id")
    }

    @Test
    fun populatedCloudAccount_withNoLocalProfiles_importsEveryCloudProfile_inCreationOrder() = runTest {
        val older = cloud("cloud-1", "Alice", createdAtMillis = 100L)
        val newer = cloud("cloud-2", "Bob", createdAtMillis = 200L)
        whenever(profileDao.getAll()).thenReturn(emptyList())
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())

        reconciler.reconcile(account(newer, older)) // ordre d'arrivée volontairement inversé

        val inserted = argumentCaptor<ProfileEntity>()
        verify(profileDao, org.mockito.kotlin.times(2)).insert(inserted.capture())
        assertEquals("Alice", inserted.firstValue.name) // le plus ancien d'abord
        assertEquals("Bob", inserted.secondValue.name)
    }

    @Test
    fun sameDisplayName_withoutAMatchingRemoteId_neverMergesAsOneProfile() = runTest {
        val cloudHomonym = cloud("cloud-1", "Nicolas")
        whenever(profileDao.getAll()).thenReturn(listOf(local(5, "Nicolas"))) // homonyme purement local, sans remoteId
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(listOf(local(5, "Nicolas")))
        whenever(api.createProfile(any())).thenReturn(createdProfileResponse("cloud-2", "Nicolas"))

        reconciler.reconcile(account(cloudHomonym))

        // Le profil cloud homonyme est importé en plus, jamais fusionné avec le local.
        verify(profileDao).insert(org.mockito.kotlin.argThat { name == "Nicolas" && remoteId == "cloud-1" })
        verify(profileDao).setRemoteId(5, "cloud-2")
    }

    @Test
    fun localProfileAlreadyLinked_getsRenamedFromTheCloudCopy_theRemoteNameWins() = runTest {
        val cloudProfile = cloud("cloud-1", "Nom cloud")
        whenever(profileDao.getAll()).thenReturn(listOf(local(5, "Ancien nom local", remoteId = "cloud-1")))
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())

        reconciler.reconcile(account(cloudProfile))

        verify(profileDao).update(org.mockito.kotlin.argThat { id == 5 && name == "Nom cloud" })
        verify(profileDao, never()).insert(any())
    }

    @Test
    fun export_sendsOneCreatePerUnassociatedLocalProfile_inCreatedAtOrder() = runTest {
        whenever(profileDao.getAll()).thenReturn(emptyList())
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(
            listOf(local(1, "Premier", createdAt = 10L), local(2, "Second", createdAt = 20L))
        )
        whenever(api.createProfile(any()))
            .thenReturn(createdProfileResponse("remote-1", "Premier"))
            .thenReturn(createdProfileResponse("remote-2", "Second"))

        reconciler.reconcile(account())

        verify(profileDao).setRemoteId(1, "remote-1")
        verify(profileDao).setRemoteId(2, "remote-2")
    }

    @Test
    fun export_stopsOnAFailure_leavingAlreadyAssociatedProfilesIntact_forAResumeOnTheNextResolve() = runTest {
        whenever(profileDao.getAll()).thenReturn(emptyList())
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(
            listOf(local(1, "Premier", createdAt = 10L), local(2, "Second", createdAt = 20L))
        )
        whenever(api.createProfile(any()))
            .thenReturn(createdProfileResponse("remote-1", "Premier"))
            .thenAnswer { throw java.io.IOException("network drop") }

        val result = reconciler.reconcile(account())

        assertTrue(result.isFailure)
        verify(profileDao).setRemoteId(1, "remote-1") // le premier export reste acquis
        verify(profileDao, never()).setRemoteId(eq(2), any())
    }

    @Test
    fun reconcile_isIdempotent_repeatingItOnAlreadyAssociatedProfilesHasNoFurtherEffect() = runTest {
        val cloudProfile = cloud("cloud-1", "Nicolas")
        whenever(profileDao.getAll()).thenReturn(listOf(local(5, "Nicolas", remoteId = "cloud-1")))
        whenever(profileDao.getAllWithoutRemoteId()).thenReturn(emptyList())

        reconciler.reconcile(account(cloudProfile))

        verify(profileDao, never()).insert(any())
        verify(profileDao, never()).setRemoteId(any(), any())
        verify(api, never()).createProfile(any())
    }
}
