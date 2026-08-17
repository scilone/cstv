package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.PlaybackRepairProfileDao
import com.cstv.app.data.local.entity.PlaybackRepairProfileEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepairRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock
    private lateinit var dao: PlaybackRepairProfileDao

    @Mock
    private lateinit var mediaRefDao: MediaRefDao

    @Mock
    private lateinit var accountKeyProvider: CurrentAccountKeyProvider

    private lateinit var repository: PlaybackRepairRepositoryImpl

    private val accountKey = "account-key"
    private val gson = Gson()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doReturn(accountKey).whenever(accountKeyProvider).current()
        repository = PlaybackRepairRepositoryImpl(dao, mediaRefDao, accountKeyProvider, gson)
    }

    @Test
    fun getRepairPlan_noMediaRefYet_returnsNull() = runTest {
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(null)

        assertNull(repository.getRepairPlan("movie", 10))
    }

    @Test
    fun getRepairPlan_noProfileForMedia_returnsNull() = runTest {
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(1L)
        whenever(dao.getByMediaUid(1L)).thenReturn(null)

        assertNull(repository.getRepairPlan("movie", 10))
    }

    @Test
    fun getRepairPlan_deserializesStrategyAndFingerprints() = runTest {
        val disabled = TrackFingerprint(TrackKind.AUDIO, "fra", "audio/eac3", "ec-3", 6, 0, "VFF")
        whenever(mediaRefDao.findUid(accountKey, "series", 20)).thenReturn(2L)
        whenever(dao.getByMediaUid(2L)).thenReturn(
            PlaybackRepairProfileEntity(
                mediaUid = 2L,
                decoderStrategy = "SOFTWARE_PREFERRED",
                disabledTrackJson = disabled.toJson(gson),
                preferredAudioJson = null,
                updatedAt = 1000L
            )
        )

        val plan = repository.getRepairPlan("series", 20)

        assertEquals(DecoderStrategy.SOFTWARE_PREFERRED, plan?.decoderStrategy)
        assertEquals(disabled, plan?.disabledTrack)
        assertNull(plan?.preferredAudio)
    }

    @Test
    fun getRepairPlan_decodesSoftwarePreferredTrackKind_encodedInTheStrategyColumn() = runTest {
        // R2 : trackKind encodé dans la même colonne ("SOFTWARE_PREFERRED:VIDEO") plutôt qu'une
        // colonne supplémentaire — pas de migration Room de plus pour ce champ.
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(1L)
        whenever(dao.getByMediaUid(1L)).thenReturn(
            PlaybackRepairProfileEntity(mediaUid = 1L, decoderStrategy = "SOFTWARE_PREFERRED:VIDEO", disabledTrackJson = null, preferredAudioJson = null, updatedAt = 1000L)
        )

        val plan = repository.getRepairPlan("movie", 10)

        assertEquals(DecoderStrategy.SOFTWARE_PREFERRED, plan?.decoderStrategy)
        assertEquals(TrackKind.VIDEO, plan?.softwarePreferredTrackKind)
    }

    @Test
    fun getRepairPlan_legacyStrategyWithoutTrackKind_decodesNullTrackKind() = runTest {
        // Rétrocompatibilité avec les profils écrits avant ce correctif (pas de ":").
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(1L)
        whenever(dao.getByMediaUid(1L)).thenReturn(
            PlaybackRepairProfileEntity(mediaUid = 1L, decoderStrategy = "SOFTWARE_PREFERRED", disabledTrackJson = null, preferredAudioJson = null, updatedAt = 1000L)
        )

        assertNull(repository.getRepairPlan("movie", 10)?.softwarePreferredTrackKind)
    }

    @Test
    fun saveRepairPlan_encodesSoftwarePreferredTrackKind_inTheStrategyColumn() = runTest {
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO)
        whenever(mediaRefDao.resolve(accountKey, "movie", 10)).thenReturn(5L)
        val captor = org.mockito.kotlin.argumentCaptor<PlaybackRepairProfileEntity>()

        repository.saveRepairPlan("movie", 10, plan)

        verify(dao).upsert(captor.capture())
        assertEquals("SOFTWARE_PREFERRED:AUDIO", captor.firstValue.decoderStrategy)
    }

    @Test
    fun getRepairPlan_corruptStrategy_fallsBackToDefault_ratherThanCrashing() = runTest {
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(1L)
        whenever(dao.getByMediaUid(1L)).thenReturn(
            PlaybackRepairProfileEntity(mediaUid = 1L, decoderStrategy = "NOT_A_REAL_STRATEGY", disabledTrackJson = null, preferredAudioJson = null, updatedAt = 1000L)
        )

        assertEquals(DecoderStrategy.DEFAULT, repository.getRepairPlan("movie", 10)?.decoderStrategy)
    }

    @Test
    fun saveRepairPlan_resolvesMediaUid_andWritesTheWinningConfiguration() = runTest {
        val plan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED)
        whenever(mediaRefDao.resolve(accountKey, "movie", 10)).thenReturn(5L)
        val captor = org.mockito.kotlin.argumentCaptor<PlaybackRepairProfileEntity>()

        repository.saveRepairPlan("movie", 10, plan)

        // `updatedAt` = `System.currentTimeMillis()` au moment de l'appel : capturé plutôt que
        // comparé à une valeur littérale, seule sa présence (> 0) est vérifiable.
        verify(dao).upsert(captor.capture())
        val saved = captor.firstValue
        assertEquals(5L, saved.mediaUid)
        assertEquals("SOFTWARE_PREFERRED", saved.decoderStrategy)
        assertNull(saved.disabledTrackJson)
        assertNull(saved.preferredAudioJson)
        assert(saved.updatedAt > 0L)
    }

    @Test
    fun clearRepairPlan_noMediaRefYet_doesNothing() = runTest {
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(null)

        repository.clearRepairPlan("movie", 10)

        verify(dao, org.mockito.kotlin.never()).deleteByMediaUid(org.mockito.kotlin.any())
    }

    @Test
    fun clearRepairPlan_existingMediaRef_deletesByMediaUid() = runTest {
        whenever(mediaRefDao.findUid(accountKey, "movie", 10)).thenReturn(5L)

        repository.clearRepairPlan("movie", 10)

        verify(dao).deleteByMediaUid(5L)
    }
}
