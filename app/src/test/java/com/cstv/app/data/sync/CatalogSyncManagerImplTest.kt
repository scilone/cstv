package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.InvalidCredentialsException
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncOutcome
import com.cstv.app.domain.sync.SyncTrigger
import com.cstv.app.domain.usecase.ClearCatalogCacheUseCase
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSyncManagerImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock private lateinit var liveTvRepository: LiveTvRepository
    @Mock private lateinit var vodRepository: VodRepository
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var syncStateDao: CatalogSyncStateDao
    @Mock private lateinit var credentialsManager: CredentialsManager
    @Mock private lateinit var settingsManager: SettingsManager
    @Mock private lateinit var networkMonitor: NetworkMonitor
    @Mock private lateinit var clearCatalogCacheUseCase: ClearCatalogCacheUseCase
    @Mock private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository
    @Mock private lateinit var catalogReconciler: CatalogReconciler

    private lateinit var manager: CatalogSyncManagerImpl

    private val credentials = Credentials("panel.example.com", 8080, "user", "secret")
    private val accountKey get() = CatalogServerKey.from(credentials)
    // Distincte de `accountKey` (serveur seul) : la réconciliation opère sur les
    // identités `media_refs`, clées par AccountKey (host:port:username).
    private val mediaAccountKey get() = AccountKey.from(credentials)

    // `catalogStatus` appelle `syncStateDao.observeAll()` une seule fois, à la
    // construction du manager (propriété initialisée dans le corps de classe) :
    // le stub doit donc exister AVANT l'instanciation, et cette même instance
    // de flow doit rester mutable pour que les tests changent ce qu'elle émet.
    private val syncStatesFlow = MutableStateFlow<List<CatalogSyncStateEntity>>(emptyList())

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(credentialsManager.getCredentials()).thenReturn(credentials)
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(true)
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DAILY)
        whenever(syncStateDao.observeAll()).thenReturn(syncStatesFlow)
        // Sans état en base, l'initialiseur n'a rien à reprendre : c'est le cas
        // nominal d'un catalogue jamais synchronisé.
        syncStateDao.stub { onBlocking { getAll() }.doReturn(emptyList()) }
        categoryPreferenceRepository.stub { onBlocking { getPreferences(any()) }.doReturn(emptyMap()) }
        // L'initialiseur est une classe finale : on l'instancie réellement avec
        // des doubles, plutôt que d'ajouter mockito-inline (voir AGENTS.md).
        manager = CatalogSyncManagerImpl(
            liveTvRepository,
            vodRepository,
            seriesRepository,
            syncStateDao,
            credentialsManager,
            settingsManager,
            networkMonitor,
            CatalogSyncStateInitializer(syncStateDao, settingsManager, credentialsManager),
            clearCatalogCacheUseCase,
            categoryPreferenceRepository,
            catalogReconciler
        )
    }

    private suspend fun stubAllSectionsSucceeding() {
        whenever(liveTvRepository.syncLiveCategories()).thenReturn(listOf(LiveCategory("1", "Général", 0)))
        whenever(liveTvRepository.syncLiveStreams(any())).thenReturn(listOf(LiveStream(1, "TF1", null, null, 1, "1")))
        whenever(vodRepository.syncVodCategories()).thenReturn(listOf(VodCategory("1", "Action", 0)))
        whenever(vodRepository.syncVodStreams(any())).thenReturn(listOf(VodStream(1, "Film", null, null, null, "1")))
        whenever(seriesRepository.syncSeriesCategories()).thenReturn(listOf(SeriesCategory("1", "Drame", 0)))
        whenever(seriesRepository.syncSeriesStreams(any())).thenReturn(listOf(SeriesStream(1, "Série", null, null, null, "1")))
        whenever(vodRepository.enrichPendingMovies(any())).thenReturn(1)
        whenever(seriesRepository.enrichPendingSeries(any())).thenReturn(1)
    }

    @Test
    fun allSectionsSucceed_reportsSuccessAndStampsEachSection() = runTest {
        stubAllSectionsSucceeding()

        val outcome = manager.syncNow(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Success)
        CatalogSection.CATALOG_SECTIONS.forEach { section ->
            verify(syncStateDao).markSuccess(eq(section), eq(accountKey), any(), any())
        }
        // Ordre imposé : catégories avant flux, sinon des flux restent invisibles.
        inOrder(liveTvRepository, vodRepository, seriesRepository) {
            verify(liveTvRepository).syncLiveCategories()
            verify(liveTvRepository).syncLiveStreams(any())
            verify(vodRepository).syncVodCategories()
            verify(vodRepository).syncVodStreams(any())
            verify(seriesRepository).syncSeriesCategories()
            verify(seriesRepository).syncSeriesStreams(any())
        }
    }

    /**
     * Règle 5.3 : jamais deux synchronisations concurrentes pour un même compte.
     * La seconde demande repart sans avoir émis la moindre requête.
     */
    @Test
    fun concurrentRequestReturnsAlreadyRunning_withoutAnySecondRequest() = runTest {
        stubAllSectionsSucceeding()
        val gate = CompletableDeferred<Unit>()
        whenever(liveTvRepository.syncLiveCategories()).doSuspendableAnswer {
            gate.await()
            listOf(LiveCategory("1", "Général", 0))
        }

        val first = async { manager.syncNow(SyncTrigger.STARTUP) }
        runCurrent() // laisse la première synchronisation prendre le verrou puis se suspendre
        val second = manager.syncNow(SyncTrigger.MANUAL)
        gate.complete(Unit)
        first.await()

        assertEquals(SyncOutcome.AlreadyRunning, second)
        verify(liveTvRepository, times(1)).syncLiveCategories()
    }

    /**
     * Isolation : l'échec d'une section ne doit ni annuler les sections déjà
     * committées, ni empêcher les suivantes de s'exécuter.
     */
    @Test
    fun sectionFailureDoesNotStopOtherSections_norRenewTheirTimestamps() = runTest {
        stubAllSectionsSucceeding()
        whenever(vodRepository.syncVodStreams(any())).doSuspendableAnswer { throw SocketTimeoutException("timeout") }

        val outcome = manager.syncNow(SyncTrigger.SCHEDULED)

        assertTrue(outcome is SyncOutcome.Failure)
        assertEquals(SyncFailureKind.NETWORK, (outcome as SyncOutcome.Failure).kind)
        // Section fautive : échec horodaté, succès inchangé.
        verify(syncStateDao).markFailure(eq(CatalogSection.VOD_STREAMS), eq(accountKey), any(), eq("NETWORK"))
        verify(syncStateDao, never()).markSuccess(eq(CatalogSection.VOD_STREAMS), any(), any(), any())
        // Sections antérieures et postérieures : intactes.
        verify(syncStateDao).markSuccess(eq(CatalogSection.LIVE_STREAMS), eq(accountKey), any(), any())
        verify(seriesRepository).syncSeriesStreams(any())
    }

    /**
     * `AUTH` est le seul cas d'arrêt immédiat : poursuivre avec des identifiants
     * rejetés ne produit que du trafic refusé et rapproche du bannissement.
     */
    @Test
    fun authFailureStopsTheRunImmediately() = runTest {
        stubAllSectionsSucceeding()
        whenever(liveTvRepository.syncLiveCategories()).doSuspendableAnswer { throw InvalidCredentialsException("rejeté") }

        val outcome = manager.syncNow(SyncTrigger.STARTUP)

        assertEquals(SyncFailureKind.AUTH, (outcome as SyncOutcome.Failure).kind)
        verify(vodRepository, never()).syncVodCategories()
        verify(seriesRepository, never()).syncSeriesCategories()
        verify(vodRepository, never()).enrichPendingMovies(any())
    }

    @Test
    fun failuresAreClassifiedByCause() = runTest {
        stubAllSectionsSucceeding()

        whenever(vodRepository.syncVodStreams(any())).doSuspendableAnswer { throw JsonSyntaxException("illisible") }
        assertEquals(SyncFailureKind.PARSE, (manager.syncNow(SyncTrigger.MANUAL) as SyncOutcome.Failure).kind)

        whenever(vodRepository.syncVodStreams(any())).doSuspendableAnswer { throw android.database.sqlite.SQLiteFullException("plein") }
        assertEquals(SyncFailureKind.STORAGE, (manager.syncNow(SyncTrigger.MANUAL) as SyncOutcome.Failure).kind)
    }

    /**
     * Un panel qui répond une liste vide n'est pas un catalogue vide légitime
     * (§5.4 : « ne prétend pas que le catalogue est vide »).
     */
    @Test
    fun emptyPanelResponseIsTreatedAsPanelFailure() = runTest {
        stubAllSectionsSucceeding()
        whenever(vodRepository.syncVodStreams(any())).thenReturn(emptyList())

        val outcome = manager.syncNow(SyncTrigger.MANUAL)

        assertEquals(SyncFailureKind.PANEL, (outcome as SyncOutcome.Failure).kind)
        verify(syncStateDao).markFailure(eq(CatalogSection.VOD_STREAMS), any(), any(), eq("PANEL"))
    }

    @Test
    fun syncIfStaleSkipsWhenOffline() = runTest {
        whenever(networkMonitor.isCurrentlyOnline()).thenReturn(false)

        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    @Test
    fun syncIfStaleSkipsWhenCatalogIsFresh() = runTest {
        val now = System.currentTimeMillis()
        whenever(syncStateDao.getAll()).thenReturn(
            CatalogSection.CATALOG_SECTIONS.map {
                CatalogSyncStateEntity(section = it, accountKey = accountKey, lastSuccessAt = now)
            }
        )

        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    // --- T7 D1 : TTL dynamique selon la fréquence de synchronisation ---

    private suspend fun stubCatalogSyncedAgo(ageMillis: Long) {
        val lastSuccessAt = System.currentTimeMillis() - ageMillis
        whenever(syncStateDao.getAll()).thenReturn(
            CatalogSection.CATALOG_SECTIONS.map {
                CatalogSyncStateEntity(section = it, accountKey = accountKey, lastSuccessAt = lastSuccessAt)
            }
        )
    }

    @Test
    fun syncIfStaleWithDailyFrequencyTriggersPastOneDay() = runTest {
        stubAllSectionsSucceeding()
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DAILY)
        stubCatalogSyncedAgo(25 * 60 * 60 * 1000L)

        assertTrue(manager.syncIfStale() is SyncOutcome.Success)
    }

    @Test
    fun syncIfStaleWithWeeklyFrequencyStaysFreshBeforeSevenDays() = runTest {
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.WEEKLY)
        // Périmé pour DAILY, mais encore frais pour WEEKLY : la fréquence
        // choisie doit primer sur l'ancien TTL fixe de 24h.
        stubCatalogSyncedAgo(2 * 24 * 60 * 60 * 1000L)

        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    @Test
    fun syncIfStaleWithWeeklyFrequencyTriggersPastSevenDays() = runTest {
        stubAllSectionsSucceeding()
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.WEEKLY)
        stubCatalogSyncedAgo(8 * 24 * 60 * 60 * 1000L)

        assertTrue(manager.syncIfStale() is SyncOutcome.Success)
    }

    @Test
    fun syncIfStaleWithMonthlyFrequencyStaysFreshBeforeThirtyDays() = runTest {
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.MONTHLY)
        stubCatalogSyncedAgo(20 * 24 * 60 * 60 * 1000L)

        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    @Test
    fun syncIfStaleWithMonthlyFrequencyTriggersPastThirtyDays() = runTest {
        stubAllSectionsSucceeding()
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.MONTHLY)
        stubCatalogSyncedAgo(31 * 24 * 60 * 60 * 1000L)

        assertTrue(manager.syncIfStale() is SyncOutcome.Success)
    }

    /**
     * Règle métier 1 : une fréquence désactivée n'autorise pas de
     * synchronisation automatique fondée sur l'âge du cache, même très ancien.
     */
    @Test
    fun syncIfStaleWithDisabledFrequencyNeverTriggersFromAge() = runTest {
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        stubCatalogSyncedAgo(365 * 24 * 60 * 60 * 1000L)

        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
        verifyNoInteractions(liveTvRepository, vodRepository, seriesRepository)
    }

    /**
     * Règle métier 3 : l'absence de catalogue local reste un besoin de
     * synchronisation indépendamment de la fréquence, y compris DISABLED.
     */
    @Test
    fun syncIfStaleWithDisabledFrequencyStillTriggersWhenNeverSynced() = runTest {
        stubAllSectionsSucceeding()
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.DISABLED)
        whenever(syncStateDao.getAll()).thenReturn(emptyList())

        assertTrue(manager.syncIfStale() is SyncOutcome.Success)
    }

    /**
     * Règle métier 2 : le statut global du catalogue (`catalogStatus`) et la
     * décision de `syncIfStale()` partagent le même TTL dynamique et ne
     * doivent jamais diverger.
     */
    @Test
    fun catalogStatusIsStaleAgreesWithSyncIfStaleForTheSameFrequency() = runTest {
        whenever(settingsManager.getSyncFrequency()).thenReturn(SyncFrequency.WEEKLY)
        val lastSuccessAt = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
        val states = CatalogSection.CATALOG_SECTIONS.map {
            CatalogSyncStateEntity(section = it, accountKey = accountKey, lastSuccessAt = lastSuccessAt)
        }
        syncStatesFlow.value = states
        whenever(syncStateDao.getAll()).thenReturn(states)

        val status = manager.catalogStatus.first()

        assertFalse(status.isStale)
        assertEquals(SyncOutcome.Skipped, manager.syncIfStale())
    }

    /**
     * T7-R1 : un échec réseau passé ne doit plus maintenir le signal de
     * connectivité exposé à l'UI une fois l'appareil
     * reconnecté. `isOffline` (diagnostic combiné) reste vrai pour l'historique,
     * mais `isNetworkOnline` (T7-R1) doit refléter la connectivité réelle.
     */
    @Test
    fun catalogStatusReportsNetworkOnlineDespiteAPastNetworkFailure() = runTest {
        val states = CatalogSection.CATALOG_SECTIONS.map {
            CatalogSyncStateEntity(
                section = it,
                accountKey = accountKey,
                lastSuccessAt = System.currentTimeMillis(),
                lastFailureAt = System.currentTimeMillis(),
                lastFailureKind = SyncFailureKind.NETWORK.name
            )
        }
        syncStatesFlow.value = states

        val status = manager.catalogStatus.first()

        assertEquals(SyncFailureKind.NETWORK, status.lastFailureKind)
        assertTrue(status.isOffline)
        assertTrue(status.isNetworkOnline)
    }

    // --- Purge à la connexion sur changement de compte ---

    @Test
    fun sameAccountKeepsTheCatalogIntact() = runTest {
        whenever(syncStateDao.getAccountKey()).thenReturn(accountKey)

        assertFalse(manager.onAccountAuthenticated(accountKey))
        verify(clearCatalogCacheUseCase, never()).invoke()
    }

    @Test
    fun differentServerPurgesTheCatalog() = runTest {
        whenever(syncStateDao.getAccountKey()).thenReturn("cle-d-un-autre-compte")
        whenever(credentialsManager.getCredentials()).thenReturn(
            credentials.copy(host = "other.example.com")
        )

        assertTrue(manager.onAccountAuthenticated(accountKey))
        verify(clearCatalogCacheUseCase).invoke()
        // Les horodatages d'avant T4 sont neutralisés, sinon l'initialiseur
        // ferait passer le catalogue purgé pour un catalogue frais.
        verify(settingsManager).setVodAllStreamsSyncedAt(0L)
    }

    @Test
    fun differentUserOnSameServerKeepsTheCatalog() = runTest {
        whenever(syncStateDao.getAccountKey()).thenReturn("legacy-user-key")
        whenever(credentialsManager.getCredentials()).thenReturn(
            credentials.copy(username = "previous-user")
        )

        assertFalse(manager.onAccountAuthenticated(accountKey))
        verify(syncStateDao).updateAccountKey(accountKey)
        verify(clearCatalogCacheUseCase, never()).invoke()
    }

    @Test
    fun missingKeyPurgesButIsNotReportedAsAnAccountChange() = runTest {
        whenever(syncStateDao.getAccountKey()).thenReturn(null)

        assertFalse(manager.onAccountAuthenticated(accountKey))
        verify(clearCatalogCacheUseCase).invoke()
    }

    // --- Réconciliation post-synchronisation (T20 §4.5) ---

    /**
     * Garde centrale du §4.5 : la réconciliation ne part que si les six
     * sections catalogue ont réellement réussi pour ce compte.
     */
    @Test
    fun fullCatalogSuccessTriggersReconciliationForTheCurrentAccount() = runTest {
        stubAllSectionsSucceeding()

        val outcome = manager.syncNow(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Success)
        verify(catalogReconciler).reconcile(mediaAccountKey)
    }

    /**
     * Une réponse partielle de panel (une section en échec) ne doit jamais
     * déclencher une destruction de fichiers — même quand cinq sections sur
     * six ont réussi.
     */
    @Test
    fun partialCatalogFailureNeverTriggersReconciliation() = runTest {
        stubAllSectionsSucceeding()
        whenever(vodRepository.syncVodStreams(any())).doSuspendableAnswer { throw SocketTimeoutException("timeout") }

        val outcome = manager.syncNow(SyncTrigger.SCHEDULED)

        assertTrue(outcome is SyncOutcome.Failure)
        verifyNoInteractions(catalogReconciler)
    }

    /** L'arrêt immédiat sur AUTH ne laisse même pas les six sections s'exécuter. */
    @Test
    fun authFailureNeverTriggersReconciliation() = runTest {
        stubAllSectionsSucceeding()
        whenever(liveTvRepository.syncLiveCategories()).doSuspendableAnswer { throw InvalidCredentialsException("rejeté") }

        manager.syncNow(SyncTrigger.STARTUP)

        verifyNoInteractions(catalogReconciler)
    }

    /**
     * L'enrichissement (septième section, hors `CATALOG_SECTIONS`) est
     * volontairement hors du calcul : son échec ne doit pas bloquer un
     * nettoyage dont la légitimité ne dépend que du catalogue lui-même.
     */
    @Test
    fun enrichmentFailureStillTriggersReconciliation() = runTest {
        stubAllSectionsSucceeding()
        whenever(vodRepository.enrichPendingMovies(any())).doSuspendableAnswer { throw RuntimeException("enrichissement en échec") }

        manager.syncNow(SyncTrigger.MANUAL)

        verify(catalogReconciler).reconcile(mediaAccountKey)
    }
}
